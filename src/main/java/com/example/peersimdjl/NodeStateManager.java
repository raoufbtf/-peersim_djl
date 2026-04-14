package com.example.peersimdjl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestionnaire global des états de nœuds.
 * Singleton initialisé au démarrage de la simulation.
 */
public final class NodeStateManager {

    private static final NodeStateManager INSTANCE = new NodeStateManager();

    private final Map<Integer, NodeState> nodeStates = new LinkedHashMap<>();
    private boolean initialized = false;

    private NodeStateManager() {
    }

    public static NodeStateManager getInstance() {
        return INSTANCE;
    }

    public synchronized void init(int networkSize) {
        nodeStates.clear();
        for (int i = 0; i < networkSize; i++) {
            nodeStates.put(i, new NodeState());
        }
        initialized = true;
        System.out.println("[NODE-STATE] Initialised with " + networkSize + " nodes");
    }

    public synchronized List<Integer> getAvailableLearners() {
        List<Integer> available = new ArrayList<>();
        if (!initialized) {
            return available;
        }
        for (Map.Entry<Integer, NodeState> entry : nodeStates.entrySet()) {
            NodeState state = entry.getValue();
            if (!state.isBusyAsLearner() && !state.isBusyAsIDE()) {
                available.add(entry.getKey());
            }
        }
        return available;
    }

    public synchronized List<Integer> getAvailableIDEs() {
        List<Integer> available = new ArrayList<>();
        if (!initialized) {
            return available;
        }
        for (Map.Entry<Integer, NodeState> entry : nodeStates.entrySet()) {
            NodeState state = entry.getValue();
            if (!state.isBusyAsIDE() && !state.isBusyAsLearner()) {
                available.add(entry.getKey());
            }
        }
        return available;
    }

    public synchronized void markAsLearner(int nodeId) {
        NodeState state = nodeStates.get(nodeId);
        if (state != null) {
            state.markAsLearner();
        }
    }

    public synchronized void markAsIDE(int nodeId) {
        NodeState state = nodeStates.get(nodeId);
        if (state != null) {
            state.markAsIDE();
        }
    }

    public synchronized void releaseLearner(int nodeId) {
        NodeState state = nodeStates.get(nodeId);
        if (state != null) {
            state.releaseLearner();
        }
    }

    public synchronized void releaseIDE(int nodeId) {
        NodeState state = nodeStates.get(nodeId);
        if (state != null) {
            state.releaseIDE();
        }
    }

    public synchronized boolean isInitialized() {
        return initialized;
    }
}
