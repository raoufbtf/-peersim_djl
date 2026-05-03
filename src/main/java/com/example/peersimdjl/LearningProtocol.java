package com.example.peersimdjl;

import peersim.config.Configuration;
import peersim.core.Node;
import peersim.cdsim.CDProtocol;

import java.util.Arrays;

/**
 * Local learner protocol.
 *
 * Each PeerSim node trains on its own batch, computes gradients, and sends
 * them to the parameter shard owner through the DHT layer.
 */
public class LearningProtocol implements CDProtocol {

    private static final float DEFAULT_EPSILON = 1.0e-3f;
    private static final int DEFAULT_PARAMETER_COUNT = 8;
    private static final int DEFAULT_BATCH_SIZE = 16;
    private static final float DEFAULT_LEARNING_RATE = 0.05f;

    private transient Node selfNode;
    private transient ChordProtocol chord;
    private transient ParameterDHTProtocol parameterDhtProtocol;
    private transient ConvergenceProtocol convergenceProtocol;
    private transient GossipProtocol gossipProtocol;

    private int dhtPid;
    private int gossipPid;
    private int convergencePid;
    private int parameterCount;
    private int batchSize;
    private float learningRate;
    private float epsilon;

    private float[] localWeights;
    private double[] localTargetVector;
    private int epoch;
    private boolean initialized;

    public LearningProtocol() {
        this("protocol.1");
    }

    public LearningProtocol(String prefix) {
        this.dhtPid = Configuration.getInt(prefix + ".dhtPid", 2);
        this.gossipPid = Configuration.getInt(prefix + ".gossipPid", 3);
        this.convergencePid = Configuration.getInt(prefix + ".convergencePid", 4);
        this.parameterCount = Math.max(1, Configuration.getInt(prefix + ".parameterCount", DEFAULT_PARAMETER_COUNT));
        this.batchSize = Math.max(1, Configuration.getInt(prefix + ".batchSize", DEFAULT_BATCH_SIZE));
        this.learningRate = (float) Math.max(0.0001d, Configuration.getDouble(prefix + ".learningRate", DEFAULT_LEARNING_RATE));
        this.epsilon = (float) Math.max(1.0e-9d, Configuration.getDouble(prefix + ".epsilon", DEFAULT_EPSILON));
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

        Object dht = node.getProtocol(dhtPid);
        if (dht instanceof ParameterDHTProtocol) {
            this.parameterDhtProtocol = (ParameterDHTProtocol) dht;
        }

        Object convergence = node.getProtocol(convergencePid);
        if (convergence instanceof ConvergenceProtocol) {
            this.convergenceProtocol = (ConvergenceProtocol) convergence;
        }

        Object gossip = node.getProtocol(gossipPid);
        if (gossip instanceof GossipProtocol) {
            this.gossipProtocol = (GossipProtocol) gossip;
        }

        if (parameterDhtProtocol != null) {
            parameterDhtProtocol.attach(node);
        }
        if (convergenceProtocol != null) {
            convergenceProtocol.attach(node);
        }
        if (gossipProtocol != null) {
            gossipProtocol.attach(node);
        }
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        attach(node);

        if (convergenceProtocol != null && convergenceProtocol.isStopRequested()) {
            return;
        }

        if (!initialized) {
            initializeModel();
        }

        if (parameterDhtProtocol == null || convergenceProtocol == null || chord == null) {
            return;
        }

        float[] previousWeights = Arrays.copyOf(localWeights, localWeights.length);
        float[] syncedModel = parameterDhtProtocol.pullModel(parameterCount, previousWeights);
        if (syncedModel != null && syncedModel.length == parameterCount) {
            localWeights = Arrays.copyOf(syncedModel, syncedModel.length);
            previousWeights = Arrays.copyOf(localWeights, localWeights.length);
        }

        double[] gradients = computeGradient(previousWeights);
        float[] updatedWeights = applyGradient(previousWeights, gradients);
        double delta = computeL2Norm(previousWeights, updatedWeights);
        boolean locallyConverged = delta < epsilon;
        double localLoss = computeLoss(updatedWeights);

        System.out.printf("[LEARN][N%s][epoch=%d] loss=%.3f delta=%.8f local=%s%n",
                chord.nodeIdString, epoch, localLoss, delta, locallyConverged);

        for (int paramIndex = 0; paramIndex < gradients.length; paramIndex++) {
            GradientUpdateMsg msg = new GradientUpdateMsg(
                    chord.nodeId,
                    epoch,
                    paramIndex,
                    gradients[paramIndex],
                    batchSize,
                    learningRate,
                    epoch);
            parameterDhtProtocol.routeGradient(msg);
        }

        convergenceProtocol.updateLocalMetrics(chord.nodeId, epoch, delta, locallyConverged);
        localWeights = updatedWeights;
        epoch++;
    }

    public float[] getLocalWeights() {
        return localWeights == null ? null : Arrays.copyOf(localWeights, localWeights.length);
    }

    @Override
    public Object clone() {
        try {
            LearningProtocol clone = (LearningProtocol) super.clone();
            clone.selfNode = null;
            clone.chord = null;
            clone.parameterDhtProtocol = null;
            clone.convergenceProtocol = null;
            clone.gossipProtocol = null;
            clone.localWeights = null;
            clone.localTargetVector = null;
            clone.epoch = 0;
            clone.initialized = false;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Unable to clone LearningProtocol", e);
        }
    }

    private void initializeModel() {
        float[] initialModel = parameterDhtProtocol != null
                ? parameterDhtProtocol.pullModel(parameterCount)
                : null;
        if (initialModel == null || initialModel.length != parameterCount) {
            initialModel = new float[parameterCount];
        }

        this.localWeights = Arrays.copyOf(initialModel, initialModel.length);
        this.localTargetVector = buildLocalTargetVector(parameterCount, chord != null ? chord.nodeId : selfNode.getIndex());
        this.initialized = true;

        System.out.printf("[LEARN][N%s] initialized with %d parameters and batchSize=%d%n",
                chord != null ? chord.nodeIdString : "?", parameterCount, batchSize);
    }

    private double[] computeGradient(float[] weights) {
        double[] gradient = new double[weights.length];
        double scale = 1.0d / Math.max(1, batchSize);

        for (int index = 0; index < weights.length; index++) {
            double target = localTargetVector[index];
            gradient[index] = (weights[index] - target) * scale;
        }

        return gradient;
    }

    private float[] applyGradient(float[] weights, double[] gradient) {
        float[] updated = new float[weights.length];
        for (int index = 0; index < weights.length; index++) {
            updated[index] = (float) (weights[index] - learningRate * gradient[index]);
        }
        return updated;
    }

    private double computeLoss(float[] weights) {
        double loss = 0.0d;
        for (int index = 0; index < weights.length; index++) {
            double diff = weights[index] - localTargetVector[index];
            loss += diff * diff;
        }
        return 0.5d * loss;
    }

    private double computeL2Norm(float[] before, float[] after) {
        double sum = 0.0d;
        for (int index = 0; index < before.length; index++) {
            double diff = after[index] - before[index];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    private double[] buildLocalTargetVector(int size, int nodeSeed) {
        double[] target = new double[size];
        double base = 0.05d * (nodeSeed + 1);
        for (int index = 0; index < size; index++) {
            double modulation = Math.sin((nodeSeed + 1) * (index + 1) * 0.5d) * 0.01d;
            target[index] = base + (0.01d * index) + modulation;
        }
        return target;
    }
}