package com.example.peersimdjl;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParamDepotTest {

    @Test
    void aggregateShouldUseWeightedFedAvg() {
        ParamDepot depot = new ParamDepot(2, 1, 3);

        depot.addContribution(new ParamEntry("N1", 2, 1, 1.0f, 10, 1000L));
        depot.addContribution(new ParamEntry("N2", 2, 1, 3.0f, 30, 1001L));
        depot.addContribution(new ParamEntry("N3", 2, 1, 2.0f, 10, 1002L));

        float aggregated = depot.aggregate();

        assertEquals(2.4f, aggregated, 1e-6f);
        assertTrue(depot.isAggregated());
        assertEquals(aggregated, depot.getAggregatedValue(), 1e-6f);
    }

    @Test
    void addContributionShouldBeIdempotentPerNodeId() {
        ParamDepot depot = new ParamDepot(0, 3, 2);

        depot.addContribution(new ParamEntry("N1", 0, 3, 0.2f, 10, 1000L));
        depot.addContribution(new ParamEntry("N1", 0, 3, 0.9f, 100, 1001L));

        assertEquals(1, depot.getContributions().size());
        assertEquals(0.2f, depot.getContributions().get("N1").getGradientDelta(), 1e-6f);
    }

    @Test
    void completeAndMissingContributorsShouldBeConsistent() {
        ParamDepot depot = new ParamDepot(1, 5, 3);
        List<String> allNodes = Arrays.asList("N1", "N2", "N3");

        depot.addContribution(new ParamEntry("N1", 1, 5, 0.3f, 15, 1000L));
        assertFalse(depot.isComplete());
        assertEquals(Arrays.asList("N2", "N3"), depot.missingContributors(allNodes));

        depot.addContribution(new ParamEntry("N2", 1, 5, -0.1f, 20, 1001L));
        depot.addContribution(new ParamEntry("N3", 1, 5, 0.5f, 10, 1002L));
        assertTrue(depot.isComplete());
        assertTrue(depot.missingContributors(allNodes).isEmpty());
    }

    @Test
    void aggregateShouldFallbackToSimpleAverageWhenWeightsAreZero() {
        ParamDepot depot = new ParamDepot(4, 7, 2);
        depot.addContribution(new ParamEntry("N1", 4, 7, 2.0f, 0, 1000L));
        depot.addContribution(new ParamEntry("N2", 4, 7, 4.0f, 0, 1001L));

        assertEquals(3.0f, depot.aggregate(), 1e-6f);
    }
}
