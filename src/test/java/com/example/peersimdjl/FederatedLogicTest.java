package com.example.peersimdjl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FederatedLogicTest {

    @Test
    void convergenceVoterShouldReturnConvergeContinueDiverge() {
        ConvergenceVoter voter = new ConvergenceVoter(null);

        assertEquals(
                ConvergenceVoter.Vote.CONVERGE,
                voter.computeVote(new float[]{1.0000f, 2.0004f}, new float[]{1.0003f, 2.0000f})
        );

        assertEquals(
                ConvergenceVoter.Vote.CONTINUE,
                voter.computeVote(new float[]{1.2f, 2.0f}, new float[]{1.0f, 2.0f})
        );

        assertEquals(
                ConvergenceVoter.Vote.DIVERGE,
                voter.computeVote(new float[]{3.5f, 2.0f}, new float[]{1.0f, 2.0f})
        );
    }

    @Test
    void voteCollectorDecisionRulesShouldMatchSpec() {
        VoteCollector collector = new VoteCollector(null);

        Map<String, ConvergenceVoter.Vote> votesReset = new LinkedHashMap<>();
        votesReset.put("N1", ConvergenceVoter.Vote.CONVERGE);
        votesReset.put("N2", ConvergenceVoter.Vote.DIVERGE);
        assertEquals(VoteCollector.Decision.RESET_LR, collector.decideFromVotes(votesReset));

        Map<String, ConvergenceVoter.Vote> votesStop = new LinkedHashMap<>();
        votesStop.put("N1", ConvergenceVoter.Vote.CONVERGE);
        votesStop.put("N2", ConvergenceVoter.Vote.CONVERGE);
        votesStop.put("N3", ConvergenceVoter.Vote.CONTINUE);
        assertEquals(VoteCollector.Decision.STOP_CONVERGED, collector.decideFromVotes(votesStop));

        Map<String, ConvergenceVoter.Vote> votesContinue = new LinkedHashMap<>();
        votesContinue.put("N1", ConvergenceVoter.Vote.CONTINUE);
        votesContinue.put("N2", ConvergenceVoter.Vote.CONTINUE);
        votesContinue.put("N3", ConvergenceVoter.Vote.CONVERGE);
        assertEquals(VoteCollector.Decision.CONTINUE, collector.decideFromVotes(votesContinue));
    }

    @Test
    void globalModelCollectorShouldReturnNullWhenIncomplete() {
        GlobalModelCollector collector = new GlobalModelCollector(null);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(FederatedDhtKeys.globalKey(2, 0), 0.25f);
        values.put(FederatedDhtKeys.globalKey(2, 2), 0.75f);

        assertNull(collector.collectGlobalModelFromMap(2, 3, values));
    }

    @Test
    void globalModelCollectorShouldReturnArrayWhenComplete() {
        GlobalModelCollector collector = new GlobalModelCollector(null);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put(FederatedDhtKeys.globalKey(2, 0), 0.25f);
        values.put(FederatedDhtKeys.globalKey(2, 1), 0.50f);
        values.put(FederatedDhtKeys.globalKey(2, 2), 0.75f);

        float[] model = collector.collectGlobalModelFromMap(2, 3, values);

        assertArrayEquals(new float[]{0.25f, 0.50f, 0.75f}, model, 1e-6f);
    }

    @Test
    void paramEntryChecksumShouldBeStableForSameInputs() {
        ParamEntry entry1 = new ParamEntry("N1", 1, 2, 0.123f, 10, 1000L);
        ParamEntry entry2 = new ParamEntry("N1", 1, 2, 0.123f, 99, 9999L);

        assertEquals(entry1.getChecksum(), entry2.getChecksum());
        assertEquals(64, entry1.getChecksum().length());
    }
}
