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
            System.out.println("  Demandes: " + request.sessionNodeRequirements);

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
        String sessionRequirements;

        if (args != null && args.length >= 3) {
            datasetPath = args[0].trim();
            nodeCount = parsePositiveInt(args[1], 4);
            sessionRequirements = args[2].trim();
        } else {
            System.out.println("=== Mode démo PeerSim ===");
            try (Scanner scanner = new Scanner(System.in)) {
                System.out.print("Chemin du dataset CSV: ");
                datasetPath = scanner.nextLine().trim();

                System.out.print("Nombre total de nœuds du réseau: ");
                nodeCount = parsePositiveInt(scanner.nextLine(), 4);

                System.out.print("Nœuds requis par apprentissage (ex: 2,6): ");
                sessionRequirements = scanner.nextLine().trim();
            }
        }

        if (datasetPath.isEmpty()) {
            datasetPath = "src/main/resources/sample_dataset.csv";
        }

        java.util.List<Integer> parsedRequirements = parseNodeRequirements(sessionRequirements);
        if (parsedRequirements.isEmpty()) {
            parsedRequirements = java.util.Arrays.asList(2, 2);
        }

        return new SimulationRequest(datasetPath, Math.max(1, nodeCount), parsedRequirements);
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
        overrides.put("simulation.cycles", String.valueOf(Math.max(24, request.sessionNodeRequirements.size() * 12)));
        overrides.put("control.learning.datasetPath", request.datasetPath.replace('\\', '/'));
        overrides.put("control.learning.batchStrategy", "ROUND_ROBIN");
        overrides.put("control.learning.maxBatchesPerNode", "2");
        overrides.put("control.learning.sessionRequirements", joinRequirements(request.sessionNodeRequirements));
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
     * Parse une liste de besoins en nœuds séparés par des virgules.
     */
    private static java.util.List<Integer> parseNodeRequirements(String rawValue) {
        java.util.List<Integer> counts = new java.util.ArrayList<>();
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return counts;
        }

        String[] parts = rawValue.split(",");
        for (String part : parts) {
            int parsed = parsePositiveInt(part, -1);
            if (parsed > 0) {
                counts.add(parsed);
            }
        }
        return counts;
    }

    /**
     * Transforme les besoins en chaîne exploitable dans la configuration PeerSim.
     */
    private static String joinRequirements(java.util.List<Integer> requirements) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < requirements.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(requirements.get(i));
        }
        return builder.toString();
    }

    /**
     * Demande utilisateur contenant les paramètres de la simulation.
     */
    private static final class SimulationRequest {
        private final String datasetPath;
        private final int nodeCount;
        private final java.util.List<Integer> sessionNodeRequirements;

        private SimulationRequest(String datasetPath, int nodeCount, java.util.List<Integer> sessionNodeRequirements) {
            this.datasetPath = datasetPath;
            this.nodeCount = nodeCount;
            this.sessionNodeRequirements = sessionNodeRequirements;
        }
    }
}