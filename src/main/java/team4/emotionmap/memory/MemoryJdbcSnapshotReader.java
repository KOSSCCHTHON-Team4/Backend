package team4.emotionmap.memory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.contracts.media.ImageMediaType;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.AtmosphereSources;
import team4.emotionmap.contracts.memory.AxisSource;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAssignment;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.DataOrigin;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.ImageAttachment;
import team4.emotionmap.contracts.memory.MemorySnapshot;
import team4.emotionmap.contracts.memory.MemorySnapshotReader;
import team4.emotionmap.contracts.memory.ModerationStatus;
import team4.emotionmap.contracts.memory.OriginKind;

/**
 * Memory-owned bulk reader for the internal snapshot used by read projections and letter assembly.
 * It deliberately loads categories in a second set query, preserving stored slot order without a
 * per-memory repository lookup.
 */
@Component
public class MemoryJdbcSnapshotReader implements MemorySnapshotReader {

    private final NamedParameterJdbcTemplate jdbc;

    public MemoryJdbcSnapshotReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MemorySnapshot> readAll(Set<UUID> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return Map.of();
        }
        for (UUID memoryId : memoryIds) {
            if (memoryId == null) {
                throw new IllegalArgumentException("memoryIds must not contain null");
            }
        }

        List<UUID> ids = List.copyOf(memoryIds);
        MapSqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
        Map<UUID, MemoryFields> fieldsById = new HashMap<>();
        jdbc.query("""
                SELECT id, owner_id, place_id, distribution_type, origin_kind, data_origin,
                       content_status, moderation_status, available_at, content, place_label_snapshot,
                       place_lat, place_lng, crowd_level, spatial_feel, company_fit, stay_style,
                       crowd_source, spatial_source, company_source, stay_source, axis_definition_version,
                       atmosphere_analysis_status, category_analysis_status, analysis_model,
                       analysis_prompt_version, image_path, image_media_type, image_size_bytes,
                       created_at, deleted_at
                FROM memories
                WHERE id IN (:ids)
                """, parameters, resultSet -> {
            UUID id = resultSet.getObject("id", UUID.class);
            MemoryFields fields = new MemoryFields(
                    id,
                    resultSet.getObject("owner_id", UUID.class),
                    resultSet.getObject("place_id", UUID.class),
                    enumValue(DistributionType.class, resultSet.getString("distribution_type")),
                    enumValue(OriginKind.class, resultSet.getString("origin_kind")),
                    enumValue(DataOrigin.class, resultSet.getString("data_origin")),
                    enumValue(ContentStatus.class, resultSet.getString("content_status")),
                    enumValue(ModerationStatus.class, resultSet.getString("moderation_status")),
                    instant(resultSet.getObject("available_at", OffsetDateTime.class)),
                    resultSet.getString("content"),
                    resultSet.getString("place_label_snapshot"),
                    new GeoPoint(resultSet.getDouble("place_lat"), resultSet.getDouble("place_lng")),
                    new Atmospheres(resultSet.getDouble("crowd_level"), resultSet.getDouble("spatial_feel"),
                            resultSet.getDouble("company_fit"), resultSet.getDouble("stay_style")),
                    new AtmosphereSources(
                            enumValue(AxisSource.class, resultSet.getString("crowd_source")),
                            enumValue(AxisSource.class, resultSet.getString("spatial_source")),
                            enumValue(AxisSource.class, resultSet.getString("company_source")),
                            enumValue(AxisSource.class, resultSet.getString("stay_source"))),
                    resultSet.getInt("axis_definition_version"),
                    enumValue(AtmosphereAnalysisStatus.class,
                            resultSet.getString("atmosphere_analysis_status")),
                    enumValue(CategoryAnalysisStatus.class, resultSet.getString("category_analysis_status")),
                    resultSet.getString("analysis_model"),
                    resultSet.getString("analysis_prompt_version"),
                    image(resultSet.getString("image_path"), resultSet.getString("image_media_type"),
                            resultSet.getObject("image_size_bytes", Long.class)),
                    instant(resultSet.getObject("created_at", OffsetDateTime.class)),
                    instant(resultSet.getObject("deleted_at", OffsetDateTime.class)));
            if (fieldsById.put(id, fields) != null) {
                throw new IllegalStateException("duplicate memory snapshot row");
            }
        });

        Map<UUID, List<CategoryAssignment>> categoriesByMemory = new HashMap<>();
        jdbc.query("""
                SELECT mc.memory_id, mc.slot_no, mc.assignment_source, pc.code AS category_code
                FROM memory_categories mc
                JOIN place_categories pc ON pc.id = mc.category_id
                WHERE mc.memory_id IN (:ids)
                ORDER BY mc.memory_id, mc.slot_no
                """, parameters, resultSet -> {
            UUID memoryId = resultSet.getObject("memory_id", UUID.class);
            CategoryAssignment assignment = new CategoryAssignment(
                    enumValue(PlaceCategoryCode.class, resultSet.getString("category_code")),
                    resultSet.getInt("slot_no"),
                    enumValue(AxisSource.class, resultSet.getString("assignment_source")));
            categoriesByMemory.computeIfAbsent(memoryId, ignored -> new ArrayList<>()).add(assignment);
        });

        Map<UUID, MemorySnapshot> snapshots = new HashMap<>();
        fieldsById.forEach((id, fields) -> snapshots.put(id, fields.toSnapshot(
                categoriesByMemory.getOrDefault(id, List.of()))));
        return Map.copyOf(snapshots);
    }

    private static ImageAttachment image(String storageKey, String mediaType, Long sizeBytes) {
        if (storageKey == null) {
            if (mediaType != null || sizeBytes != null) {
                throw new IllegalStateException("incomplete memory image metadata");
            }
            return null;
        }
        if (mediaType == null || sizeBytes == null) {
            throw new IllegalStateException("incomplete memory image metadata");
        }
        ImageMediaType type = ImageMediaType.fromMimeType(mediaType)
                .orElseThrow(() -> new IllegalStateException("unknown memory image media type"));
        return new ImageAttachment(storageKey, type, sizeBytes);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (value == null) {
            throw new IllegalStateException("missing " + type.getSimpleName());
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("unknown " + type.getSimpleName(), error);
        }
    }

    private record MemoryFields(
            UUID id,
            UUID ownerId,
            UUID placeId,
            DistributionType distributionType,
            OriginKind originKind,
            DataOrigin dataOrigin,
            ContentStatus contentStatus,
            ModerationStatus moderationStatus,
            Instant availableAt,
            String content,
            String placeLabel,
            GeoPoint location,
            Atmospheres atmospheres,
            AtmosphereSources atmosphereSources,
            int axisDefinitionVersion,
            AtmosphereAnalysisStatus atmosphereAnalysisStatus,
            CategoryAnalysisStatus categoryAnalysisStatus,
            String analysisModel,
            String analysisPromptVersion,
            ImageAttachment image,
            Instant createdAt,
            Instant deletedAt
    ) {
        private MemorySnapshot toSnapshot(List<CategoryAssignment> categories) {
            return new MemorySnapshot(id, ownerId, placeId, distributionType, originKind, dataOrigin,
                    contentStatus, moderationStatus, availableAt, content, placeLabel, location, atmospheres,
                    atmosphereSources, axisDefinitionVersion, atmosphereAnalysisStatus, categoryAnalysisStatus,
                    analysisModel, analysisPromptVersion, categories, image, createdAt, deletedAt);
        }
    }
}
