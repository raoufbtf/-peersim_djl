package com.example.peersimdjl;

import peersim.core.*;
import java.util.*;

public class InitControl implements Control {

    private static final int CHORD_PROTOCOL_ID = 0;

    public InitControl(String prefix) {}

    @Override
    public boolean execute() {

        // ── 1. Collecter et trier les nœuds par chordId ─────────────────────
        List<Node> nodeList = new ArrayList<>();
        for (int i = 0; i < Network.size(); i++) {
            Node node   = Network.get(i);
            ChordProtocol chord = (ChordProtocol) node.getProtocol(CHORD_PROTOCOL_ID);
            chord.nodeId   = i * 2;      // IDs indépendants: 0, 2, 4, 6, 8, 10, 12, 14
            // nodeIdString est assigné une seule fois et jamais réassigné
            if (chord.nodeIdString == null) {
                chord.nodeIdString = ChordProtocol.generateUniqueStringId();
            }
            chord.selfNode = node;
            if (chord.successorList == null)
                chord.successorList = new Node[3];
            nodeList.add(node);
        }

        nodeList.sort(Comparator.comparingInt(
                n -> ((ChordProtocol) n.getProtocol(CHORD_PROTOCOL_ID)).nodeId));

        // ── 2. Calculer m ────────────────────────────────────────────────────
        int size = nodeList.size();
        int maxM = Math.max(3, (int) Math.ceil(Math.log(size) / Math.log(2)));

        // ── 3. Initialiser m et finger pour chaque nœud ─────────────────────
        for (Node node : nodeList) {
            ChordProtocol cp = (ChordProtocol) node.getProtocol(CHORD_PROTOCOL_ID);
            cp.m      = maxM;
            cp.finger = new Node[maxM];
        }

        // ── 4. Construire l'anneau (successor / predecessor) ─────────────────
        for (int i = 0; i < size; i++) {
            Node          cur   = nodeList.get(i);
            ChordProtocol chord = (ChordProtocol) cur.getProtocol(CHORD_PROTOCOL_ID);
            chord.successor   = nodeList.get((i + 1) % size);
            chord.predecessor = nodeList.get((i - 1 + size) % size);
            // Initialiser aussi la successorList
            chord.successorList[0] = chord.successor;
            if (size > 1) chord.successorList[1] = nodeList.get((i + 2) % size);
            if (size > 2) chord.successorList[2] = nodeList.get((i + 3) % size);
        }

        // ── 5. Afficher l'anneau ─────────────────────────────────────────────
        if (ChordProtocol.DEBUGChord) {
            for (Node node : nodeList) {
                ChordProtocol cp       = (ChordProtocol) node.getProtocol(CHORD_PROTOCOL_ID);
                ChordProtocol succChord = (ChordProtocol) cp.successor  .getProtocol(CHORD_PROTOCOL_ID);
                ChordProtocol predChord = (ChordProtocol) cp.predecessor.getProtocol(CHORD_PROTOCOL_ID);
                System.out.println("Node " + cp.nodeIdString + " (PeerSim=" + cp.selfNode.getIndex() + ")"
                        + " -> succ: " + succChord.nodeIdString
                        + " pred: "    + predChord.nodeIdString);
            }
        } else {
            // Affichage simple des nodeIdString
            StringBuilder sb = new StringBuilder("Nodes: [");
            for (int i = 0; i < nodeList.size(); i++) {
                Node node = nodeList.get(i);
                ChordProtocol cp = (ChordProtocol) node.getProtocol(CHORD_PROTOCOL_ID);
                sb.append(cp.nodeIdString);
                if (i < nodeList.size() - 1) sb.append(" ");
            }
            sb.append("]");
            System.out.println(sb.toString());
        }

        // ── 6. Calculer les finger tables ─────────────────────────────────────
        int ringSize = 1 << maxM;
        for (Node node : nodeList) {
            ChordProtocol cp = (ChordProtocol) node.getProtocol(CHORD_PROTOCOL_ID);
            for (int i = 0; i < cp.m; i++) {
                int  start = (cp.nodeId + (1 << i)) % ringSize;
                Node succ  = cp.findSuccessor(start);
                cp.finger[i] = (succ != null) ? succ : cp.successor;
            }
        }

        // ── 7. Afficher les finger tables ─────────────────────────────────────
        if (ChordProtocol.DEBUGChord) {
            for (Node node : nodeList) {
                ChordProtocol cp = (ChordProtocol) node.getProtocol(CHORD_PROTOCOL_ID);
                System.out.println("Node " + cp.nodeIdString + " (PeerSim=" + cp.selfNode.getIndex() + ") fingers:");
                for (int i = 0; i < cp.m; i++) {
                    Node fn    = cp.finger[i];
                    String fnStr = (fn == null) ? "null"
                            : ((ChordProtocol) fn.getProtocol(CHORD_PROTOCOL_ID)).nodeIdString;
                    int  start = (cp.nodeId + (1 << i)) % ringSize;
                    System.out.println("  finger[" + i + "] (" + start + ") -> " + fnStr);
                }
            }
        }

        // ── 8. Test de lookup ─────────────────────────────────────────────────
        if (ChordProtocol.DEBUGChord) {
            Random rand      = new Random();
            int    lookupId  = rand.nextInt(20);
            ChordProtocol sc = (ChordProtocol) nodeList.get(0).getProtocol(CHORD_PROTOCOL_ID);
            Node   found     = sc.findSuccessor(lookupId);
            if (found != null) {
                ChordProtocol fc = (ChordProtocol) found.getProtocol(CHORD_PROTOCOL_ID);
                System.out.println("Lookup for ID " + lookupId + " -> Node " + fc.nodeIdString + " (PeerSim=" + fc.selfNode.getIndex() + ")");
            } else {
                System.out.println("Lookup for ID " + lookupId + " -> no successor found");
            }
        }

        return false; // continuer la simulation
    }
}