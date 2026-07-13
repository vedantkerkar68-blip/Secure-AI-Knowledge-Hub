package com.sakh.repository;

import com.sakh.entity.Chunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    List<Chunk> findByDocumentIdOrderByChunkIndex(Long documentId);

    void deleteByDocumentId(Long documentId);

    @Query(value = """
        SELECT c.* FROM chunks c 
        WHERE c.document_id = :documentId 
        AND to_tsvector('english', c.chunk_text) @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank_cd(to_tsvector('english', c.chunk_text), plainto_tsquery('english', :query)) DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Chunk> findByKeywordSearch(Long documentId, String query, int limit);
}