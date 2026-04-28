package com.example.peersimdjl.events;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.ResponseEntity;
import java.util.List;

@RestController
@RequestMapping("/api/simulations")
@CrossOrigin(origins = "http://localhost:5173")
public class SimulationEventsController {

    private final SimulationEventPublisher publisher;

    public SimulationEventsController(SimulationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @GetMapping("/events")
    public ResponseEntity<?> getEvents(@RequestParam(defaultValue = "200") int limit) {
        if (limit < 1 || limit > 5000) {
            return ResponseEntity.status(400).body(java.util.Collections.singletonMap("error", "limit must be 1-5000"));
        }
        List<SimulationEvent> events = publisher.getLast(limit);
        return ResponseEntity.ok(events);
    }
}
