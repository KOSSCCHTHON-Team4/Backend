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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    private ValueSource crowdSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "spatial_source", nullable = false, columnDefinition = "text")
    private ValueSource spatialSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_source", nullable = false, columnDefinition = "text")
    private ValueSource companySource;

    @Enumerated(EnumType.STRING)
    @Column(name = "stay_source", nullable = false, columnDefinition = "text")
    private ValueSource staySource;

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
