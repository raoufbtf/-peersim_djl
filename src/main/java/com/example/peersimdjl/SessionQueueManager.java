package com.example.peersimdjl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import peersim.core.Network;
import peersim.core.Node;

/**
 * File d'attente centrale des demandes d'apprentissage.
 * Gère les ressources disponibles et les sessions actives.
 */
public final class SessionQueueManager {

    private static final SessionQueueManager INSTANCE = new SessionQueueManager();

    private final Deque<SessionRequest> waitingQueue = new ArrayDeque<>();
    private final Map<Integer, ActiveSession> activeSessions = new LinkedHashMap<>();

    private SessionQueueManager() {
    }

    public static SessionQueueManager getInstance() {
        return INSTANCE;
    }

    public synchronized ActiveSession tryStartSession(SessionRequest req) {
        if (req == null) {
            return null;
        }

        if (activeSessions.containsKey(req.sessionId)) {
            return activeSessions.get(req.sessionId);
        }

        ActiveSession started = tryStartSessionInternal(req, true);
        if (started != null) {
            return started;
        }

        if (!containsRequest(req.sessionId)) {
            waitingQueue.addLast(req);
        }
        logWaiting(req);
        return null;
    }

    public synchronized void onSessionComplete(int sessionId) {
        ActiveSession finished = activeSessions.remove(sessionId);
        if (finished == null) {
            return;
        }

        NodeStateManager nodeStateManager = NodeStateManager.getInstance();
        for (int learnerId : finished.getLearnerNodeIds()) {
            nodeStateManager.releaseLearner(learnerId);
        }
        nodeStateManager.releaseIDE(finished.getIdeNodeId());

        System.out.println("[SESSION " + sessionId + "] Done    | Releasing nodes → checking queue");
        drainWaitingQueue();
    }

    public synchronized Map<Integer, ActiveSession> getActiveSessions() {
        return new LinkedHashMap<>(activeSessions);
    }

    public synchronized List<SessionRequest> getWaitingQueueSnapshot() {
        return new ArrayList<>(waitingQueue);
    }

    private ActiveSession tryStartSessionInternal(SessionRequest req, boolean allowQueueOnFailure) {
        NodeStateManager nodeStateManager = NodeStateManager.getInstance();
        List<Integer> availableLearners = nodeStateManager.getAvailableLearners();
        List<Integer> availableIDEs = nodeStateManager.getAvailableIDEs();
        int requiredAdditionalLearners = Math.max(0, req.requiredLearners - 1);

        if (availableLearners.size() < req.requiredLearners || availableIDEs.isEmpty()) {
            if (allowQueueOnFailure && !containsRequest(req.sessionId)) {
                waitingQueue.addLast(req);
            }
            return null;
        }

        int ideNodeId = chooseIdeNode(availableIDEs, availableLearners);
        if (ideNodeId < 0) {
            if (allowQueueOnFailure && !containsRequest(req.sessionId)) {
                waitingQueue.addLast(req);
            }
            return null;
        }

        List<Integer> learners = chooseLearners(availableLearners, ideNodeId, requiredAdditionalLearners);
        if (learners.size() < requiredAdditionalLearners) {
            if (allowQueueOnFailure && !containsRequest(req.sessionId)) {
                waitingQueue.addLast(req);
            }
            return null;
        }

        ChordProtocol ideProtocol = chordProtocolForNodeIndex(ideNodeId);
        if (ideProtocol == null) {
            return null;
        }

        List<Integer> learnerChordIds = new ArrayList<>();
        for (int learnerId : learners) {
            ChordProtocol learnerProtocol = chordProtocolForNodeIndex(learnerId);
            if (learnerProtocol != null) {
                learnerChordIds.add(learnerProtocol.nodeId);
            }
        }

        if (learnerChordIds.size() < requiredAdditionalLearners) {
            return null;
        }

        nodeStateManager.markAsIDE(ideNodeId);
        for (int learnerId : learners) {
            nodeStateManager.markAsLearner(learnerId);
        }

        String ideNodeIdString = ideProtocol.nodeIdString;
        LearningSession learningSession = new LearningSession(
                "session_" + req.sessionId,
                ideProtocol.nodeId,
                ideNodeIdString,
                req.requiredLearners,
                System.currentTimeMillis(),
                (int) peersim.core.CommonState.getTime());
        learningSession.addActiveNode(ideProtocol.nodeId, ideNodeIdString);
        for (int i = 0; i < learners.size(); i++) {
            int learnerIndex = learners.get(i);
            ChordProtocol learnerProtocol = chordProtocolForNodeIndex(learnerIndex);
            if (learnerProtocol != null) {
                learningSession.addActiveNode(learnerProtocol.nodeId, learnerProtocol.nodeIdString);
            }
        }
        learningSession.transitionToRunning();

        ActiveSession activeSession = new ActiveSession(req, ideNodeId, ideProtocol.nodeId, ideNodeIdString, learners, learnerChordIds, learningSession);
        activeSessions.put(req.sessionId, activeSession);

        System.out.println("[SESSION " + req.sessionId + "] Started | IDE+Learner: " + ideNodeIdString
                + " | Other learners: " + joinNodes(learners)
                + " | Learners total: " + req.requiredLearners);
        return activeSession;
    }

