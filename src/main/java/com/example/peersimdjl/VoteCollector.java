package com.example.peersimdjl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collecte les votes de convergence et prend une décision de quorum.
 */
public class VoteCollector {

    public enum Decision {
        STOP_CONVERGED,
        CONTINUE,
        RESET_LR
    }

    private final ChordProtocol chord;

    public VoteCollector(ChordProtocol chord) {
        this.chord = chord;
    }

    public Decision collectAndDecide(int epoch, List<String> allNodeIds) {
        Map<String, ConvergenceVoter.Vote> votes = new LinkedHashMap<>();

        for (String nodeId : allNodeIds) {
            String key = FederatedDhtKeys.voteKey(epoch, nodeId);
            Object raw = chord.get(key);
            if (!(raw instanceof String)) {
                System.out.println("[EPOCH " + epoch + "] votes incomplets: manquant " + nodeId);
                return Decision.CONTINUE;
            }

            try {
                votes.put(nodeId, ConvergenceVoter.Vote.valueOf((String) raw));
            } catch (Exception e) {
                System.out.println("[EPOCH " + epoch + "] vote invalide pour " + nodeId + ": " + raw);
                return Decision.CONTINUE;
            }
        }

        Decision decision = decideFromVotes(votes);
        chord.put(FederatedDhtKeys.decisionKey(epoch), decision.name());
        System.out.println("[EPOCH " + epoch + "] votes=" + votes + " => decision=" + decision);
        return decision;
    }

    Decision decideFromVotes(Map<String, ConvergenceVoter.Vote> votes) {
        int total = votes.size();
        int convergeCount = 0;
        int divergeCount = 0;

        for (ConvergenceVoter.Vote vote : votes.values()) {
            if (vote == ConvergenceVoter.Vote.CONVERGE) {
                convergeCount++;
            } else if (vote == ConvergenceVoter.Vote.DIVERGE) {
                divergeCount++;
            }
        }

        if (divergeCount > 0) {
            return Decision.RESET_LR;
        }

        double convergeRatio = total == 0 ? 0.0 : (double) convergeCount / (double) total;
        if (convergeRatio > 0.51d) {
            return Decision.STOP_CONVERGED;
        }

        return Decision.CONTINUE;
    }
}
