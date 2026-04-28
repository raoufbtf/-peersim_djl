package com.example.peersimdjl.events;

import org.springframework.stereotype.Component;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import com.example.peersimdjl.websocket.WebSocketEventBridge;

@Component
public class SimulationEventPublisher {

    private final WebSocketEventBridge webSocketBridge;

    public SimulationEventPublisher(WebSocketEventBridge webSocketBridge) {
        this.webSocketBridge = webSocketBridge;
    }

    private final ArrayDeque<SimulationEvent> buffer = new ArrayDeque<>(5000);

    public synchronized void publish(SimulationEvent event) {
        if (buffer.size() >= 5000) {
            buffer.pollFirst();
        }
        buffer.addLast(event);
        // Forward the event over WebSocket, any exception is handled inside the bridge
        if (webSocketBridge != null) {
            webSocketBridge.send(event);
        }
    }

    public synchronized List<SimulationEvent> getLast(int limit) {
        List<SimulationEvent> result = new ArrayList<>(limit);
        for (SimulationEvent event : buffer) {
            result.add(event);
        }
        int fromIndex = Math.max(0, result.size() - limit);
        return new ArrayList<>(result.subList(fromIndex, result.size()));
    }
}
