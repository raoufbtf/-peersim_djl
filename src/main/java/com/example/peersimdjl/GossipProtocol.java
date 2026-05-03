package com.example.peersimdjl;

import peersim.config.Configuration;
import peersim.core.Network;
import peersim.core.Node;
import peersim.cdsim.CDProtocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gossip dissemination layer used exclusively for convergence metrics and stop propagation.
 */
public class GossipProtocol implements CDProtocol {

    private static final double DEFAULT_DROP_PROBABILITY = 0.05d;
    private static final int DEFAULT_RETRY_ROUNDS = 5;
    private static final int DEFAULT_FANOUT = 2;

    private transient Node selfNode;
    private transient ChordProtocol chord;
    private transient ConvergenceProtocol convergenceProtocol;

    private final Map<String, PendingStopBroadcast> stopBroadcasts = new ConcurrentHashMap<>();

    private int selfPid;
    private int convergencePid;
    private int fanout;
    private int retryRounds;
    private double dropProbability;

    public GossipProtocol() {
        this("protocol.3");
    }

    public GossipProtocol(String prefix) {
        this.selfPid = Configuration.getInt(prefix + ".selfPid", Configuration.getInt(prefix + ".pid", 3));
        this.convergencePid = Configuration.getInt(prefix + ".convergencePid", 4);
        this.fanout = Math.max(1, Configuration.getInt(prefix + ".fanout", DEFAULT_FANOUT));
        this.retryRounds = Math.max(1, Configuration.getInt(prefix + ".retryRounds", DEFAULT_RETRY_ROUNDS));
        this.dropProbability = Math.max(0.0d, Math.min(0.95d,
                Configuration.getDouble(prefix + ".dropProbability", DEFAULT_DROP_PROBABILITY)));
    }

    public void attach(Node node) {
        if (node == null) {
            return;
        }

        this.selfNode = node;
        Object chordProtocol = node.getProtocol(ChordProtocol.CHORD_PROTOCOL_ID);
        if (chordProtocol instanceof ChordProtocol) {
            this.chord = (ChordProtocol) chordProtocol;
        }

        Object convergence = node.getProtocol(convergencePid);
        if (convergence instanceof ConvergenceProtocol) {
            this.convergenceProtocol = (ConvergenceProtocol) convergence;
        }
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        attach(node);
        sendLocalConvergenceDigest();
        processStopBroadcasts();
    }

    public void receiveConvergence(GossipConvergenceMsg msg) {
        if (msg == null || convergenceProtocol == null) {
            return;
        }
        convergenceProtocol.receiveRemoteConvergence(msg);
    }

    public void receiveStop(StopTrainingMsg msg) {
        if (msg == null || convergenceProtocol == null) {
            return;
        }

        convergenceProtocol.receiveStopSignal(msg);
        if (msg.remainingHops > 0) {
            stopBroadcasts.putIfAbsent(msg.messageId, new PendingStopBroadcast(msg.nextHop(), retryRounds));
        }
    }

    public void queueLocalStopBroadcast(StopTrainingMsg msg) {
        if (msg == null) {
            return;
        }
        stopBroadcasts.putIfAbsent(msg.messageId, new PendingStopBroadcast(msg, retryRounds));
    }

    @Override
    public Object clone() {
        try {
            GossipProtocol clone = (GossipProtocol) super.clone();
            clone.selfNode = null;
            clone.chord = null;
            clone.convergenceProtocol = null;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Unable to clone GossipProtocol", e);
        }
    }

    private void sendLocalConvergenceDigest() {
        if (convergenceProtocol == null || convergenceProtocol.isStopRequested()) {
            return;
        }

        GossipConvergenceMsg localMessage = convergenceProtocol.snapshotLocalMessage();
        if (localMessage == null) {
            return;
        }

        broadcastConvergence(localMessage);
    }

