package com.peersim.gossip;

import java.util.*;

/**
 * Représente un vecteur de pertes partagé par gossip.
 */
public interface LossVector {

    /**
     * Met à jour la perte associée à un pair et sa version.
     */
    void update(String peerId, double loss, int version);

    /**
     * Indique si le vecteur est convergé selon epsilon.
     */
    boolean isConverged(double epsilon);

    /**
     * Retourne l'écart maximal entre les valeurs min et max.
     */
    double getMaxMinDelta();
}