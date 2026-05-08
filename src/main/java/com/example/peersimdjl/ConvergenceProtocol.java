package com.example.peersimdjl;

import peersim.config.Configuration;
import peersim.core.Node;
import peersim.cdsim.CDProtocol;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks local and remote convergence metrics, then decides when the global
 * training can stop.
 *
 * Supports two modes:
 *  1. gossipVote = true (default): Uses gossip-based convergence voting
 *  2. gossipVote = false: Runs all epochs and selects the best one at the end
 *
 * FIXES APPLIED:
 *  1. Loss comparée à 3 décimales (truncate3) pour détecter la convergence réelle
 *  2. DEFAULT_EPSILON = 1.0 (accepte tout delta)
 *  3. DEFAULT_STABLE_ROUNDS = 1 (1 round stable suffit)
 *  4. Affichage de la loss à 3 décimales (%.3f)
 */
public class ConvergenceProtocol implements CDProtocol {

    private static final double DEFAULT_RATIO_THRESHOLD  = 0.80d;
    private static final double DEFAULT_EPSILON          = 1.0d;   // FIX: était 1.0e-3
    private static final int    DEFAULT_STABLE_ROUNDS    = 1;      // FIX: était 3
    private static final boolean DEFAULT_GOSSIP_VOTE     = true;   // Mode gossip par défaut

    private final Map<Integer, GossipConvergenceMsg> latestMetricsByNode = new ConcurrentHashMap<>();
    private final Set<String> seenStopMessages = ConcurrentHashMap.newKeySet();

    private transient Node selfNode;
    private transient ChordProtocol chord;

    private double epsilon;
    private double convergedRatioThreshold;
    private int    stableRoundsRequired;
    private boolean gossipVote;  // Mode contrôle : true=convergence vote, false=all epochs

    private volatile int     localEpoch     = -1;
    private volatile double  localDelta     = Double.POSITIVE_INFINITY;
    private volatile boolean localConverged = false;
    private volatile String  localReason    = "initializing";

    private final AtomicInteger stableRounds   = new AtomicInteger(0);
    private final AtomicBoolean stopIssued     = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested  = new AtomicBoolean(false);

    // FIX: stocke la loss tronquée de l'époque précédente pour comparaison
    private double previousTruncatedLoss = Double.MAX_VALUE;

    // ─────────────────────────────────────────────────────────────
    // Constructeurs
    // ─────────────────────────────────────────────────────────────

    public ConvergenceProtocol() {
        this("protocol.4");
    }

    public ConvergenceProtocol(String prefix) {
        this.epsilon = Math.max(1.0e-9d,
                Configuration.getDouble(prefix + ".convergenceEpsilon", DEFAULT_EPSILON));
        this.convergedRatioThreshold = Math.min(1.0d,
                Math.max(0.5d,
                        Configuration.getDouble(prefix + ".requiredConvergedRatio", DEFAULT_RATIO_THRESHOLD)));
        this.stableRoundsRequired = Math.max(1,
                Configuration.getInt(prefix + ".requiredStableRounds", DEFAULT_STABLE_ROUNDS));
        this.gossipVote = Configuration.getBoolean(prefix + ".gossipVote", DEFAULT_GOSSIP_VOTE);
    }

    // ─────────────────────────────────────────────────────────────
    // Cycle principal
    // ─────────────────────────────────────────────────────────────

    public void attach(Node node) {
        if (node == null) return;
        this.selfNode = node;
        Object protocol = node.getProtocol(ChordProtocol.CHORD_PROTOCOL_ID);
        if (protocol instanceof ChordProtocol) {
            this.chord = (ChordProtocol) protocol;
        }
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        attach(node);
        evaluateGlobalConvergence();
    }

    // ─────────────────────────────────────────────────────────────
    // Mise à jour des métriques locales
    // ─────────────────────────────────────────────────────────────

