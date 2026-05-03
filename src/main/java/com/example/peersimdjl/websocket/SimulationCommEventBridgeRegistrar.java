package com.example.peersimdjl.websocket;

import com.example.peersimdjl.SimulationCommEventLogger;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Component
public class SimulationCommEventBridgeRegistrar {

    private final WebSocketEventBridge bridge;

    public SimulationCommEventBridgeRegistrar(WebSocketEventBridge bridge) {
        this.bridge = bridge;
    }

    @PostConstruct
    public void register() {
        SimulationCommEventLogger.setBridge(bridge);
    }
}
