package com.example.peersimdjl.api;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path uploadDir = Path.of("uploads");

    @PostConstruct
    public void init() throws IOException {
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    /**
     * Store a list of uploaded CSV files.
     *
     * @param files uploaded multipart files
     * @return list of relative paths ("uploads/<uniqueName>")
     * @throws IllegalArgumentException if a file is invalid
     * @throws IOException              on I/O error
     */
    public List<String> store(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> storedPaths = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                // Validate extension
                String original = file.getOriginalFilename();
                if (original == null || !original.toLowerCase().endsWith(".csv")) {
                    throw new IllegalArgumentException("Only CSV files are allowed: " + original);
                }
                // Validate size <= 50 MB
                if (file.getSize() > 50L * 1024 * 1024) {
                    throw new IllegalArgumentException("File too large (max 50 MB): " + original);
                }
                // Sanitize name (remove path separators)
                String sanitized = original.replaceAll("[\\\\/]+", "_");
                String uniqueName = UUID.randomUUID() + "_" + sanitized;
                Path destination = uploadDir.resolve(uniqueName);
                // Fermer explicitement le flux pour libérer le verrou Windows
                try (InputStream is = file.getInputStream()) {
                    Files.copy(is, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                storedPaths.add("uploads/" + uniqueName);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to store file: " + file.getOriginalFilename(), e);
            }
        }
        return storedPaths;
    }
}
