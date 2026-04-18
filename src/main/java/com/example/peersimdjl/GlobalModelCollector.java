package com.example.peersimdjl;

import java.util.Map;

/**
 * Reconstruit le modèle global en lisant les paramètres agrégés depuis le DHT.
 */
public class GlobalModelCollector {

    private final ChordProtocol chord;

    public GlobalModelCollector(ChordProtocol chord) {
        this.chord = chord;
    }

    public float[] collectGlobalModel(int epoch, int numParams, float[] fallbackModel) {
        float[] global = new float[numParams];
        int missingCount = 0;

        for (int paramIndex = 0; paramIndex < numParams; paramIndex++) {
            String key = FederatedDhtKeys.globalKey(epoch, paramIndex);
            Object value = chord.get(key);
            if (value instanceof Number) {
                global[paramIndex] = ((Number) value).floatValue();
                continue;
            }

            if (fallbackModel != null && fallbackModel.length > paramIndex) {
                global[paramIndex] = fallbackModel[paramIndex];
                missingCount++;
                continue;
            }

            System.out.println("[EPOCH " + epoch + "][Node " + chord.nodeIdString + "] global model incomplet: key manquante " + key);
            return null;
        }

        System.out.println("[EPOCH " + epoch + "][Node " + chord.nodeIdString + "] global model collecté: missing="
                + missingCount + "/" + numParams + " values, fallback appliqué");
        return global;
    }

    float[] collectGlobalModelFromMap(int epoch, int numParams, Map<String, Object> values) {
        float[] global = new float[numParams];
        for (int paramIndex = 0; paramIndex < numParams; paramIndex++) {
            String key = FederatedDhtKeys.globalKey(epoch, paramIndex);
            Object value = values.get(key);
            if (!(value instanceof Number)) {
                return null;
            }
            global[paramIndex] = ((Number) value).floatValue();
        }
        return global;
    }
}
