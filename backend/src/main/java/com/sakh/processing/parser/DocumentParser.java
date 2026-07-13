package com.sakh.processing.parser;

import java.nio.file.Path;

public interface DocumentParser {

    boolean supports(String contentType);

    String extractText(Path file);
}