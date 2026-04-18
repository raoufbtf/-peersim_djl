package com.example.peersimdjl;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FederatedScenarioIntegrationTest {

    @ParameterizedTest
    @ValueSource(ints = {2, 5, 10})
    void scenarioShouldPublishAggregateAndDecideConsistently(int nodeCount) {
        int epoch = 1;
        int numParams = 4;

        Map<String, ParamDepot> gradientDepots = new LinkedHashMap<>();

        for (int n = 0; n < nodeCount; n++) {
            String nodeId = "N" + n;
            int datasetSize = 10 + n;

            for (int p = 0; p < numParams; p++) {
                String gradKey = FederatedDhtKeys.gradientKey(epoch, p);
                final int paramIndex = p;
                ParamDepot depot = gradientDepots.computeIfAbsent(
                        gradKey,
                    ignored -> new ParamDepot(paramIndex, epoch, nodeCount)
                );

                float delta = ((n + 1) * (p + 1)) / 100f;
                depot.addContribution(new ParamEntry(nodeId, p, epoch, delta, datasetSize, 1000L + n));
            }
        }

        assertEquals(numParams, gradientDepots.size(), "Toutes les clés gradients doivent être publiées");

        Map<String, Float> globalValues = new LinkedHashMap<>();
        for (int p = 0; p < numParams; p++) {
            String gradKey = FederatedDhtKeys.gradientKey(epoch, p);
            ParamDepot depot = gradientDepots.get(gradKey);

            assertTrue(depot.isComplete(), "Chaque dépôt doit être complet");
            float aggregated = depot.aggregate();
            globalValues.put(FederatedDhtKeys.globalKey(epoch, p), aggregated);
        }

        assertEquals(numParams, globalValues.size(), "Toutes les clés globales doivent être publiées");

        VoteCollector collector = new VoteCollector(null);
        Map<String, ConvergenceVoter.Vote> votes = new LinkedHashMap<>();
        for (int n = 0; n < nodeCount; n++) {
            votes.put("N" + n, ConvergenceVoter.Vote.CONVERGE);
        }

        VoteCollector.Decision decision = collector.decideFromVotes(votes);
        assertEquals(VoteCollector.Decision.STOP_CONVERGED, decision, "Le quorum convergent doit arrêter");
    }
}
