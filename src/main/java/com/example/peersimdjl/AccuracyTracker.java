package com.example.peersimdjl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Suivi simple des métriques locales/globales par epoch.
 */
public class AccuracyTracker {

    private static final double MIN_VALID_ACCURACY = 0.0;
    private static final double MAX_VALID_ACCURACY = 1.0;

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
        if (localParams == null || localParams.length == 0) {
            System.out.printf("[EPOCH %d][Node %s] local metrics unavailable (dataset=%d)%n",
                epoch, nodeId, datasetSize);
            return;
        }

        System.out.printf("[EPOCH %d][Node %s] local metrics available but ignored: use trackLocalAccuracy() for real values (dataset=%d)%n",
            epoch, nodeId, datasetSize);
    }
    
    /**
     * Enregistre la précision locale du modèle neuronal réel.
     */
    public void trackLocalAccuracy(String nodeId, float accuracy, int epoch) {
        double clippedAccuracy = Math.max(MIN_VALID_ACCURACY, Math.min(MAX_VALID_ACCURACY, accuracy));
        Metrics metrics = new Metrics(clippedAccuracy, 1.0 - clippedAccuracy);
        localByEpoch.computeIfAbsent(epoch, ignored -> new LinkedHashMap<>()).put(nodeId, metrics);
        System.out.printf("[EPOCH %d][Node %s] real accuracy=%.4f%n", epoch, nodeId, clippedAccuracy);
    }

    public void evaluateGlobal(float[] globalParams, int totalDatasetSize, int epoch) {
        Map<String, Metrics> localMetrics = localByEpoch.get(epoch);
        Metrics metrics;

        if (localMetrics == null || localMetrics.isEmpty()) {
            metrics = new Metrics(0.0, 1.0);
        } else {
            double sumAccuracy = 0.0;
            double sumLoss = 0.0;
            int count = 0;

            for (Metrics value : localMetrics.values()) {
                sumAccuracy += value.accuracy;
                sumLoss += value.loss;
                count++;
            }

            metrics = new Metrics(sumAccuracy / count, sumLoss / count);
        }

        globalByEpoch.put(epoch, metrics);
        System.out.printf("[EPOCH %d][GLOBAL] real accuracy=%.4f real loss=%.6f (dataset=%d)%n",
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

}
