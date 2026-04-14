package com.example.peersimdjl;

/**
 * Représente l'état d'un nœud PeerSim pour deux rôles indépendants:
 * learner et IDE.
 */
public class NodeState {

    private boolean busyAsLearner;
    private boolean busyAsIDE;

    public NodeState() {
        this.busyAsLearner = false;
        this.busyAsIDE = false;
    }

    public synchronized boolean isBusyAsLearner() {
        return busyAsLearner;
    }

    public synchronized boolean isBusyAsIDE() {
        return busyAsIDE;
    }

    public synchronized void markAsLearner() {
        this.busyAsLearner = true;
    }

    public synchronized void markAsIDE() {
        this.busyAsIDE = true;
    }

    public synchronized void releaseLearner() {
        this.busyAsLearner = false;
    }

    public synchronized void releaseIDE() {
        this.busyAsIDE = false;
    }
}
