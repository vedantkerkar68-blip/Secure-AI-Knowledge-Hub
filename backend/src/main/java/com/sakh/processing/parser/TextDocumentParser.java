package com.sakh.processing.parser;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class TextDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String contentType) {
        return "text/plain".equalsIgnoreCase(contentType)
                || "text/markdown".equalsIgnoreCase(contentType)
                || "text/x-markdown".equalsIgnoreCase(contentType);
    }

    @Override
    public String extractText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read text file: " + file, e);
        }
    }
}