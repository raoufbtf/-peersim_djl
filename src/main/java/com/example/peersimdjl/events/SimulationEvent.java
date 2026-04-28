package com.example.peersimdjl.events;

import java.time.Instant;
import java.util.Map;

public class SimulationEvent {

    private final Instant timestamp;
    private final String level;
    private final String type;
    private final String sessionId;
    private final String nodeId;
    private final String message;
    private final Map<String, Object> payload;

    public SimulationEvent(Instant timestamp, String level, String type, String sessionId, String nodeId, String message, Map<String, Object> payload) {
        this.timestamp = timestamp;
        this.level = level;
        this.type = type;
        this.sessionId = sessionId;
        this.nodeId = nodeId;
        this.message = message;
        this.payload = payload;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getLevel() {
        return level;
    }

    public String getType() {
        return type;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
