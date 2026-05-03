package com.peersim.gossip;

import java.util.List;

/**
 * Représente un nœud participant au gossip.
 */
public interface GossipNode {

    /**
     * Propage une perte courante à ses voisins.
     */
    void gossipLoss(double currentLoss, int version);

    /**
     * Reçoit une perte propagée par un pair distant.
     */
    void receiveLoss(String peerId, double loss, int version);

    /**
     * Retourne la liste des identifiants des voisins.
     */
    List<String> getNeighbors();
}