package com.example.peersimdjl;

import java.util.UUID;

/**
 * Gradient message sent from a learner to the node responsible for a
 * parameter shard in the Chord DHT.
 */
public final class GradientUpdateMsg {

    public final String messageId;
    public final int senderNodeId;
    public final int epoch;
    public final int paramIndex;
    public final double gradient;
    public final int sampleCount;
    public final double learningRate;
    public final int version;
    public final long createdAtMs;

    public GradientUpdateMsg(int senderNodeId,
                             int epoch,
                             int paramIndex,
                             double gradient,
                             int sampleCount,
                             double learningRate,
                             int version) {
        this(UUID.randomUUID().toString(), senderNodeId, epoch, paramIndex, gradient, sampleCount, learningRate, version, System.currentTimeMillis());
    }

    public GradientUpdateMsg(String messageId,
                             int senderNodeId,
                             int epoch,
                             int paramIndex,
                             double gradient,
                             int sampleCount,
                             double learningRate,
                             int version,
                             long createdAtMs) {
        this.messageId = messageId;
        this.senderNodeId = senderNodeId;
        this.epoch = epoch;
        this.paramIndex = paramIndex;
        this.gradient = gradient;
        this.sampleCount = sampleCount;
        this.learningRate = learningRate;
        this.version = version;
        this.createdAtMs = createdAtMs;
    }

    @Override
    public String toString() {
        return "GradientUpdateMsg{" +
                "messageId='" + messageId + '\'' +
                ", senderNodeId=" + senderNodeId +
                ", epoch=" + epoch +
                ", paramIndex=" + paramIndex +
                ", gradient=" + gradient +
                ", sampleCount=" + sampleCount +
                ", learningRate=" + learningRate +
                ", version=" + version +
                '}';
    }
}