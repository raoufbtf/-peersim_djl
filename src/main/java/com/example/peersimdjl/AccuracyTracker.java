package com.example.peersimdjl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Suivi simple des métriques locales/globales par epoch.
 */
public class AccuracyTracker {

    private static final class Metrics {
        private final double accuracy;
        private final double loss;

        private Metrics(double accuracy, double loss) {
            this.accuracy = accuracy;
            this.loss = loss;
        }
    }

    private final Map<Integer, Map<String, Metrics>> localByEpoch = new LinkedHashMap<>();
    private final Map<Integer, Metrics> globalByEpoch = new LinkedHashMap<>();

    public void evaluateLocal(float[] localParams, int datasetSize, int epoch, String nodeId) {
        Metrics metrics = computeMetrics(localParams, datasetSize);
        localByEpoch.computeIfAbsent(epoch, ignored -> new LinkedHashMap<>()).put(nodeId, metrics);
        System.out.printf("[EPOCH %d][Node %s] local accuracy=%.4f loss=%.6f (dataset=%d)%n",
                epoch, nodeId, metrics.accuracy, metrics.loss, datasetSize);
    }
    
    /**
     * Enregistre la précision locale du modèle neuronal réel.
     */
    public void trackLocalAccuracy(String nodeId, float accuracy, int epoch) {
        Metrics metrics = new Metrics(accuracy, 1.0 - accuracy); // loss = 1 - accuracy pour simplifier
        localByEpoch.computeIfAbsent(epoch, ignored -> new LinkedHashMap<>()).put(nodeId, metrics);
        System.out.printf("[EPOCH %d][Node %s] MLP accuracy=%.4f%n", epoch, nodeId, accuracy);
    }

    public void evaluateGlobal(float[] globalParams, int totalDatasetSize, int epoch) {
        Metrics metrics = computeMetrics(globalParams, totalDatasetSize);
        globalByEpoch.put(epoch, metrics);
        System.out.printf("[EPOCH %d][GLOBAL] accuracy=%.4f loss=%.6f (dataset=%d)%n",
                epoch, metrics.accuracy, metrics.loss, totalDatasetSize);
    }

    public void printEpochSummary(String sessionName, String datasetPath, int epoch) {
        System.out.println("========================================");
        System.out.println("[EPOCH " + epoch + "] SUMMARY");
        if (sessionName != null && !sessionName.trim().isEmpty()) {
            System.out.println("  Session   : " + sessionName);
        }
        if (datasetPath != null && !datasetPath.trim().isEmpty()) {
            System.out.println("  Dataset   : " + datasetPath);
        }

        Map<String, Metrics> local = localByEpoch.get(epoch);
        if (local != null) {
            for (Map.Entry<String, Metrics> entry : local.entrySet()) {
                Metrics m = entry.getValue();
                System.out.printf("  Node %-8s | acc=%.4f | loss=%.6f%n", entry.getKey(), m.accuracy, m.loss);
            }
        }

        Metrics global = globalByEpoch.get(epoch);
        if (global != null) {
            System.out.printf("  GLOBAL     | acc=%.4f | loss=%.6f%n", global.accuracy, global.loss);
        }

        System.out.println("========================================");
    }

    private Metrics computeMetrics(float[] params, int datasetSize) {
        if (params == null || params.length == 0) {
            return new Metrics(0.0, 1.0);
        }

        double sumAbs = 0.0;
        for (float value : params) {
            sumAbs += Math.abs(value);
        }

        double meanAbs = sumAbs / params.length;
        double sampleFactor = Math.max(1.0, Math.log10(Math.max(10, datasetSize)));
        double loss = meanAbs / sampleFactor;
        double accuracy = 1.0 / (1.0 + loss);
        return new Metrics(accuracy, loss);
    }
}
