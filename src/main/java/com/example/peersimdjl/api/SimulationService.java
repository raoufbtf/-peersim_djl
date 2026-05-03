package com.example.peersimdjl.api;

import org.springframework.stereotype.Service;

import com.example.peersimdjl.ChordProtocol;
import com.example.peersimdjl.SimulationCommEventLogger;
import com.example.peersimdjl.events.Communication;
import com.example.peersimdjl.events.SimulationEvent;
import com.example.peersimdjl.events.SimulationEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import java.time.Instant;

/**
 * Service de simulation PeerSim + DJL.
 * LANCE PEERSIM DANS UN PROCESSUS FILS ISOLÉ
 * pour éviter que System.exit() ne tue le serveur Spring.
 *
 * FIXES APPLIED:
 *  1. Détection automatique de "Simulation terminée" dans les logs → arrêt propre
 *  2. Watchdog timeout de sécurité (10 minutes max)
 *  3. destroyForcibly() si destroy() ne suffit pas
 *  4. state mis à STOPPED correctement quand le processus se termine seul
 */
@Service
public class SimulationService {

    // ── Timeout maximum de la simulation (sécurité) ──────────────────────────
    private static final long WATCHDOG_TIMEOUT_MINUTES = 10L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AtomicReference<SimulationState> state  = new AtomicReference<>(SimulationState.IDLE);
    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile Process  simulatorProcess = null;
    private volatile Thread   watchdogThread   = null;

    private final SimulationEventPublisher eventPublisher;

