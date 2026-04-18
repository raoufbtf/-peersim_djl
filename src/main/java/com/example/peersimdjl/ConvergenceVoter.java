package com.example.peersimdjl;

/**
 * Calcule et publie le vote de convergence d'un nœud.
 */
public class ConvergenceVoter {

    public enum Vote {
        CONVERGE,
        CONTINUE,
        DIVERGE
    }

    private final ChordProtocol chord;

    public ConvergenceVoter(ChordProtocol chord) {
        this.chord = chord;
    }

    public Vote computeVote(float[] globalEpoch, float[] globalPrevEpoch) {
        if (globalEpoch == null || globalPrevEpoch == null || globalEpoch.length == 0 || globalPrevEpoch.length == 0) {
            return Vote.CONTINUE;
        }

        int count = Math.min(globalEpoch.length, globalPrevEpoch.length);
        float sumAbs = 0f;
        for (int i = 0; i < count; i++) {
            sumAbs += Math.abs(globalEpoch[i] - globalPrevEpoch[i]);
        }

        float meanAbsDelta = sumAbs / count;
        if (meanAbsDelta < 0.001f) {
            return Vote.CONVERGE;
        }
        if (meanAbsDelta > 1.0f) {
            return Vote.DIVERGE;
        }
        return Vote.CONTINUE;
    }

    public void publishVote(Vote vote, int epoch, String nodeId) {
        if (vote == null || nodeId == null || nodeId.trim().isEmpty()) {
            return;
        }

        String key = FederatedDhtKeys.voteKey(epoch, nodeId);
        chord.put(key, vote.name());
        System.out.println("[EPOCH " + epoch + "][Node " + nodeId + "] vote=" + vote + " key=" + key);
    }
}