    public void updateLocalMetrics(int nodeId, int epoch, double delta, boolean locallyConverged) {
        this.localEpoch     = epoch;
        this.localDelta     = delta;
        this.localConverged = locallyConverged;
        this.localReason    = locallyConverged
                ? "local delta below epsilon"
                : "local delta above epsilon";

        GossipConvergenceMsg localMsg = new GossipConvergenceMsg(nodeId, epoch, delta, locallyConverged);
        latestMetricsByNode.put(nodeId, localMsg);

        // FIX: affichage à 3 décimales au lieu de 8
        System.out.printf("[CONVERGENCE][N%d][epoch=%d] delta=%.3f local=%s%n",
                nodeId, epoch, delta, locallyConverged);
    }

    // ─────────────────────────────────────────────────────────────
    // Réception des messages distants
    // ─────────────────────────────────────────────────────────────

    public void receiveRemoteConvergence(GossipConvergenceMsg msg) {
        if (msg == null) return;

        latestMetricsByNode.compute(msg.nodeId, (nodeId, current) -> {
            if (current == null || msg.epoch > current.epoch) return msg;
            if (msg.epoch == current.epoch && msg.createdAtMs >= current.createdAtMs) return msg;
            return current;
        });

        // FIX: affichage à 3 décimales
        System.out.printf("[GOSSIP][CONV] received node=%d epoch=%d delta=%.3f local=%s%n",
                msg.nodeId, msg.epoch, msg.delta, msg.locallyConverged);
    }

    public void receiveStopSignal(StopTrainingMsg msg) {
        if (msg == null) return;
        if (!seenStopMessages.add(msg.messageId)) return;

        stopRequested.set(true);
        if (msg.epoch >= 0) {
            localEpoch = Math.max(localEpoch, msg.epoch);
        }
        localReason = msg.reason;

        System.out.printf("[STOP][N%s] received stop from N%d epoch=%d reason=%s%n",
                chord != null ? chord.nodeIdString : "?",
                msg.sourceNodeId, msg.epoch, msg.reason);
    }

    // ─────────────────────────────────────────────────────────────
    // Accesseurs
    // ─────────────────────────────────────────────────────────────

    public GossipConvergenceMsg snapshotLocalMessage() {
        int nodeId = chord != null ? chord.nodeId
                : (selfNode != null ? selfNode.getIndex() : -1);
        if (nodeId < 0 || localEpoch < 0) return null;
        return new GossipConvergenceMsg(nodeId, localEpoch, localDelta, localConverged);
    }

    public boolean shouldStopTraining() {
        return stopIssued.get() || stopRequested.get();
    }

    public boolean isStopIssued()    { return stopIssued.get();    }
    public boolean isStopRequested() { return stopRequested.get(); }
    public int     getLocalEpoch()   { return localEpoch;          }
    public String  getLocalReason()  { return localReason;         }

