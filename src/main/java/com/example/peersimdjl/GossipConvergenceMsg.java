package com.example.peersimdjl;

import java.util.UUID;

/**
 * Lightweight gossip message used to disseminate local convergence metrics.
 */
public final class GossipConvergenceMsg {

    public final int nodeId;
    public final int epoch;
    public final double delta;
    public final boolean locallyConverged;
    public final String messageId;
    public final long createdAtMs;

    public GossipConvergenceMsg(int nodeId, int epoch, double delta, boolean locallyConverged) {
        this(UUID.randomUUID().toString(), nodeId, epoch, delta, locallyConverged, System.currentTimeMillis());
    }

    public GossipConvergenceMsg(String messageId,
                                int nodeId,
                                int epoch,
                                double delta,
                                boolean locallyConverged,
                                long createdAtMs) {
        this.nodeId = nodeId;
        this.epoch = epoch;
        this.delta = delta;
        this.locallyConverged = locallyConverged;
        this.messageId = messageId;
        this.createdAtMs = createdAtMs;
    }

    @Override
    public String toString() {
        return "GossipConvergenceMsg{" +
                "nodeId=" + nodeId +
                ", epoch=" + epoch +
                ", delta=" + delta +
                ", locallyConverged=" + locallyConverged +
                '}';
    }
}