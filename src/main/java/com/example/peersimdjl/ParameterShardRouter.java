package com.example.peersimdjl;

import peersim.core.Network;
import peersim.core.Node;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Routeur déterministe qui répartit les paramètres sur les nœuds actifs.
 */
public final class ParameterShardRouter {

    private ParameterShardRouter() {
    }

    public static ChordProtocol ownerForParam(ChordProtocol caller, int paramIndex) {
        List<ChordProtocol> activeNodes = getActiveProtocols();
        if (activeNodes.isEmpty()) {
            return caller;
        }

        int ownerIndex = Math.floorMod(hashParamIndex(paramIndex), activeNodes.size());
        return activeNodes.get(ownerIndex);
    }

    public static int hashParamIndex(int paramIndex) {
        return Integer.hashCode(paramIndex);
    }

    public static List<ChordProtocol> getActiveProtocols() {
        List<ChordProtocol> activeProtocols = new ArrayList<>();

        for (int i = 0; i < Network.size(); i++) {
            Node node = Network.get(i);
            if (node == null || !node.isUp()) {
                continue;
            }

            ChordProtocol protocol = (ChordProtocol) node.getProtocol(ChordProtocol.CHORD_PROTOCOL_ID);
            if (protocol != null && protocol.selfNode != null) {
                activeProtocols.add(protocol);
            }
        }

        activeProtocols.sort(Comparator.comparingInt(protocol -> protocol.nodeId));
        return activeProtocols;
    }
}