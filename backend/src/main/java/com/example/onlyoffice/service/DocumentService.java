package com.example.onlyoffice.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class DocumentService {

    @Value("${storage.path}")
    private String storagePath;

    @Value("${server.baseUrl}")
    private String serverBaseUrl;

    private Path rootLocation;

    @PostConstruct
    public void init() {
        try {
            this.rootLocation = Paths.get(storagePath);
            Files.createDirectories(this.rootLocation);
            log.info("Storage directory initialized at: {}", this.rootLocation.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage", e);
        }
    }

    public List<String> listFiles() {
        try (Stream<Path> walk = Files.walk(this.rootLocation, 1)) {
            return walk
                    .filter(path -> !Files.isDirectory(path))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read stored files", e);
        }
    }

    public File getFile(String fileName) {
        return this.rootLocation.resolve(fileName).toFile();
    }

    public void saveFile(String fileName, InputStream inputStream) {
        try {
            log.info("Saving file: {}", fileName);
            Path destinationFile = this.rootLocation.resolve(fileName);
            log.info("Saving file to: {}", destinationFile);
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file " + fileName, e);
        }
    }

    public String getServerUrl() {
        // Return the configurable base URL (e.g., http://host.docker.internal:8080)
        return serverBaseUrl;
    }
}
