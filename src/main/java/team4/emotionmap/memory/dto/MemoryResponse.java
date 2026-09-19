package team4.emotionmap.memory.dto;

import java.time.Instant;
import java.util.UUID;
import team4.emotionmap.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.memory.CategoryAnalysisStatus;
import team4.emotionmap.memory.ContentStatus;
import team4.emotionmap.memory.DataOrigin;
import team4.emotionmap.memory.DistributionType;
import team4.emotionmap.memory.Memory;
import team4.emotionmap.memory.ModerationStatus;
import team4.emotionmap.memory.OriginKind;
import team4.emotionmap.memory.ValueSource;

/** No author identity or storage key is exposed to recipients. */
public record MemoryResponse(
        UUID id, UUID placeId, String content, String imageUrl,
        DistributionType distributionType, OriginKind originKind, DataOrigin dataOrigin,
        String placeLabelSnapshot, Double placeLat, Double placeLng,
        Short crowdLevel, Short spatialFeel, Short companyFit, Short stayStyle,
        ValueSource crowdSource, ValueSource spatialSource, ValueSource companySource, ValueSource staySource,
        Short axisDefinitionVersion, AtmosphereAnalysisStatus atmosphereAnalysisStatus,
        CategoryAnalysisStatus categoryAnalysisStatus, ContentStatus contentStatus,
        ModerationStatus moderationStatus, Instant availableAt, Instant createdAt
) {
    public static MemoryResponse from(Memory memory) {
        return new MemoryResponse(memory.getId(), memory.getPlaceId(), memory.getContent(),
                memory.getImagePath() == null ? null : "/v1/memories/" + memory.getId() + "/image",
                memory.getDistributionType(), memory.getOriginKind(), memory.getDataOrigin(),
                memory.getPlaceLabelSnapshot(), memory.getPlaceLat(), memory.getPlaceLng(),
                memory.getCrowdLevel(), memory.getSpatialFeel(), memory.getCompanyFit(), memory.getStayStyle(),
                memory.getCrowdSource(), memory.getSpatialSource(), memory.getCompanySource(), memory.getStaySource(),
                memory.getAxisDefinitionVersion(), memory.getAtmosphereAnalysisStatus(),
                memory.getCategoryAnalysisStatus(), memory.getContentStatus(), memory.getModerationStatus(),
                memory.getAvailableAt(), memory.getCreatedAt());
    }
}
