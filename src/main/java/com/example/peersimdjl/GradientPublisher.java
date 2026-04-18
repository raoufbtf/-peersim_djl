package com.example.peersimdjl;

import peersim.core.Node;

/**
 * Publie les gradients de paramètres dans le DHT Chord.
 */
public class GradientPublisher {

    private static final float MIN_ABS_DELTA = 1e-6f;
    private static final float TOP_K_RATIO = 0.05f;
    private static final int MIN_TOP_K = 8;

    private final ChordProtocol chord;
    private final int expectedContributors;

    public GradientPublisher(ChordProtocol chord, int expectedContributors) {
        this.chord = chord;
        this.expectedContributors = expectedContributors;
    }

    public void publishGradients(float[] prevWeights, float[] newWeights, int epoch, String nodeId, int datasetSize) {
        if (prevWeights == null || newWeights == null) {
            return;
        }

        int count = Math.min(prevWeights.length, newWeights.length);
        if (count == 0) {
            return;
        }

        float[] deltas = new float[count];
        for (int paramIndex = 0; paramIndex < count; paramIndex++) {
            deltas[paramIndex] = newWeights[paramIndex] - prevWeights[paramIndex];
        }

        int[] selectedIndices = selectTopKIndices(deltas);
        int sentCount = 0;

        for (int paramIndex : selectedIndices) {
            float delta = deltas[paramIndex];
            if (Math.abs(delta) < MIN_ABS_DELTA) {
                continue;
            }
            String gradKey = FederatedDhtKeys.gradientKey(epoch, paramIndex);

            Node responsibleNode = chord.lookup(gradKey);
            if (responsibleNode == null) {
                System.err.println("[EPOCH " + epoch + "][Node " + nodeId + "] lookup KO for key " + gradKey);
                continue;
            }

            ChordProtocol responsibleChord = (ChordProtocol) responsibleNode.getProtocol(ChordProtocol.CHORD_PROTOCOL_ID);
            if (responsibleChord == null) {
                System.err.println("[EPOCH " + epoch + "][Node " + nodeId + "] responsibleChord KO for key " + gradKey);
                continue;
            }

            Object existing = responsibleChord.getLocal(gradKey);
            ParamDepot depot;
            if (existing instanceof ParamDepot) {
                depot = (ParamDepot) existing;
            } else {
                depot = new ParamDepot(paramIndex, epoch, expectedContributors);
            }

            ParamEntry entry = new ParamEntry(nodeId, paramIndex, epoch, delta, datasetSize, System.currentTimeMillis());
            depot.addContribution(entry);
            responsibleChord.putLocal(gradKey, depot);
            sentCount++;

            if (ChordProtocol.DEBUGChord) {
                int hops = estimateHopCount(chord, responsibleNode);
                System.out.println("[EPOCH " + epoch + "][Node " + nodeId + "] grad param[" + paramIndex + "]=" + delta
                        + " -> key=" + gradKey
                        + " target=" + responsibleChord.nodeIdString
                        + " hops=" + hops);
            }
        }

        if (sentCount == 0) {
            int fallbackParam = indexOfMaxAbs(deltas);
            if (fallbackParam >= 0) {
                publishSingleDelta(epoch, nodeId, datasetSize, fallbackParam, deltas[fallbackParam]);
                sentCount = 1;
            }
        }

        System.out.println("[EPOCH " + epoch + "][Node " + nodeId + "] sparse publish=" + sentCount
                + "/" + count + " (topK=" + selectedIndices.length + ", eps=" + MIN_ABS_DELTA + ")");
    }

    private int[] selectTopKIndices(float[] deltas) {
        int count = deltas.length;
        int topK = Math.max(MIN_TOP_K, (int) Math.ceil(count * TOP_K_RATIO));
        topK = Math.min(topK, count);

        Integer[] indices = new Integer[count];
        for (int i = 0; i < count; i++) {
            indices[i] = i;
        }

        java.util.Arrays.sort(indices, (left, right) ->
                Float.compare(Math.abs(deltas[right]), Math.abs(deltas[left])));

        int[] selected = new int[topK];
        for (int i = 0; i < topK; i++) {
            selected[i] = indices[i];
        }
        return selected;
    }

    private int indexOfMaxAbs(float[] deltas) {
        if (deltas.length == 0) {
            return -1;
        }
        int index = 0;
        float best = Math.abs(deltas[0]);
        for (int i = 1; i < deltas.length; i++) {
            float value = Math.abs(deltas[i]);
            if (value > best) {
                best = value;
                index = i;
            }
        }
        return index;
    }

    private void publishSingleDelta(int epoch, String nodeId, int datasetSize, int paramIndex, float delta) {
        String gradKey = FederatedDhtKeys.gradientKey(epoch, paramIndex);
        Node responsibleNode = chord.lookup(gradKey);
        if (responsibleNode == null) {
            return;
        }
        ChordProtocol responsibleChord = (ChordProtocol) responsibleNode.getProtocol(ChordProtocol.CHORD_PROTOCOL_ID);
        if (responsibleChord == null) {
            return;
        }

        Object existing = responsibleChord.getLocal(gradKey);
        ParamDepot depot = existing instanceof ParamDepot
                ? (ParamDepot) existing
                : new ParamDepot(paramIndex, epoch, expectedContributors);

        ParamEntry entry = new ParamEntry(nodeId, paramIndex, epoch, delta, datasetSize, System.currentTimeMillis());
        depot.addContribution(entry);
        responsibleChord.putLocal(gradKey, depot);
    }

    private int estimateHopCount(ChordProtocol sourceChord, Node target) {
        if (sourceChord == null || sourceChord.selfNode == null || target == null) {
            return -1;
        }
        if (sourceChord.selfNode == target) {
            return 0;
        }

        Node cursor = sourceChord.selfNode;
        int guard = 0;
        while (cursor != null && guard <= peersim.core.Network.size()) {
            ChordProtocol cp = (ChordProtocol) cursor.getProtocol(ChordProtocol.CHORD_PROTOCOL_ID);
            if (cp == null || cp.successor == null) {
                return -1;
            }
            cursor = cp.successor;
            guard++;
            if (cursor == target) {
                return guard;
            }
        }
        return -1;
    }
}
