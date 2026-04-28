package com.example.peersimdjl.websocket;

import com.example.peersimdjl.events.SimulationEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketEventBridge {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventBridge(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void send(SimulationEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/sim-events", event);
        } catch (Exception e) {
            // silently ignore
        }
    }
}
