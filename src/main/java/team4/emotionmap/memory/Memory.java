package team4.emotionmap.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.dictionary.VibeVector;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.AxisSource;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.DataOrigin;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.ModerationStatus;
import team4.emotionmap.contracts.memory.OriginKind;

@Entity
@Table(name = "memories")
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Memory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "place_id", nullable = false)
    private UUID placeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "distribution_type", nullable = false, columnDefinition = "text")
    private DistributionType distributionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_kind", nullable = false, columnDefinition = "text")
    private OriginKind originKind;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_origin", nullable = false, columnDefinition = "text")
    private DataOrigin dataOrigin;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "place_label_snapshot", columnDefinition = "text")
    private String placeLabelSnapshot;

    @Column(name = "place_lat", nullable = false)
    private Double placeLat;

    @Column(name = "place_lng", nullable = false)
    private Double placeLng;

    @Column(name = "crowd_level", nullable = false)
    private Short crowdLevel;

    @Column(name = "spatial_feel", nullable = false)
    private Short spatialFeel;

    @Column(name = "company_fit", nullable = false)
    private Short companyFit;

    @Column(name = "stay_style", nullable = false)
    private Short stayStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "crowd_source", nullable = false, columnDefinition = "text")
    private AxisSource crowdSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "spatial_source", nullable = false, columnDefinition = "text")
    private AxisSource spatialSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_source", nullable = false, columnDefinition = "text")
    private AxisSource companySource;

    @Enumerated(EnumType.STRING)
    @Column(name = "stay_source", nullable = false, columnDefinition = "text")
    private AxisSource staySource;

    @Builder.Default
    @Column(name = "axis_definition_version", nullable = false)
    private Short axisDefinitionVersion = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "atmosphere_analysis_status", nullable = false, columnDefinition = "text")
    private AtmosphereAnalysisStatus atmosphereAnalysisStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_analysis_status", nullable = false, columnDefinition = "text")
    private CategoryAnalysisStatus categoryAnalysisStatus;

    @Column(name = "analysis_model", columnDefinition = "text")
    private String analysisModel;

    @Column(name = "analysis_prompt_version", columnDefinition = "text")
    private String analysisPromptVersion;

    @Column(name = "image_path", unique = true, columnDefinition = "text")
    private String imagePath;

    @Column(name = "image_media_type", columnDefinition = "text")
    private String imageMediaType;

    @Column(name = "image_size_bytes")
    private Long imageSizeBytes;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "content_status", nullable = false, columnDefinition = "text")
    private ContentStatus contentStatus = ContentStatus.ACTIVE;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false, columnDefinition = "text")
    private ModerationStatus moderationStatus = ModerationStatus.PENDING;

    @Column(name = "available_at")
    private Instant availableAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ---- 기획 §8·§9 분석 부가 출력 (최종 4축은 위 ±1 컬럼이 기준) ----
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> evidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> tags;

    @Column(name = "category_pred", columnDefinition = "text")
    private String categoryPred;

    @Column(name = "category_conf")
    private Double categoryConf;

    @Column(name = "safe")
    private Boolean safe;

    @Builder.Default
    @Column(name = "pii_masked", nullable = false)
    private Boolean piiMasked = false;

    @Column(name = "unsafe_reason", columnDefinition = "text")
    private String unsafeReason;

    public Atmospheres atmospheres() {
        return new Atmospheres(crowdLevel, spatialFeel, companyFit, stayStyle);
    }

    public VibeVector vibe() {
        return VibeVector.of(atmospheres());
    }

    /**
     * 안전 검사 결과 반영(A07). APPROVED 면 최초 available_at 을 <b>한 번만</b> 정한다 — 재시도·운영 상태 변경으로
     * 신규 글처럼 갱신하지 않는다. PRIVATE 는 available_at 을 갖지 않는다.
     */
    public void applyModeration(ModerationStatus verdict, Instant now) {
        if (distributionType != DistributionType.LETTER) {
            throw new IllegalStateException("Only LETTER memories are moderated");
        }
        if (verdict == ModerationStatus.NOT_REQUIRED || verdict == ModerationStatus.PENDING) {
            throw new IllegalArgumentException("Not a final moderation verdict: " + verdict);
        }
        this.moderationStatus = verdict;
        if (verdict == ModerationStatus.APPROVED && availableAt == null) {
            this.availableAt = now.isBefore(createdAt) ? createdAt : now;
        }
    }

    public boolean isDeliverable() {
        return distributionType == DistributionType.LETTER && contentStatus == ContentStatus.ACTIVE
                && moderationStatus == ModerationStatus.APPROVED && availableAt != null;
    }

    public void softDelete(Instant deletedAt) {
        this.contentStatus = ContentStatus.DELETED;
        this.deletedAt = deletedAt;
    }

    public void hide() {
        this.contentStatus = ContentStatus.HIDDEN;
    }

    public Memory copyForOwner(UUID ownerId, String independentImagePath) {
        if ((imagePath == null) != (independentImagePath == null)
                || (imagePath != null && imagePath.equals(independentImagePath))) {
            throw new IllegalArgumentException("A saved copy requires an independent image path");
        }
        return toBuilder().id(null).ownerId(ownerId)
                .distributionType(DistributionType.PRIVATE).originKind(OriginKind.LETTER_COPY)
                .imagePath(independentImagePath).contentStatus(ContentStatus.ACTIVE)
                .availableAt(null).createdAt(Instant.now()).deletedAt(null).build();
    }
}
