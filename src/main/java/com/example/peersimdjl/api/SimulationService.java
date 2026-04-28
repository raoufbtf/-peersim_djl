package com.example.peersimdjl.api;

import org.springframework.stereotype.Service;

import com.example.peersimdjl.ChordProtocol;
import com.example.peersimdjl.events.SimulationEvent;
import com.example.peersimdjl.events.SimulationEventPublisher;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.time.Instant;
import java.util.Map;

/**
 * Service de simulation PeerSim + DJL.
 * LANCE PEERSIM DANS UN PROCESSUS FILS ISOLÉ
 * pour éviter que System.exit() ne tue le serveur Spring.
 */
@Service
public class SimulationService {

    private final AtomicReference<SimulationState> state = new AtomicReference<>(SimulationState.IDLE);
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private Process simulatorProcess = null;
    private final SimulationEventPublisher eventPublisher;

    public SimulationService(SimulationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Lance PeerSim dans un processus fils isolé.
     * Si PeerSim fait System.exit(), seul le fils meurt, pas le serveur Spring.
     */
    public void start(SimulationRequest req) {
        if (state.get() == SimulationState.RUNNING) {
            throw new IllegalStateException("Already running");
        }
        state.set(SimulationState.RUNNING);

        executor.submit(() -> {
            ProcessBuilder pb = null;
            try {
                Path configPath = buildConfig(req);
                ChordProtocol.DEBUGChord = false;

                // 1. Récupérer le chemin de la JVM actuelle
                String javaHome = System.getProperty("java.home");
                String javaBin = Paths.get(javaHome, "bin", "java.exe").toString();

                // 2. Récupérer le classpath (toutes les dépendances)
                String classpath = System.getProperty("java.class.path");

                // 3. Créer le processus fils
                pb = new ProcessBuilder(
                        javaBin,
                        "-cp", classpath,
                        "peersim.Simulator",
                        configPath.toString()
                );

                pb.redirectErrorStream(true);

                System.out.println("[SimulationService] Lancement PeerSim dans un processus fils...");
                eventPublisher.publish(new SimulationEvent(
                    Instant.now(),
                    "INFO",
                    "SIM_START",
                    null,
                    null,
                    "Simulation started",
                    Map.of("configPath", configPath.toString())
                ));
                simulatorProcess = pb.start();

                Thread logReader = new Thread(() -> readProcessLogs(simulatorProcess));
                logReader.setDaemon(true);
                logReader.start();

                // 4. Attendre la fin (sans tuer le parent)
                int exitCode = simulatorProcess.waitFor();

                if (exitCode == 0) {
                    System.out.println("[SimulationService] Simulation terminée avec succès.");
                } else {
                    System.out.println("[SimulationService] Simulation terminée (code: " + exitCode + ")");
                }

                eventPublisher.publish(new SimulationEvent(
                        Instant.now(),
                        exitCode == 0 ? "INFO" : "WARN",
                        "SIM_END",
                        null,
                        null,
                        "Simulation ended with code " + exitCode,
                        Map.of("exitCode", exitCode)
                ));

                state.set(SimulationState.STOPPED);

            } catch (Exception e) {
                System.err.println("[SimulationService] Erreur: " + e.getMessage());
                eventPublisher.publish(new SimulationEvent(
                        Instant.now(),
                        "ERROR",
                        "SIM_ERROR",
                        null,
                        null,
                        "Simulation error: " + e.getMessage(),
                        Map.of()
                ));
                state.set(SimulationState.FAILED);
            } finally {
                simulatorProcess = null;
                // Recréer l'executor pour la prochaine simulation
                executor.shutdownNow();
                executor = Executors.newSingleThreadExecutor();
            }
        });
    }

    public void stop() {
        if (state.get() != SimulationState.RUNNING) {
            throw new IllegalStateException("Not running");
        }
        if (simulatorProcess != null) {
            simulatorProcess.destroy();  // Tue le processus fils PeerSim
        }
        executor.shutdownNow();
        state.set(SimulationState.STOPPED);
        executor = Executors.newSingleThreadExecutor();
    }

    private void readProcessLogs(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                eventPublisher.publish(new SimulationEvent(
                        Instant.now(),
                        "INFO",
                        "SIM_LOG",
                        null,
                        null,
                        line,
                        Map.of()
                ));
            }
        } catch (IOException e) {
            eventPublisher.publish(new SimulationEvent(
                    Instant.now(),
                    "ERROR",
                    "SIM_LOG",
                    null,
                    null,
                    "Log stream error: " + e.getMessage(),
                    Map.of()
            ));
        }
    }

    public SimulationState getState() {
        return state.get();
    }

    private Path buildConfig(SimulationRequest req) throws IOException, URISyntaxException {
        Path source = resolveConfigPath();
        String original = Files.readString(source, StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(original);

        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("network.size", String.valueOf(req.getNetworkSize()));
        overrides.put("simulation.cycles", String.valueOf(Math.max(24, req.getSessionRequirements().length * 12)));
        overrides.put("control.learning.datasetPath", req.getDatasetPaths()[0].replace('\\', '/'));
        overrides.put("control.learning.datasetPaths", joinDatasetPaths(req.getDatasetPaths()));
        overrides.put("control.learning.batchStrategy", req.getBatchStrategy());
        overrides.put("control.learning.maxBatchesPerNode", String.valueOf(req.getMaxBatchesPerNode()));
        overrides.put("control.learning.sessionRequirements", joinRequirements(req.getSessionRequirements()));
        overrides.put("control.learning.modelType", req.getModelType());
        overrides.put("control.learning.pid", "0");

        builder.append(System.lineSeparator())
                .append("# Overrides API request").append(System.lineSeparator());
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            builder.append(entry.getKey()).append(" = ").append(entry.getValue()).append(System.lineSeparator());
        }

        Path tempConfig = Files.createTempFile("peersim-api-", ".cfg");
        Files.writeString(tempConfig, builder.toString(), StandardCharsets.UTF_8);
        tempConfig.toFile().deleteOnExit();
        return tempConfig;
    }

    private Path resolveConfigPath() throws URISyntaxException, IOException {
        URL resource = SimulationService.class.getClassLoader().getResource("peersim.cfg");
        if (resource != null) {
            if ("file".equalsIgnoreCase(resource.getProtocol())) {
                return Paths.get(resource.toURI());
            }
            try (InputStream in = SimulationService.class.getClassLoader().getResourceAsStream("peersim.cfg")) {
                if (in != null) {
                    Path tempConfig = Files.createTempFile("peersim-config-", ".cfg");
                    Files.copy(in, tempConfig, StandardCopyOption.REPLACE_EXISTING);
                    tempConfig.toFile().deleteOnExit();
                    return tempConfig;
                }
            }
        }
        return Paths.get("src/main/resources/peersim.cfg");
    }

    private String joinRequirements(int[] requirements) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < requirements.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(requirements[i]);
        }
        return builder.toString();
    }

    private String joinDatasetPaths(String[] datasetPaths) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < datasetPaths.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(datasetPaths[i].replace('\\', '/'));
        }
        return builder.toString();
    }
}
