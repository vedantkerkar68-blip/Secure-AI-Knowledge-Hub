package com.sakh.repository;

import com.sakh.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    void deleteByDocumentId(Long documentId);

    /**
     * Keyword search restricted to the documents the caller may actually see.
     *
     * <p>The authorization predicate is applied in SQL, before {@code LIMIT}, so that
     * unauthorized high-ranked rows cannot crowd authorized matches out of the
     * candidate pool. Callers must pass a non-empty {@code visibleDepartmentIds}
     * (use the {@code -1} sentinel when the user has no visible department) so the
     * {@code IN} list never renders as an empty list.
     *
     * <p>This is a candidate filter only. RetrieverService still applies the
     * centralized {@code DocumentAuthorizationService.isAccessibleForRag} check to
     * every chunk before it can reach the LLM prompt.
     */
    @Query(value = """
        SELECT c.id, c.document_id, c.chunk_index, c.chunk_text, c.page_number, c.section_title,
               d.department_id, u.email, d.access_scope,
               ts_rank_cd(to_tsvector('english', c.chunk_text), plainto_tsquery('english', :query))
        FROM chunks c
        JOIN documents d ON c.document_id = d.id
        JOIN users u ON d.uploaded_by = u.id
        WHERE to_tsvector('english', c.chunk_text) @@ plainto_tsquery('english', :query)
        AND d.status = 'READY'
        AND (
              :unrestricted = true
           OR (:includeShared = true AND d.access_scope = 'ALL')
           OR d.department_id IN (:visibleDepartmentIds)
           OR (:includeOwnUploads = true AND LOWER(u.email) = LOWER(:uploaderEmail))
        )
        ORDER BY 10 DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findKeywordSearchGlobal(String query, int limit,
                                           List<Long> visibleDepartmentIds,
                                           String uploaderEmail,
                                           boolean unrestricted,
                                           boolean includeShared,
                                           boolean includeOwnUploads);

    @Query(value = """
        SELECT c.id, c.document_id, c.chunk_index, c.chunk_text, c.page_number, c.section_title,
               d.department_id, u.email,
               ts_rank_cd(to_tsvector('english', c.chunk_text), plainto_tsquery('english', :query))
        FROM chunks c
        JOIN documents d ON c.document_id = d.id
        JOIN users u ON d.uploaded_by = u.id
        WHERE to_tsvector('english', c.chunk_text) @@ plainto_tsquery('english', :query)
        AND c.document_id = :documentId
        AND d.status = 'READY'
        ORDER BY 9 DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findKeywordSearchInDocument(String query, Long documentId, int limit);
}
