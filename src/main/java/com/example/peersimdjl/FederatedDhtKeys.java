package com.example.peersimdjl;

/**
 * Utilitaire de génération des clés DHT pour l'apprentissage fédéré.
 */
public final class FederatedDhtKeys {

    private FederatedDhtKeys() {
    }

    public static String gradientKey(int epoch, int paramIndex) {
        return "grad/epoch/" + epoch + "/param/" + paramIndex;
    }

    public static String globalKey(int epoch, int paramIndex) {
        return "global/epoch/" + epoch + "/param/" + paramIndex;
    }

    public static String voteKey(int epoch, String nodeId) {
        return "vote/epoch/" + epoch + "/node/" + nodeId;
    }

    public static String decisionKey(int epoch) {
        return "decision/epoch/" + epoch;
    }
}
