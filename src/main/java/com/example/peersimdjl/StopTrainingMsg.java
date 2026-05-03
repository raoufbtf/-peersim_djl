package com.example.peersimdjl;

import java.util.UUID;

/**
 * Broadcast when a node detects global convergence and wants the network to stop training.
 * The message is re-sent gossip-style to tolerate losses and partial failures.
 */
public final class StopTrainingMsg {

    public final String messageId;
    public final int sourceNodeId;
    public final int epoch;
    public final String reason;
    public final int remainingHops;
    public final long createdAtMs;

    public StopTrainingMsg(int sourceNodeId, int epoch, String reason, int remainingHops) {
        this(UUID.randomUUID().toString(), sourceNodeId, epoch, reason, remainingHops, System.currentTimeMillis());
    }

    public StopTrainingMsg(String messageId,
                           int sourceNodeId,
                           int epoch,
                           String reason,
                           int remainingHops,
                           long createdAtMs) {
        this.messageId = messageId;
        this.sourceNodeId = sourceNodeId;
        this.epoch = epoch;
        this.reason = reason;
        this.remainingHops = remainingHops;
        this.createdAtMs = createdAtMs;
    }

    public StopTrainingMsg nextHop() {
        return new StopTrainingMsg(messageId, sourceNodeId, epoch, reason, Math.max(0, remainingHops - 1), createdAtMs);
    }

    @Override
    public String toString() {
        return "StopTrainingMsg{" +
                "messageId='" + messageId + '\'' +
                ", sourceNodeId=" + sourceNodeId +
                ", epoch=" + epoch +
                ", reason='" + reason + '\'' +
                ", remainingHops=" + remainingHops +
                '}';
    }
}