    private void drainWaitingQueue() {
        int initialSize = waitingQueue.size();
        for (int i = 0; i < initialSize; i++) {
            SessionRequest next = waitingQueue.pollFirst();
            if (next == null) {
                continue;
            }

            ActiveSession started = tryStartSessionInternal(next, false);
            if (started == null) {
                waitingQueue.addLast(next);
            } else {
                System.out.println("[QUEUE]     Session " + next.sessionId + " dequeued and started");
            }
        }
    }

    private boolean containsRequest(int sessionId) {
        for (SessionRequest request : waitingQueue) {
            if (request.sessionId == sessionId) {
                return true;
            }
        }
        return false;
    }

    private int chooseIdeNode(List<Integer> availableIDEs, List<Integer> availableLearners) {
        for (Integer nodeId : availableIDEs) {
            if (nodeId != null && !availableLearners.contains(nodeId)) {
                return nodeId;
            }
        }
        return availableIDEs.isEmpty() ? -1 : availableIDEs.get(0);
    }

    private List<Integer> chooseLearners(List<Integer> availableLearners, int ideNodeId, int requiredLearners) {
        List<Integer> learners = new ArrayList<>();
        for (Integer nodeId : availableLearners) {
            if (nodeId == null || nodeId == ideNodeId) {
                continue;
            }
            learners.add(nodeId);
            if (learners.size() >= requiredLearners) {
                break;
            }
        }
        return learners;
    }

    private String joinNodes(List<Integer> nodes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            ChordProtocol protocol = chordProtocolForNodeIndex(nodes.get(i));
            builder.append(protocol != null ? protocol.nodeIdString : ("n" + nodes.get(i)));
        }
        return builder.toString();
    }

    private ChordProtocol chordProtocolForNodeIndex(int nodeIndex) {
        if (nodeIndex < 0 || nodeIndex >= Network.size()) {
            return null;
        }

        Node node = Network.get(nodeIndex);
        if (node == null || !node.isUp()) {
            return null;
        }

        return (ChordProtocol) node.getProtocol(ChordProtocol.CHORD_PROTOCOL_ID);
    }

    private void logWaiting(SessionRequest req) {
        NodeStateManager nodeStateManager = NodeStateManager.getInstance();
        int availableLearners = nodeStateManager.getAvailableLearners().size();
        int availableIDEs = nodeStateManager.getAvailableIDEs().size();
        System.out.println("[SESSION " + req.sessionId + "] Waiting — not enough free nodes (need "
                + req.requiredLearners + " learners, have " + availableLearners + ")");
        if (availableIDEs <= 0) {
            System.out.println("[SESSION " + req.sessionId + "] Waiting — no free IDE available");
        }
    }
}
