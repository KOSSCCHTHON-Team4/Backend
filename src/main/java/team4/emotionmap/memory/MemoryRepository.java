package team4.emotionmap.memory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryRepository extends JpaRepository<Memory, Long> {

    List<Memory> findByUserId(Long userId);

    List<Memory> findByPlaceId(Long placeId);

    List<Memory> findByStatus(MemoryStatus status);

    /**
     * 벡터 유사도 검색 (코사인 거리 기준 최근접 이웃).
     *
     * pgvector 연산자: <=> 코사인, <-> L2, <#> 내적.
     * V1 의 HNSW 인덱스가 vector_cosine_ops 이므로 <=> 사용.
     *
     * 주의:
     *   - nativeQuery = true (JPQL 은 pgvector 연산자를 모른다).
     *   - float[] 파라미터는 hibernate-vector 가 vector 로 바인딩한다.
     *     드라이버가 캐스팅을 요구하면 "... <=> CAST(:embedding AS vector) ..." 로 바꾼다.
     *   - embedding 이 NULL 이거나 ACTIVE 가 아닌 행은 제외.
     *
     * @param embedding 질의 임베딩 (차원 = Memory.EMBEDDING_DIMENSION)
     * @param limit     반환 개수
     */
    @Query(value = """
            SELECT *
            FROM memory
            WHERE embedding IS NOT NULL
              AND status = 'ACTIVE'
            ORDER BY embedding <=> :embedding
            LIMIT :limit
            """, nativeQuery = true)
    List<Memory> findNearestByEmbedding(@Param("embedding") float[] embedding,
                                        @Param("limit") int limit);
}
