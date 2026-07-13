package com.sakh.processing.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class DocxDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String contentType) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)
                || "application/msword".equalsIgnoreCase(contentType);
    }

    @Override
    public String extractText(Path file) {
        try (InputStream is = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract text from DOCX: " + file, e);
        }
    }
}