package com.peersim.gossip;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LossVectorImpl implements LossVector {

    private final Map<String, Double> losses;
    private final ConcurrentHashMap<String, Integer> versions;

    public LossVectorImpl() {
        this.losses = new ConcurrentHashMap<>();
        this.versions = new ConcurrentHashMap<>();
    }

    @Override
    public void update(String peerId, double loss, int version) {
        final double[] deltaHolder = new double[1];

        versions.compute(peerId, (key, currentVersion) -> {
            int previousVersion = currentVersion == null ? Integer.MIN_VALUE : currentVersion;
            if (version <= previousVersion) {
                Double currentLoss = losses.get(peerId);
                deltaHolder[0] = currentLoss == null ? 0.0d : getMaxMinDelta();
                return currentVersion;
            }

            losses.put(peerId, loss);
            deltaHolder[0] = getMaxMinDelta();
            return version;
        });

        System.out.println("[LOSS_UPDATE] peerId=" + peerId + " loss=" + loss + " delta=" + deltaHolder[0]);
    }

    @Override
    public boolean isConverged(double epsilon) {
        return getMaxMinDelta() < epsilon;
    }

    @Override
    public double getMaxMinDelta() {
        if (losses.isEmpty()) {
            return 0.0d;
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (Double loss : losses.values()) {
            if (loss < min) {
                min = loss;
            }
            if (loss > max) {
                max = loss;
            }
        }

        return max - min;
    }
}
