package com.example.peersimdjl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dépôt local d'un paramètre pour un epoch, recevant les contributions de tous les nœuds.
 */
public class ParamDepot implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int paramIndex;
    private final int epoch;
    private final int expectedContributors;
    private final Map<String, ParamEntry> contributions;
    private boolean aggregated;
    private float aggregatedValue;

    public ParamDepot(int paramIndex, int epoch, int expectedContributors) {
        this.paramIndex = paramIndex;
        this.epoch = epoch;
        this.expectedContributors = expectedContributors;
        this.contributions = new LinkedHashMap<>();
        this.aggregated = false;
        this.aggregatedValue = 0f;
    }

    public synchronized void addContribution(ParamEntry entry) {
        if (entry == null || entry.getNodeId() == null) {
            return;
        }
        if (entry.getEpoch() != epoch || entry.getParamIndex() != paramIndex) {
            if (ChordProtocol.DEBUGChord) {
                System.out.println("[EPOCH " + epoch + "][Depot param[" + paramIndex + "]] contribution ignorée"
                        + " (mismatch epoch/param): " + entry);
            }
            return;
        }
        if (contributions.containsKey(entry.getNodeId())) {
            if (ChordProtocol.DEBUGChord) {
                System.out.println("[EPOCH " + epoch + "][Depot param[" + paramIndex + "]] duplicate ignoré: node="
                        + entry.getNodeId());
            }
            return;
        }

        contributions.put(entry.getNodeId(), entry);
        if (ChordProtocol.DEBUGChord) {
            System.out.println("[EPOCH " + epoch + "][Depot param[" + paramIndex + "]] contribution ajoutée: node="
                    + entry.getNodeId() + ", delta=" + entry.getGradientDelta()
                    + ", size=" + entry.getDatasetSize() + ", count=" + contributions.size()
                    + "/" + expectedContributors);
        }
    }

    public synchronized boolean isComplete() {
        return contributions.size() >= expectedContributors;
    }

    public synchronized float aggregate() {
        if (aggregated) {
            return aggregatedValue;
        }
        if (contributions.isEmpty()) {
            aggregated = true;
            aggregatedValue = 0f;
            return aggregatedValue;
        }

        float weightedSum = 0f;
        int totalWeight = 0;
        for (ParamEntry entry : contributions.values()) {
            int weight = Math.max(0, entry.getDatasetSize());
            weightedSum += entry.getGradientDelta() * weight;
            totalWeight += weight;
        }

        if (totalWeight > 0) {
            aggregatedValue = weightedSum / totalWeight;
        } else {
            float sum = 0f;
            for (ParamEntry entry : contributions.values()) {
                sum += entry.getGradientDelta();
            }
            aggregatedValue = sum / contributions.size();
        }

        aggregated = true;
        if (ChordProtocol.DEBUGChord) {
            System.out.println("[EPOCH " + epoch + "][Depot param[" + paramIndex + "]] agrégation FedAvg terminée: "
                    + "value=" + aggregatedValue + ", totalWeight=" + totalWeight
                    + ", contributors=" + contributions.size());
        }
        return aggregatedValue;
    }

    public synchronized List<String> missingContributors(List<String> allNodeIds) {
        List<String> missing = new ArrayList<>();
        if (allNodeIds == null) {
            return missing;
        }
        for (String nodeId : allNodeIds) {
            if (nodeId != null && !contributions.containsKey(nodeId)) {
                missing.add(nodeId);
            }
        }
        return missing;
    }

    public synchronized String debugState(List<String> allNodeIds) {
        List<String> missing = missingContributors(allNodeIds);
        return "[EPOCH " + epoch + "][Depot param[" + paramIndex + "]]"
                + " contributors=" + contributions.size() + "/" + expectedContributors
                + ", aggregated=" + aggregated
                + ", aggregatedValue=" + aggregatedValue
                + ", missing=" + missing;
    }

    public int getParamIndex() {
        return paramIndex;
    }

    public int getEpoch() {
        return epoch;
    }

    public int getExpectedContributors() {
        return expectedContributors;
    }

    public synchronized Map<String, ParamEntry> getContributions() {
        return new LinkedHashMap<>(contributions);
    }

    public synchronized boolean isAggregated() {
        return aggregated;
    }

    public synchronized float getAggregatedValue() {
        return aggregatedValue;
    }

    @Override
    public synchronized String toString() {
        return "ParamDepot{" +
                "paramIndex=" + paramIndex +
                ", epoch=" + epoch +
                ", expectedContributors=" + expectedContributors +
                ", contributions=" + contributions.size() +
                ", aggregated=" + aggregated +
                ", aggregatedValue=" + aggregatedValue +
                '}';
    }
}
