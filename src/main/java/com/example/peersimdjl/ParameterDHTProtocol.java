package com.example.peersimdjl;

import peersim.cdsim.CDProtocol;
import peersim.config.Configuration;
import peersim.core.Node;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fully decentralized parameter shard manager.
 *
 * Each parameter index is mapped to one Chord owner node. Only the owner
 * aggregates the updates for that parameter shard and persists the current
 * parameter value in its local Chord storage.
 */
public class ParameterDHTProtocol implements CDProtocol {

    private static final float DEFAULT_INITIAL_VALUE_SCALE = 0.01f;
    private static final String PARAMETER_KEY_PREFIX = "decentralized/model/current/param/";

    private final Map<Integer, ShardState> shards = new ConcurrentHashMap<>();

    private transient ChordProtocol chord;

    private int selfPid;
    private int chordPid;
    private int parameterCount;
    private float initialValueScale;

    public ParameterDHTProtocol() {
        this("protocol.1");
    }

    public ParameterDHTProtocol(String prefix) {
        this.selfPid = Configuration.getInt(prefix + ".selfPid", Configuration.getInt(prefix + ".pid", chordPid + 1));
        this.chordPid = Configuration.getInt(prefix + ".chordPid", ChordProtocol.CHORD_PROTOCOL_ID);
        this.parameterCount = Math.max(1, Configuration.getInt(prefix + ".parameterCount", 8));
        this.initialValueScale = (float) Math.max(0.0001d,
                Configuration.getDouble(prefix + ".initialValueScale", DEFAULT_INITIAL_VALUE_SCALE));
    }

    public void attach(Node node) {
        if (node == null) {
            return;
        }
        Object protocol = node.getProtocol(chordPid);
        if (protocol instanceof ChordProtocol) {
            this.chord = (ChordProtocol) protocol;
        }
    }

    @Override
    public void nextCycle(Node node, int protocolID) {
        attach(node);
        aggregatePendingUpdates();
    }

    public void routeGradient(GradientUpdateMsg msg) {
        ChordProtocol ownerChord = resolveOwnerChord(msg.paramIndex);
        if (ownerChord == null) {
            System.out.println("[DHT] drop gradient " + msg + " (no owner)");
            return;
        }

        ParameterDHTProtocol ownerProtocol = dhtProtocolOf(ownerChord);
        if (ownerProtocol == null) {
            System.out.println("[DHT] drop gradient " + msg + " (owner protocol missing)");
            return;
        }

        ownerProtocol.attach(ownerChord.selfNode);
        ownerProtocol.receiveGradient(msg);

        if (ChordProtocol.DEBUGChord) {
            System.out.println("[DHT] route gradient param[" + msg.paramIndex + "] from N"
                    + msg.senderNodeId + " -> " + ownerChord.nodeIdString);
        }
    }

    public void receiveGradient(GradientUpdateMsg msg) {
        ShardState shard = shards.computeIfAbsent(msg.paramIndex,
                index -> new ShardState(index, initialValueFor(index)));

        synchronized (shard) {
            if (msg.epoch < shard.currentEpoch) {
                return;
            }

            if (msg.epoch > shard.currentEpoch) {
                shard.resetForEpoch(msg.epoch);
            }

            if (!shard.seenMessageIds.add(msg.messageId)) {
                return;
            }

            shard.pendingGradientSum += msg.gradient;
            shard.contributionCount++;
            shard.lastLearningRate = msg.learningRate;
            shard.lastSenderNodeId = msg.senderNodeId;
            shard.lastUpdateAtMs = System.currentTimeMillis();

            System.out.printf("[DHT][epoch=%d][param=%d] received gradient from N%d -> delta=%.6f contributions=%d%n",
                    msg.epoch, msg.paramIndex, msg.senderNodeId, msg.gradient, shard.contributionCount);
        }
    }

    public void aggregatePendingUpdates() {
        for (ShardState shard : shards.values()) {
            synchronized (shard) {
                if (shard.contributionCount == 0) {
                    continue;
                }

                double averageGradient = shard.pendingGradientSum / shard.contributionCount;
                float previousValue = shard.currentValue;
                shard.currentValue = (float) (previousValue - shard.lastLearningRate * averageGradient);
                shard.version++;
                persistShard(shard);

                System.out.printf("[DHT][epoch=%d][param=%d] aggregate avgGrad=%.6f old=%.6f new=%.6f version=%d%n",
                        shard.currentEpoch, shard.paramIndex, averageGradient, previousValue, shard.currentValue, shard.version);

                shard.clearPending();
            }
        }
    }

    public float[] pullModel(int count) {
        return pullModel(count, null);
    }

