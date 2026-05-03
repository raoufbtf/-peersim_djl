package com.peersim.gossip;

import java.util.*;

/**
 * Suit le vote de convergence entre pairs.
 */
public interface ConvergenceTracker {

    /**
     * Enregistre le vote d'un pair pour la convergence.
     */
    void recordVote(String peerId);

    /**
     * Vérifie si le quorum est atteint selon un seuil.
     */
    boolean hasQuorum(int totalPeers, double threshold);

    /**
     * Réinitialise l'état de suivi des votes.
     */
    void reset();
}