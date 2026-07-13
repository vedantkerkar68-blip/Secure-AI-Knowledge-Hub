package com.sakh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableAsync
public class BackendApplication {

    public static void main(String[] args) {
        loadEnvFile();
        SpringApplication.run(BackendApplication.class, args);
    }

    private static void loadEnvFile() {
        Path envFile = Path.of("", ".env").toAbsolutePath();
        if (!Files.exists(envFile)) {
            envFile = Path.of("backend", ".env").toAbsolutePath();
        }
        if (Files.exists(envFile)) {
            try (var lines = Files.lines(envFile)) {
                lines.forEach(line -> {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                        return;
                    }
                    String[] parts = line.split("=", 2);
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    if (System.getenv(key) == null) {
                        System.setProperty(key, value);
                    }
                });
            } catch (IOException e) {
                // .env is optional; env vars or other mechanisms may provide the values
            }
        }
    }
}