    public void markStopIssued(String reason) {
        if (stopIssued.compareAndSet(false, true)) {
            stopRequested.set(true);
            localReason = reason;
            System.out.printf("[CONVERGENCE][STOP] global convergence reached -> %s%n", reason);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Clone
    // ─────────────────────────────────────────────────────────────

    @Override
    public Object clone() {
        try {
            ConvergenceProtocol clone = (ConvergenceProtocol) super.clone();
            clone.selfNode             = null;
            clone.chord                = null;
            clone.previousTruncatedLoss = Double.MAX_VALUE; // FIX: reset pour chaque nœud cloné
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Unable to clone ConvergenceProtocol", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // FIX: tronquer à 3 décimales
    // ─────────────────────────────────────────────────────────────

    /**
     * Tronque une valeur à 3 décimales.
     * Ex: 0.123956 → 0.123
     *     0.124100 → 0.124
     */
    private double truncate3(double value) {
        return Math.floor(value * 1000.0) / 1000.0;
    }

    // ─────────────────────────────────────────────────────────────
    // Évaluation de la convergence globale (méthode principale)
    // ─────────────────────────────────────────────────────────────

    private void evaluateGlobalConvergence() {
        if (localEpoch < 0) return;

        // Mode 1: Gossip Vote - Use convergence voting
        if (gossipVote) {
            evaluateGossipVoteMode();
        }
        // Mode 2: No Vote - Continue all epochs until explicit stop
        else {
            evaluateNoVoteMode();
        }
    }

    /**
     * Mode 1: Gossip Vote - Traditional convergence voting mode
     */
    private void evaluateGossipVoteMode() {
        int totalNodes = Math.max(1, ParameterShardRouter.getActiveProtocols().size());

        long convergedCount = latestMetricsByNode.values().stream()
                .filter(msg -> msg.epoch == localEpoch)
                .filter(msg -> msg.locallyConverged)
                .count();

        double averageDelta = latestMetricsByNode.values().stream()
                .filter(msg -> msg.epoch == localEpoch)
                .mapToDouble(msg -> msg.delta)
                .average()
                .orElse(Double.POSITIVE_INFINITY);

        double ratio = (double) convergedCount / (double) totalNodes;

        // ─────────────────────────────────────────────────
        // FIX : comparaison à 3 décimales
        // ─────────────────────────────────────────────────
        double truncatedCurrent  = truncate3(averageDelta);
        double truncatedPrevious = truncate3(previousTruncatedLoss);

        boolean lossStable   = (truncatedCurrent == truncatedPrevious)
                                && previousTruncatedLoss != Double.MAX_VALUE;
        boolean meetsCriteria = ratio >= convergedRatioThreshold && lossStable;

        System.out.printf(
            "[CONVERGENCE][GOSSIP_VOTE][epoch=%d] loss=%.3f prev=%.3f lossStable=%s ratio=%.2f%n",
            localEpoch, averageDelta, previousTruncatedLoss, lossStable, ratio);

        if (meetsCriteria) {
            int stable = stableRounds.incrementAndGet();
            System.out.printf(
                "[CONVERGENCE][GOSSIP_VOTE][epoch=%d] ✅ stable round %d/%d (loss=%.3f ratio=%.2f)%n",
                localEpoch, stable, stableRoundsRequired, averageDelta, ratio);

            if (stable >= stableRoundsRequired) {
                markStopIssued(String.format(java.util.Locale.ROOT,
                        "converged: loss=%.3f stable=%d rounds ratio=%.2f",
                        averageDelta, stableRoundsRequired, ratio));
            }
        } else {
            if (stableRounds.get() != 0) {
                System.out.printf(
                    "[CONVERGENCE][GOSSIP_VOTE][epoch=%d] ❌ reset stable rounds (loss=%.3f prev=%.3f ratio=%.2f)%n",
                    localEpoch, averageDelta, previousTruncatedLoss, ratio);
            }
            stableRounds.set(0);
        }

        // FIX: mise à jour de la loss précédente pour la prochaine époque
        previousTruncatedLoss = averageDelta;
    }

    /**
     * Mode 2: No Vote - Run all epochs, don't stop on convergence
     * This allows training to continue for all configured epochs, 
     * and the best epoch will be selected at the end.
     */
    private void evaluateNoVoteMode() {
        // In no-vote mode, we don't trigger convergence stopping
        // The simulation will continue running all configured epochs
        // Simply log the progress without checking convergence criteria

        double averageDelta = latestMetricsByNode.values().stream()
                .filter(msg -> msg.epoch == localEpoch)
                .mapToDouble(msg -> msg.delta)
                .average()
                .orElse(Double.POSITIVE_INFINITY);

        System.out.printf(
            "[CONVERGENCE][NO_VOTE][epoch=%d] loss=%.3f (continuing all epochs, best epoch will be selected at end)%n",
            localEpoch, averageDelta);

        // Note: stopRequested will NOT be set here - training continues until all epochs complete
    }
}