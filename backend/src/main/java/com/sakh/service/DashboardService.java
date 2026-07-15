package com.sakh.service;

import com.sakh.dto.DashboardResponse;
import com.sakh.enums.DocumentStatus;
import com.sakh.repository.ChatMessageRepository;
import com.sakh.repository.ChatSessionRepository;
import com.sakh.repository.DepartmentRepository;
import com.sakh.repository.DocumentRepository;
import com.sakh.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final EntityManager entityManager;

    public DashboardService(UserRepository userRepository,
                            DepartmentRepository departmentRepository,
                            DocumentRepository documentRepository,
                            ChatSessionRepository chatSessionRepository,
                            ChatMessageRepository chatMessageRepository,
                            EntityManager entityManager) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.documentRepository = documentRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.entityManager = entityManager;
    }

    public DashboardResponse getDashboard() {
        long users = userRepository.count();
        long departments = departmentRepository.count();
        long documents = documentRepository.count();
        long processedDocuments = documentRepository.countByStatus(DocumentStatus.READY);
        long failedDocuments = documentRepository.countByStatus(DocumentStatus.FAILED);
        long chatSessions = chatSessionRepository.count();
        long chatMessages = chatMessageRepository.count();
        long vectorDocuments = countVectorDocuments();

        return DashboardResponse.builder()
                .users(users)
                .departments(departments)
                .documents(documents)
                .processedDocuments(processedDocuments)
                .failedDocuments(failedDocuments)
                .chatSessions(chatSessions)
                .chatMessages(chatMessages)
                .vectorDocuments(vectorDocuments)
                .build();
    }

    private long countVectorDocuments() {
        try {
            var query = entityManager.createNativeQuery("SELECT COUNT(*) FROM vector_store");
            Number result = (Number) query.getSingleResult();
            return result != null ? result.longValue() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