    private void processStopBroadcasts() {
        if (convergenceProtocol != null && convergenceProtocol.isStopIssued()) {
            StopTrainingMsg stopMsg = new StopTrainingMsg(
                    chord != null ? chord.nodeId : selfNode.getIndex(),
                    convergenceProtocol.getLocalEpoch(),
                    convergenceProtocol.getLocalReason(),
                    retryRounds);
            queueLocalStopBroadcast(stopMsg);
        }

        if (stopBroadcasts.isEmpty()) {
            return;
        }

        List<String> finished = new ArrayList<>();
        for (PendingStopBroadcast pending : stopBroadcasts.values()) {
            if (pending.retriesLeft <= 0) {
                finished.add(pending.message.messageId);
                continue;
            }

            broadcastStop(pending.message);
            pending.retriesLeft--;
        }

        for (String messageId : finished) {
            stopBroadcasts.remove(messageId);
        }
    }

    private void broadcastConvergence(GossipConvergenceMsg msg) {
        for (Node target : chooseRandomPeers(fanout)) {
            GossipProtocol targetProtocol = gossipProtocolOf(target);
            if (targetProtocol == null) {
                continue;
            }

            if (shouldDrop()) {
                System.out.printf("[GOSSIP][DROP] convergence msg %s -> N%d%n", msg.messageId, target.getIndex());
                continue;
            }

            targetProtocol.receiveConvergence(msg);
            System.out.printf("[GOSSIP][SEND] convergence N%s -> N%s epoch=%d delta=%.8f%n",
                    nodeLabel(), targetLabel(target), msg.epoch, msg.delta);
        }
    }

    private void broadcastStop(StopTrainingMsg msg) {
        for (Node target : chooseRandomPeers(fanout)) {
            GossipProtocol targetProtocol = gossipProtocolOf(target);
            if (targetProtocol == null) {
                continue;
            }

            if (shouldDrop()) {
                System.out.printf("[GOSSIP][DROP] stop msg %s -> N%d%n", msg.messageId, target.getIndex());
                continue;
            }

            targetProtocol.receiveStop(msg.nextHop());
            System.out.printf("[GOSSIP][SEND] stop N%s -> N%s epoch=%d reason=%s hops=%d%n",
                    nodeLabel(), targetLabel(target), msg.epoch, msg.reason, msg.remainingHops);
        }
    }

    private List<Node> chooseRandomPeers(int maxCount) {
        if (selfNode == null || Network.size() <= 1) {
            return Collections.emptyList();
        }

        List<Node> candidates = new ArrayList<>();
        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            if (node == null || !node.isUp() || node == selfNode) {
                continue;
            }
            if (gossipProtocolOf(node) != null) {
                candidates.add(node);
            }
        }

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        Collections.shuffle(candidates, ThreadLocalRandom.current());
        return candidates.subList(0, Math.min(maxCount, candidates.size()));
    }

    private GossipProtocol gossipProtocolOf(Node node) {
        if (node == null) {
            return null;
        }

        Object protocol = node.getProtocol(selfPid);
        if (protocol instanceof GossipProtocol) {
            return (GossipProtocol) protocol;
        }

        return null;
    }

    private boolean shouldDrop() {
        return ThreadLocalRandom.current().nextDouble() < dropProbability;
    }

    private String nodeLabel() {
        if (chord != null) {
            return chord.nodeIdString;
        }
        return selfNode == null ? "?" : String.valueOf(selfNode.getIndex());
    }

    private String targetLabel(Node target) {
        if (target == null) {
            return "?";
        }
        Object protocol = target.getProtocol(ChordProtocol.CHORD_PROTOCOL_ID);
        if (protocol instanceof ChordProtocol) {
            ChordProtocol targetChord = (ChordProtocol) protocol;
            return targetChord.nodeIdString;
        }
        return String.valueOf(target.getIndex());
    }

    private static final class PendingStopBroadcast {
        private final StopTrainingMsg message;
        private int retriesLeft;

        private PendingStopBroadcast(StopTrainingMsg message, int retriesLeft) {
            this.message = message;
            this.retriesLeft = retriesLeft;
        }
    }
}