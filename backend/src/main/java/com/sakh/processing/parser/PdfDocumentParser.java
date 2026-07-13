package com.sakh.processing.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String contentType) {
        return "application/pdf".equalsIgnoreCase(contentType)
                || "pdf".equalsIgnoreCase(contentType);
    }

    @Override
    public String extractText(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            try (var document = Loader.loadPDF(bytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract text from PDF: " + file, e);
        }
    }
}