package com.sakh.repository;

import com.sakh.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    void deleteByDocumentId(Long documentId);

    @Query(value = """
        SELECT c.id, c.document_id, c.chunk_index, c.chunk_text, c.page_number, c.section_title,
               d.department_id, u.email,
               ts_rank_cd(to_tsvector('english', c.chunk_text), plainto_tsquery('english', :query))
        FROM chunks c
        JOIN documents d ON c.document_id = d.id
        JOIN users u ON d.uploaded_by = u.id
        WHERE to_tsvector('english', c.chunk_text) @@ plainto_tsquery('english', :query)
        AND d.status = 'READY'
        ORDER BY 9 DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findKeywordSearchGlobal(String query, int limit);
}
