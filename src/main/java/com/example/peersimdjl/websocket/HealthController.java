package com.example.peersimdjl.websocket;

import com.example.peersimdjl.api.SimulationService;
import com.example.peersimdjl.events.SimulationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final SimulationService simulationService;
    private final SimulationEventPublisher publisher;

    public HealthController(SimulationService simulationService, SimulationEventPublisher publisher) {
        this.simulationService = simulationService;
        this.publisher = publisher;
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Collections.unmodifiableMap(
                java.util.Map.of(
                        "status", "UP",
                        "simulationState", simulationService.getState().name(),
                        "eventsBuffered", publisher.getLast(5000).size()
                )
        ));
    }
}
