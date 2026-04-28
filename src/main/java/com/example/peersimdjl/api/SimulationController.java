package com.example.peersimdjl.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/simulations")
@CrossOrigin(origins = "http://localhost:5173")
public class SimulationController {

    private final SimulationService service;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SimulationController(SimulationService service, FileStorageService fileStorageService) {
        this.service = service;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping(value = "/start", consumes = {"multipart/form-data"})
    public ResponseEntity<?> start(
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart("config") String configJson) {
        try {
            SimulationRequest req = objectMapper.readValue(configJson, SimulationRequest.class);

            // --- CORRECTION : Vider les buffers pour libérer les verrous Windows ---
            if (files != null) {
                for (MultipartFile file : files) {
                    if (!file.isEmpty()) {
                        try (InputStream is = file.getInputStream()) {
                            byte[] buffer = new byte[8192];
                            while (is.read(buffer) != -1) {
                                // Forcer la lecture complète du stream
                            }
                        } catch (IOException e) {
                            System.err.println("Warning: Could not fully consume stream for " + file.getOriginalFilename());
                        }
                    }
                }
            }
            // ---------------------------------------------------------------------

            List<String> paths = fileStorageService.store(files != null ? files : Collections.emptyList());
            req.setDatasetPaths(paths.toArray(new String[0]));

            service.start(req);
            return ResponseEntity.ok(Collections.singletonMap("message", "started"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Collections.singletonMap("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Collections.singletonMap("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(400).body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stop() {
        try {
            service.stop();
            return ResponseEntity.ok(Collections.singletonMap("message", "stopped"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Collections.singletonMap("state", service.getState().name()));
    }
}
