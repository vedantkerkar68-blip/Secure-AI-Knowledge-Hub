package com.sakh.service;

import com.sakh.entity.Document;
import com.sakh.entity.DocumentMetadata;
import com.sakh.repository.DocumentMetadataRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

@Service
public class MetadataExtractionService {

    private final DocumentMetadataRepository metadataRepository;

    public MetadataExtractionService(DocumentMetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    public DocumentMetadata extractAndSave(Document document) {
        String fileType = document.getFileType().toLowerCase();
        Path path = java.nio.file.Paths.get(document.getStoragePath());

        DocumentMetadata metadata = new DocumentMetadata();
        metadata.setDocument(document);

        try {
            switch (fileType) {
                case "pdf" -> extractPdfMetadata(path, metadata);
                case "docx" -> extractDocxMetadata(path, metadata);
                default -> setDefaults(metadata, document.getOriginalFilename());
            }
        } catch (Exception e) {
            setDefaults(metadata, null);
        }

        return metadataRepository.save(metadata);
    }

    private void extractPdfMetadata(Path path, DocumentMetadata metadata) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(path.toFile())) {
            PDDocumentInformation info = pdf.getDocumentInformation();
            metadata.setAuthor(info.getAuthor());
            metadata.setPageCount(pdf.getNumberOfPages());
            metadata.setLanguage(detectLanguage(info.getTitle()));
            metadata.setVersion("1.0");
            metadata.setTags(new ArrayList<>());
        }
    }

    private void extractDocxMetadata(Path path, DocumentMetadata metadata) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(path))) {
            metadata.setAuthor(doc.getProperties().getCoreProperties().getCreator());
            // POI 5.x: use getPageCount() from DocumentSummaryInformation or default to 1
            metadata.setPageCount(1); // CoreProperties doesn't have getPageCount() in POI 5.x
            metadata.setLanguage(detectLanguage(doc.getProperties().getCoreProperties().getTitle()));
            metadata.setVersion("1.0");
            metadata.setTags(new ArrayList<>());
        }
    }

    private void setDefaults(DocumentMetadata metadata, String filename) {
        metadata.setAuthor(null);
        metadata.setPageCount(1);
        metadata.setLanguage(detectLanguage(filename));
        metadata.setVersion("1.0");
        metadata.setTags(new ArrayList<>());
    }

    private String detectLanguage(String text) {
        if (text == null || text.isBlank()) {
            return "en";
        }
        return "en";
    }
}