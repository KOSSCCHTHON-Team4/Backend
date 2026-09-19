package team4.emotionmap.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * memory 테이블 매핑 (V1__init.sql). 장소에 남긴 기억.
 *
 * 도메인 규칙:
 *   - emotionTag : 서버가 content 에서 Claude(sonnet-5) 로 자동 추출해 채운다.
 *                  생성 시점엔 보통 null 이고, 추출 후 assignEmotionTag() 로 채운다.
 *                  (API 요청에서 감정을 직접 받지 않는다.)
 *   - embedding  : pgvector vector(1024). 임베딩 모델(Voyage 등)로 생성해 채운다.
 *                  Claude 는 임베딩 모델이 아니므로 여기엔 쓰지 않는다. 생성 전엔 null.
 *   - visibility : LETTER / PRIVATE
 *   - status     : ACTIVE / HIDDEN / DELETED (기본 ACTIVE)
 *   - userId/placeId 는 우선 FK 값만 보관(해커톤 단순화). 필요 시 @ManyToOne 확장.
 */
@Entity
@Table(name = "memory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Memory {

    /** DB vector(N) 과 일치시킬 임베딩 차원. 임베딩 모델 확정 시 마이그레이션과 함께 바꾼다. */
    public static final int EMBEDDING_DIMENSION = 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "place_id")
    private Long placeId;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(length = 500)
    private String imagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Visibility visibility;

    /** 본문에서 Claude 로 자동 추출한 감정 태그. 추출 전에는 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "emotion_tag", length = 30)
    private Emotion emotionTag;

    // ---- pgvector: vector(1024) 매핑 (임베딩 모델 추후 확정) ----
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSION)
    @Column(columnDefinition = "vector(1024)")
    private float[] embedding;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemoryStatus status;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    private Memory(Long userId, Long placeId, String content, String imagePath,
                   Visibility visibility) {
        this.userId = userId;
        this.placeId = placeId;
        this.content = content;
        this.imagePath = imagePath;
        this.visibility = visibility;
        this.status = MemoryStatus.ACTIVE;
        this.createdAt = OffsetDateTime.now();
        // emotionTag, embedding 은 생성 후 서버가 채운다.
    }

    /** 본문에서 Claude 로 추출한 감정 태그를 채운다. */
    public void assignEmotionTag(Emotion emotionTag) {
        this.emotionTag = emotionTag;
    }

    /** 임베딩 모델이 생성한 벡터를 채운다. */
    public void assignEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    /** 신고 등으로 숨김 처리. */
    public void hide() {
        this.status = MemoryStatus.HIDDEN;
    }

    /** 삭제 표시(소프트). 하드 삭제는 repository.delete 로 수행. */
    public void markDeleted() {
        this.status = MemoryStatus.DELETED;
    }
}
