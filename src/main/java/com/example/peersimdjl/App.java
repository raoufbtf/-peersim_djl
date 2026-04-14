package com.example.peersimdjl;

import peersim.Simulator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Point d'entrée de l'application de simulation PeerSim.
 *
 * Cette classe initialise l'exécution de PeerSim avec le fichier
 * de configuration `peersim.cfg` résolu depuis les ressources,
 * puis bascule vers un chemin local de secours si nécessaire.
 */
public class App {

    /**
     * Démarre la simulation PeerSim en mode interactif.
     * L'utilisateur fournit le dataset et le nombre de nœuds souhaité.
     *
     * @param args Arguments CLI optionnels :
     *             args[0] = chemin du dataset CSV,
     *             args[1] = nombre de nœuds.
     */
    public static void main(String[] args) {
        try {
            SimulationRequest request = readSimulationRequest(args);

            // Désactiver les logs Chord pour n'afficher que le debug apprentissage.
            ChordProtocol.DEBUGChord = false;

            Path configPath = buildInteractiveConfig(resolveConfigPath(), request);

            System.out.println("Démarrage de la simulation PeerSim...");
            System.out.println("  Dataset: " + request.datasetPath);
            System.out.println("  Nœuds   : " + request.nodeCount);
            System.out.println("  Sessions: " + request.sessionCount);

            Simulator.main(new String[]{configPath.toString()});
            System.out.println("Simulation terminée.");
        } catch (Exception e) {
            System.err.println("Erreur lors de la simulation : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Résout le chemin du fichier de configuration PeerSim.
     *
     * @return Le chemin absolu ou relatif vers `peersim.cfg`.
     * @throws URISyntaxException Si l'URL de ressource ne peut pas être convertie en URI.
     */
    private static String resolveConfigPath() throws URISyntaxException {
        URL resource = App.class.getClassLoader().getResource("peersim.cfg");
        if (resource != null) {
            Path configPath = Paths.get(resource.toURI());
            return configPath.toString();
        }

        return "src/main/resources/peersim.cfg";
    }

    /**
     * Lit la demande de simulation depuis les arguments CLI ou l'entrée utilisateur.
     */
    private static SimulationRequest readSimulationRequest(String[] args) {
        String datasetPath;
        int nodeCount;
        int sessionCount;

        if (args != null && args.length >= 3) {
            datasetPath = args[0].trim();
            nodeCount = parsePositiveInt(args[1], 4);
            sessionCount = parsePositiveInt(args[2], 1);
        } else {
            System.out.println("=== Mode démo PeerSim ===");
            try (Scanner scanner = new Scanner(System.in)) {
                System.out.print("Chemin du dataset CSV: ");
                datasetPath = scanner.nextLine().trim();

                System.out.print("Nombre de nœuds à utiliser: ");
                nodeCount = parsePositiveInt(scanner.nextLine(), 4);

                System.out.print("Nombre d'apprentissages à lancer (1 ou 2): ");
                sessionCount = parsePositiveInt(scanner.nextLine(), 1);
            }
        }

        if (datasetPath.isEmpty()) {
            datasetPath = "src/main/resources/sample_dataset.csv";
        }

        return new SimulationRequest(datasetPath, Math.max(1, nodeCount), Math.max(1, Math.min(sessionCount, 2)));
    }

    /**
     * Construit un fichier de configuration temporaire avec les paramètres saisis.
     */
    private static Path buildInteractiveConfig(String baseConfigPath, SimulationRequest request) throws IOException {
        Path source = Paths.get(baseConfigPath);
        Path tempConfig = Files.createTempFile("peersim-interactive-", ".cfg");

        String original = Files.readString(source, StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(original);

        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("network.size", String.valueOf(request.nodeCount));
        overrides.put("simulation.cycles", "24");
        overrides.put("control.learning.datasetPath", request.datasetPath.replace('\\', '/'));
        overrides.put("control.learning.activeNodeCount", String.valueOf(request.nodeCount));
        overrides.put("control.learning.batchStrategy", "ROUND_ROBIN");
        overrides.put("control.learning.maxBatchesPerNode", "2");
        overrides.put("control.learning.sessionCount", String.valueOf(request.sessionCount));
        overrides.put("control.learning.pid", "0");

        builder.append(System.lineSeparator())
                .append("# Overrides de la démo interactive").append(System.lineSeparator());
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            builder.append(entry.getKey()).append(" = ").append(entry.getValue()).append(System.lineSeparator());
        }

        Files.writeString(tempConfig, builder.toString(), StandardCharsets.UTF_8);
        tempConfig.toFile().deleteOnExit();
        return tempConfig;
    }

    /**
     * Parse un entier positif avec valeur de repli.
     */
    private static int parsePositiveInt(String value, int defaultValue) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Demande utilisateur contenant les paramètres de la simulation.
     */
    private static final class SimulationRequest {
        private final String datasetPath;
        private final int nodeCount;
        private final int sessionCount;

        private SimulationRequest(String datasetPath, int nodeCount, int sessionCount) {
            this.datasetPath = datasetPath;
            this.nodeCount = nodeCount;
            this.sessionCount = sessionCount;
        }
    }
}