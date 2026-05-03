package com.peersim.gossip;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GossipSmokeTest {

    @Test
    void gossipFlow_selectsNeighbors_andUpdatesState() {
        GossipNetworkImpl nodeA = new GossipNetworkImpl("nodeA", 2, 3);
        GossipNetworkImpl nodeB = new GossipNetworkImpl("nodeB", 2, 3);
        GossipNetworkImpl nodeC = new GossipNetworkImpl("nodeC", 2, 3);

        assertTrue(nodeB.getNeighbors().isEmpty());
        assertTrue(nodeC.getNeighbors().isEmpty());

        nodeA.gossipLoss(0.42d, 1);

        List<String> neighbors = nodeA.getNeighbors();
        assertTrue(neighbors.size() >= 1);
        assertTrue(neighbors.size() <= 2);
        assertTrue(neighbors.contains("nodeB") || neighbors.contains("nodeC"));

        LossVector vector = new LossVectorImpl();
        vector.update("nodeA", 0.42d, 1);
        vector.update("nodeB", 0.45d, 2);
        vector.update("nodeB", 0.10d, 1);
        assertEquals(0.03d, vector.getMaxMinDelta(), 0.0001d);
        assertTrue(vector.isConverged(0.05d));

        ConvergenceTracker tracker = new ConvergenceTrackerImpl("nodeA");
        tracker.recordVote("nodeB");
        tracker.recordVote("nodeC");
        assertTrue(tracker.hasQuorum(2, 0.7d));
    }
}
