package com.example.peersimdjl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Représente une session effectivement démarrée avec ses ressources allouées.
 */
public class ActiveSession {

    private final SessionRequest request;
    private final int ideNodeId;
    private final int ideChordId;
    private final String ideNodeIdString;
    private final List<Integer> learnerNodeIds;
    private final List<Integer> learnerChordIds;
    private final LearningSession learningSession;
    private boolean completed;

    public ActiveSession(SessionRequest request,
                         int ideNodeId,
                         int ideChordId,
                         String ideNodeIdString,
                         List<Integer> learnerNodeIds,
                         List<Integer> learnerChordIds,
                         LearningSession learningSession) {
        this.request = request;
        this.ideNodeId = ideNodeId;
        this.ideChordId = ideChordId;
        this.ideNodeIdString = ideNodeIdString;
        this.learnerNodeIds = new ArrayList<>(learnerNodeIds);
        this.learnerChordIds = new ArrayList<>(learnerChordIds);
        this.learningSession = learningSession;
        this.completed = false;
    }

    public SessionRequest getRequest() {
        return request;
    }

    public int getSessionId() {
        return request.sessionId;
    }

    public int getIdeNodeId() {
        return ideNodeId;
    }

    public int getIdeChordId() {
        return ideChordId;
    }

    public String getIdeNodeIdString() {
        return ideNodeIdString;
    }

    public List<Integer> getLearnerNodeIds() {
        return Collections.unmodifiableList(learnerNodeIds);
    }

    public List<Integer> getLearnerChordIds() {
        return Collections.unmodifiableList(learnerChordIds);
    }

    public LearningSession getLearningSession() {
        return learningSession;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void markCompleted() {
        this.completed = true;
    }
}
