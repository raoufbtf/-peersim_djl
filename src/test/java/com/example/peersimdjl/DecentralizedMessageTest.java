package com.example.peersimdjl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecentralizedMessageTest {

    @Test
    void gradientMessageCarriesExpectedFields() {
        GradientUpdateMsg msg = new GradientUpdateMsg(3, 7, 2, 0.25d, 16, 0.05d, 7);

        assertEquals(3, msg.senderNodeId);
        assertEquals(7, msg.epoch);
        assertEquals(2, msg.paramIndex);
        assertEquals(0.25d, msg.gradient);
        assertEquals(16, msg.sampleCount);
        assertEquals(0.05d, msg.learningRate);
        assertEquals(7, msg.version);
        assertNotNull(msg.messageId);
    }

    @Test
    void convergenceMessageCarriesExpectedFields() {
        GossipConvergenceMsg msg = new GossipConvergenceMsg(5, 11, 0.0042d, true);

        assertEquals(5, msg.nodeId);
        assertEquals(11, msg.epoch);
        assertEquals(0.0042d, msg.delta);
        assertTrue(msg.locallyConverged);
        assertNotNull(msg.messageId);
    }

    @Test
    void stopMessageCreatesForwardedHop() {
        StopTrainingMsg msg = new StopTrainingMsg(9, 12, "global convergence", 4);
        StopTrainingMsg nextHop = msg.nextHop();

        assertEquals(9, nextHop.sourceNodeId);
        assertEquals(12, nextHop.epoch);
        assertEquals("global convergence", nextHop.reason);
        assertEquals(3, nextHop.remainingHops);
        assertEquals(msg.messageId, nextHop.messageId);
    }

    @Test
    void parameterHashIsStable() {
        assertEquals(Integer.hashCode(42), ParameterShardRouter.hashParamIndex(42));
        assertEquals(Integer.hashCode(-7), ParameterShardRouter.hashParamIndex(-7));
        assertFalse(ParameterShardRouter.hashParamIndex(42) == ParameterShardRouter.hashParamIndex(43));
    }
}