    public float[] pullModel(int count, float[] fallbackModel) {
        int requestedCount = Math.max(1, count);
        float[] model = new float[requestedCount];

        for (int index = 0; index < requestedCount; index++) {
            model[index] = resolveCurrentValue(index, fallbackModel);
        }

        return model;
    }

    public float getCurrentParameter(int paramIndex) {
        ShardState shard = shards.computeIfAbsent(paramIndex,
                index -> new ShardState(index, initialValueFor(index)));
        synchronized (shard) {
            return shard.currentValue;
        }
    }

    public int getParameterCount() {
        return parameterCount;
    }

    @Override
    public Object clone() {
        try {
            ParameterDHTProtocol clone = (ParameterDHTProtocol) super.clone();
            clone.chord = null;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException("Unable to clone ParameterDHTProtocol", e);
        }
    }

    private void persistShard(ShardState shard) {
        if (chord == null) {
            return;
        }

        chord.putLocal(parameterKey(shard.paramIndex), shard.currentValue);
        chord.putLocal(parameterEpochKey(shard.currentEpoch, shard.paramIndex), shard.currentValue);
        chord.putLocal(parameterMetaKey(shard.paramIndex), shard.describe());
    }

    private float resolveCurrentValue(int paramIndex, float[] fallbackModel) {
        ChordProtocol ownerChord = resolveOwnerChord(paramIndex);
        if (ownerChord == null) {
            if (fallbackModel != null && paramIndex < fallbackModel.length) {
                return fallbackModel[paramIndex];
            }
            return initialValueFor(paramIndex);
        }

        ParameterDHTProtocol ownerProtocol = dhtProtocolOf(ownerChord);
        if (ownerProtocol == null) {
            if (fallbackModel != null && paramIndex < fallbackModel.length) {
                return fallbackModel[paramIndex];
            }
            return initialValueFor(paramIndex);
        }

        return ownerProtocol.getCurrentParameter(paramIndex);
    }

    private ChordProtocol resolveOwnerChord(int paramIndex) {
        if (chord != null) {
            return ParameterShardRouter.ownerForParam(chord, paramIndex);
        }

        java.util.List<ChordProtocol> activeProtocols = ParameterShardRouter.getActiveProtocols();
        if (activeProtocols.isEmpty()) {
            return null;
        }

        int ownerIndex = Math.floorMod(ParameterShardRouter.hashParamIndex(paramIndex), activeProtocols.size());
        return activeProtocols.get(ownerIndex);
    }

    private ParameterDHTProtocol dhtProtocolOf(ChordProtocol ownerChord) {
        if (ownerChord == null || ownerChord.selfNode == null) {
            return null;
        }

        Node ownerNode = ownerChord.selfNode;
        Object protocol = ownerNode.getProtocol(selfPid);
        if (protocol instanceof ParameterDHTProtocol) {
            return (ParameterDHTProtocol) protocol;
        }
        return null;
    }

    private float initialValueFor(int paramIndex) {
        return initialValueScale * (paramIndex + 1);
    }

    private static String parameterKey(int paramIndex) {
        return PARAMETER_KEY_PREFIX + paramIndex;
    }

    private static String parameterEpochKey(int epoch, int paramIndex) {
        return PARAMETER_KEY_PREFIX + "epoch/" + epoch + "/" + paramIndex;
    }

    private static String parameterMetaKey(int paramIndex) {
        return PARAMETER_KEY_PREFIX + "meta/" + paramIndex;
    }

    private static final class ShardState {
        private final int paramIndex;
        private final Set<String> seenMessageIds = ConcurrentHashMap.newKeySet();
        private int currentEpoch;
        private int version;
        private float currentValue;
        private double pendingGradientSum;
        private int contributionCount;
        private double lastLearningRate = 0.01d;
        private int lastSenderNodeId = -1;
        private long lastUpdateAtMs;

        private ShardState(int paramIndex, float initialValue) {
            this.paramIndex = paramIndex;
            this.currentValue = initialValue;
            this.currentEpoch = 0;
            this.version = 0;
            this.pendingGradientSum = 0.0d;
            this.contributionCount = 0;
        }

        private void resetForEpoch(int epoch) {
            this.currentEpoch = epoch;
            this.pendingGradientSum = 0.0d;
            this.contributionCount = 0;
            this.seenMessageIds.clear();
        }

        private void clearPending() {
            this.pendingGradientSum = 0.0d;
            this.contributionCount = 0;
            this.seenMessageIds.clear();
        }

        private String describe() {
            return "ShardState{" +
                    "paramIndex=" + paramIndex +
                    ", epoch=" + currentEpoch +
                    ", version=" + version +
                    ", currentValue=" + currentValue +
                    ", lastSenderNodeId=" + lastSenderNodeId +
                    ", lastUpdateAtMs=" + lastUpdateAtMs +
                    '}';
        }
    }
}