package com.example.peersimdjl;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Contribution de gradient pour un paramètre donné et un epoch donné.
 */
public class ParamEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String nodeId;
    private final int paramIndex;
    private final int epoch;
    private final float gradientDelta;
    private final int datasetSize;
    private final long timestamp;
    private final String checksum;

    public ParamEntry(String nodeId, int paramIndex, int epoch, float gradientDelta, int datasetSize, long timestamp) {
        this.nodeId = nodeId;
        this.paramIndex = paramIndex;
        this.epoch = epoch;
        this.gradientDelta = gradientDelta;
        this.datasetSize = datasetSize;
        this.timestamp = timestamp;
        this.checksum = computeChecksum(nodeId, paramIndex, epoch, gradientDelta);
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getParamIndex() {
        return paramIndex;
    }

    public int getEpoch() {
        return epoch;
    }

    public float getGradientDelta() {
        return gradientDelta;
    }

    public int getDatasetSize() {
        return datasetSize;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getChecksum() {
        return checksum;
    }

    private static String computeChecksum(String nodeId, int paramIndex, int epoch, float gradientDelta) {
        String base = Float.toString(gradientDelta) + "|" + nodeId + "|" + epoch + "|" + paramIndex;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(base.hashCode());
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        if (ChordProtocol.DEBUGChord) {
            System.out.println("[SERIALIZATION] ParamEntry sérialisé: node=" + nodeId
                    + ", epoch=" + epoch + ", param=" + paramIndex + ", checksum=" + checksum);
        }
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (ChordProtocol.DEBUGChord) {
            System.out.println("[DESERIALIZATION] ParamEntry désérialisé: node=" + nodeId
                    + ", epoch=" + epoch + ", param=" + paramIndex + ", checksum=" + checksum);
        }
    }

    @Override
    public String toString() {
        return "ParamEntry{" +
                "nodeId='" + nodeId + '\'' +
                ", paramIndex=" + paramIndex +
                ", epoch=" + epoch +
                ", gradientDelta=" + gradientDelta +
                ", datasetSize=" + datasetSize +
                ", timestamp=" + timestamp +
                ", checksum='" + checksum + '\'' +
                '}';
    }
}