    public SimulationService(SimulationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lancement
    // ─────────────────────────────────────────────────────────────────────────

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
            try {
                Path configPath = buildConfig(req);
                ChordProtocol.DEBUGChord = false;

                // 1. Récupérer le chemin de la JVM actuelle
                String javaHome = System.getProperty("java.home");
                String javaBin  = Paths.get(javaHome, "bin", "java.exe").toString();

                // 2. Récupérer le classpath (toutes les dépendances)
                String classpath = System.getProperty("java.class.path");

                // 3. Créer le processus fils
                ProcessBuilder pb = new ProcessBuilder(
                    javaBin,
                    "-cp", classpath,
                    "com.example.peersimdjl.DecentralizedLearningApp",
                    configPath.toString()
                );
                pb.redirectErrorStream(true);

                System.out.println("[SimulationService] Lancement PeerSim dans un processus fils...");
                publishEvent("INFO", "SIM_START", "Simulation started",
                        Map.of("configPath", configPath.toString()));

                simulatorProcess = pb.start();

                // 4. Thread de lecture des logs
                Thread logReader = new Thread(() -> readProcessLogs(simulatorProcess));
                logReader.setDaemon(true);
                logReader.start();

                // 5. ✅ FIX — Watchdog timeout (sécurité si la simulation tourne trop longtemps)
                startWatchdog(simulatorProcess);

                // 6. Attendre la fin du processus fils
                int exitCode = simulatorProcess.waitFor();

                // 7. Annuler le watchdog si la simulation s'est terminée normalement
                stopWatchdog();

                System.out.printf("[SimulationService] Simulation terminée (code: %d)%n", exitCode);
                publishEvent(
                    exitCode == 0 ? "INFO" : "WARN",
                    "SIM_END",
                    "Simulation ended with code " + exitCode,
                    Map.of("exitCode", exitCode)
                );

                // ✅ FIX — Mettre l'état à STOPPED quand le processus fils se termine
                state.set(SimulationState.STOPPED);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("[SimulationService] Simulation interrompue.");
                state.set(SimulationState.STOPPED);

            } catch (Exception e) {
                System.err.println("[SimulationService] Erreur: " + e.getMessage());
                publishEvent("ERROR", "SIM_ERROR", "Simulation error: " + e.getMessage(), Map.of());
                state.set(SimulationState.FAILED);

            } finally {
                terminateProcess();       // ✅ FIX — s'assurer que le processus est bien mort
                simulatorProcess = null;
                resetExecutor();
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Arrêt manuel
    // ─────────────────────────────────────────────────────────────────────────

    public void stop() {
        if (state.get() != SimulationState.RUNNING) {
            throw new IllegalStateException("Not running");
        }
        stopWatchdog();
        terminateProcess();
        executor.shutdownNow();
        state.set(SimulationState.STOPPED);
        resetExecutor();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lecture des logs du processus fils
    // ─────────────────────────────────────────────────────────────────────────

    private void readProcessLogs(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean endDetected = false;
            java.util.regex.Pattern cycleDonePattern = java.util.regex.Pattern.compile(".*cycle\\s*\\d+\\s*done.*");

            while ((line = reader.readLine()) != null) {

                if (line.startsWith(SimulationCommEventLogger.PREFIX)) {
                    Communication communication = parseCommunicationLine(line);
                    if (communication != null) {
                        eventPublisher.publishCommunication(communication);
                        continue;
                    }
                }

                // Normalize to lowercase for robust matching
                String lower = line.toLowerCase(java.util.Locale.ROOT);

                boolean matchesCycleDone = cycleDonePattern.matcher(lower).matches()
                        || lower.contains("cdsimulator: cycle");

                // Detect common end-of-simulation messages (French/English)
                boolean matchesEnd = lower.contains("simulation termin")
                        || lower.contains("simulation ended")
                        || lower.contains("simulation finished")
                        || lower.contains("end of simulation")
                        || lower.contains("simulation completed")
                        || lower.contains("peersim: exiting")
                        || lower.contains("cdsimulator: exit")
                        || matchesCycleDone;

                publishEvent("INFO", "SIM_LOG", line, Map.of());

                if (matchesEnd && !endDetected) {
                    endDetected = true;
                    System.out.println("[SimulationService] ✅ Fin détectée dans les logs → publication SIM_END (continuing to stream remaining logs)");
                    publishEvent("INFO", "SIM_END", "Detected end of simulation in logs: " + line, Map.of());
                    // DO NOT terminate the process here; continue streaming remaining logs until EOF.
                }
            }

            if (endDetected) {
                publishEvent("INFO", "SIM_LOG", "End-of-simulation marker detected; log stream ended.", Map.of());
            }

        } catch (IOException e) {
            publishEvent("ERROR", "SIM_LOG", "Log stream error: " + e.getMessage(), Map.of());
        }
    }

    private Communication parseCommunicationLine(String line) {
        try {
            String json = line.substring(SimulationCommEventLogger.PREFIX.length()).trim();
            Map<String, Object> payload = OBJECT_MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {
            });

            String type = stringValue(payload.get("type"));
            String from = stringValue(payload.get("from"));
            String to = stringValue(payload.get("to"));
            Long seq = longValue(payload.get("seq"));
            Integer epoch = intValue(payload.get("epoch"));
            Integer cycle = intValue(payload.get("cycle"));
            String param = stringValue(payload.get("param"));
            Double value = doubleValue(payload.get("value"));
            String voteCount = stringValue(payload.get("voteCount"));
            Double threshold = doubleValue(payload.get("threshold"));
            String detail = stringValue(payload.get("detail"));
            String timestamp = stringValue(payload.get("timestamp"));
            Long ts = longValue(payload.get("ts"));
                String id = (seq != null ? String.valueOf(seq) : (timestamp != null ? timestamp : Instant.now().toString()))
                    + "-" + (type != null ? type : "UNKNOWN")
                    + "-" + (from != null ? from : "?")
                    + "-" + (to != null ? to : "?")
                    + "-" + (param != null ? param : "-")
                    + "-" + (epoch != null ? epoch : -1)
                    + "-" + (cycle != null ? cycle : -1);

                return new Communication(seq, id, type, from, to, epoch, cycle, param, value, voteCount, threshold, detail,
                    timestamp, ts);
        } catch (Exception ex) {
            publishEvent("WARN", "SIM_LOG", "Malformed communication event line: " + ex.getMessage(), Map.of());
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ✅ FIX — Watchdog : arrêt forcé si timeout dépassé
    // ─────────────────────────────────────────────────────────────────────────

    private void startWatchdog(Process process) {
        watchdogThread = new Thread(() -> {
            try {
                boolean finished = process.waitFor(WATCHDOG_TIMEOUT_MINUTES, TimeUnit.MINUTES);
                if (!finished) {
                    System.err.printf(
                        "[SimulationService] ⚠️ Timeout %d min dépassé → arrêt forcé%n",
                        WATCHDOG_TIMEOUT_MINUTES);
                    publishEvent("WARN", "SIM_TIMEOUT",
                        "Simulation timeout after " + WATCHDOG_TIMEOUT_MINUTES + " minutes", Map.of());
                    terminateProcess();
                    state.set(SimulationState.STOPPED);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // Watchdog annulé normalement (simulation terminée avant le timeout)
            }
        });
        watchdogThread.setDaemon(true);
        watchdogThread.setName("sim-watchdog");
        watchdogThread.start();
    }

    private void stopWatchdog() {
        if (watchdogThread != null && watchdogThread.isAlive()) {
            watchdogThread.interrupt();
            watchdogThread = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ✅ FIX — Terminer le processus fils proprement
    // ─────────────────────────────────────────────────────────────────────────

    private void terminateProcess() {
        Process p = simulatorProcess;
        if (p != null && p.isAlive()) {
            System.out.println("[SimulationService] 🛑 Arrêt du processus PeerSim...");
            p.destroy(); // SIGTERM doux

            try {
                // Attendre 3 secondes que le processus se termine proprement
                if (!p.waitFor(3, TimeUnit.SECONDS)) {
                    System.err.println("[SimulationService] ⚠️ Processus résistant → SIGKILL forcé");
                    p.destroyForcibly(); // SIGKILL si toujours vivant
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                p.destroyForcibly();
            }

            System.out.println("[SimulationService] ✅ Processus PeerSim arrêté.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilitaires
    // ─────────────────────────────────────────────────────────────────────────

    public SimulationState getState() {
        return state.get();
    }

    private void resetExecutor() {
        executor.shutdownNow();
        executor = Executors.newSingleThreadExecutor();
    }

    private void publishEvent(String level, String type, String message, Map<String, Object> data) {
        eventPublisher.publish(new SimulationEvent(
            Instant.now(), level, type, null, null, message, data
        ));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construction de la config PeerSim
    // ─────────────────────────────────────────────────────────────────────────

    private Path buildConfig(SimulationRequest req) throws IOException, URISyntaxException {
        Path source   = resolveConfigPath();
        String original = Files.readString(source, StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(original);

        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("network.size",                          String.valueOf(req.getNetworkSize()));
        overrides.put("simulation.cycles",                     String.valueOf(Math.max(24, req.getSessionRequirements().length * 12)));
        overrides.put("control.learning.datasetPath",          req.getDatasetPaths()[0].replace('\\', '/'));
        overrides.put("control.learning.datasetPaths",         joinDatasetPaths(req.getDatasetPaths()));
        overrides.put("control.learning.batchStrategy",        req.getBatchStrategy());
        overrides.put("control.learning.maxBatchesPerNode",    String.valueOf(req.getMaxBatchesPerNode()));
        overrides.put("control.learning.sessionRequirements",  joinRequirements(req.getSessionRequirements()));
        overrides.put("control.learning.modelType",            req.getModelType());
        overrides.put("control.learning.pid",                  "0");

        builder.append(System.lineSeparator())
               .append("# Overrides API request").append(System.lineSeparator());
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            builder.append(entry.getKey())
                   .append(" = ")
                   .append(entry.getValue())
                   .append(System.lineSeparator());
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
            try (InputStream in = SimulationService.class.getClassLoader()
                    .getResourceAsStream("peersim.cfg")) {
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