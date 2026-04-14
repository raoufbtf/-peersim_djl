package com.example.peersimdjl;

/**
 * Demande d'apprentissage déposée dans la file d'attente.
 */
public class SessionRequest {

    public final int sessionId;
    public final int requiredLearners;
    public final String csvDataset;

    public SessionRequest(int sessionId, int requiredLearners, String csvDataset) {
        this.sessionId = sessionId;
        this.requiredLearners = Math.max(1, requiredLearners);
        this.csvDataset = csvDataset;
    }
}
