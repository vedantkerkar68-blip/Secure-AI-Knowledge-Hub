package com.sakh.rag;

import com.sakh.repository.DocumentRepository;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class PromptBuilder {

    private static final int MAX_CONTEXT_LENGTH = 12000;

    private static final String SYSTEM_INSTRUCTIONS = """
            You are Secure AI Knowledge Hub.
            Only answer from supplied context.
            Never hallucinate.
            If information is unavailable say "I don't have enough information."\
            """;

    private final DocumentRepository documentRepository;

    public PromptBuilder(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public Prompt build(String question, List<Document> documents) {
        return build(question, documents, null);
    }

    public Prompt build(String question, List<Document> documents, String conversationHistory) {
        String context = buildContext(documents);
        StringBuilder userContent = new StringBuilder();
        userContent.append("CONTEXT\n\n").append(context);
        if (conversationHistory != null && !conversationHistory.isBlank()) {
            userContent.append("\n\nCONVERSATION HISTORY\n").append(conversationHistory);
        }
        userContent.append("\n\nQUESTION\n\n").append(question);
        return new Prompt(List.of(
                new SystemMessage(SYSTEM_INSTRUCTIONS),
                new UserMessage(userContent.toString())));
    }

    private String buildContext(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return "No relevant documents found.";
        }

        StringBuilder sb = new StringBuilder();
        int docNumber = 0;

        for (Document doc : documents) {
            String text = doc.getText();
            if (text == null || text.isBlank()) {
                continue;
            }

            docNumber++;
            String title = resolveTitle(doc);
            String department = resolveDepartment(doc);
            Object page = doc.getMetadata().get("pageNumber");
            Object chunkIndex = doc.getMetadata().get("chunkIndex");

            String entry = formatEntry(docNumber, title, department, page, chunkIndex, sanitize(text));

            if (sb.length() + entry.length() > MAX_CONTEXT_LENGTH) {
                break;
            }

            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(entry);
        }

        if (sb.isEmpty()) {
            return "No relevant documents found.";
        }
        return sb.toString();
    }

    private String formatEntry(int number, String title, String department, Object page, Object chunkIndex, String text) {
        StringBuilder sb = new StringBuilder();
        sb.append("Document ").append(number).append("\n");
        sb.append("Title: ").append(title != null ? title : "N/A").append("\n");
        sb.append("Department: ").append(department != null ? department : "N/A").append("\n");
        if (page != null) {
            sb.append("Page: ").append(page).append("\n");
        }
        if (chunkIndex != null) {
            sb.append("Chunk: ").append(chunkIndex).append("\n");
        }
        sb.append(text);
        return sb.toString();
    }

    private String resolveTitle(Document doc) {
        Object docIdObj = doc.getMetadata().get("documentId");
        if (docIdObj instanceof Number num) {
            Optional<com.sakh.entity.Document> entity = documentRepository.findById(num.longValue());
            if (entity.isPresent()) {
                String title = entity.get().getTitle();
                return title != null ? title : "Untitled";
            }
        }
        return "Unknown";
    }

    private String resolveDepartment(Document doc) {
        Object deptId = doc.getMetadata().get("departmentId");
        if (deptId != null) {
            return "Department #" + deptId;
        }
        return "N/A";
    }

    private String sanitize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c >= 32) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
