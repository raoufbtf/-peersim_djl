package com.example.peersimdjl;

import peersim.Simulator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Tiny runner dedicated to the decentralized learning simulation.
 */
public final class DecentralizedLearningApp {

    private DecentralizedLearningApp() {
    }

    public static void main(String[] args) {
        try {
            String configPath = args != null && args.length > 0
                    ? args[0]
                    : resolveConfigPath();

            System.out.println("Launching decentralized PeerSim simulation with config: " + configPath);
            Simulator.main(new String[]{configPath});
        } catch (Exception e) {
            System.err.println("Failed to start decentralized simulation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String resolveConfigPath() throws URISyntaxException {
        URL resource = DecentralizedLearningApp.class.getClassLoader().getResource("decentralized-learning.cfg");
        if (resource != null) {
            if ("file".equalsIgnoreCase(resource.getProtocol())) {
                Path configPath = Paths.get(resource.toURI());
                return configPath.toString();
            }

            try (InputStream in = DecentralizedLearningApp.class.getClassLoader().getResourceAsStream("decentralized-learning.cfg")) {
                if (in != null) {
                    Path tempConfig = Files.createTempFile("decentralized-learning-", ".cfg");
                    Files.copy(in, tempConfig, StandardCopyOption.REPLACE_EXISTING);
                    tempConfig.toFile().deleteOnExit();
                    return tempConfig.toString();
                }
            } catch (IOException e) {
                throw new RuntimeException("Impossible de copier decentralized-learning.cfg depuis les ressources", e);
            }
        }

        return "src/main/resources/decentralized-learning.cfg";
    }
}