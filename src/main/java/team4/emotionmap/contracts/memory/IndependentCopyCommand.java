package team4.emotionmap.contracts.memory;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.geo.GeoPoint;

/**
 * 좋아요 시 수신자 소유 독립 PRIVATE({@code origin_kind=LETTER_COPY}) 를 만들기 위한 입력.
 * BE2 like use case 가 {@link MemorySnapshot} 에서 <b>내용만</b> 옮겨 만든다.
 *
 * <p>의도적으로 원문 memory id 필드가 없다. 넣을 자리를 만들지 않는 것이 계약이다(불변 규칙 3).
 * {@code image} 는 {@code LocalImageStore.duplicateIndependent} 로 만든 <b>별도 경로</b>여야 한다.
 */
public record IndependentCopyCommand(
        UUID newOwnerId,
        UUID placeId,
        DataOrigin dataOrigin,
        String content,
        String placeLabelSnapshot,
        GeoPoint location,
        Atmospheres atmospheres,
        AtmosphereSources atmosphereSources,
        int axisDefinitionVersion,
        AtmosphereAnalysisStatus atmosphereAnalysisStatus,
        CategoryAnalysisStatus categoryAnalysisStatus,
        String analysisModel,
        String analysisPromptVersion,
        List<CategoryAssignment> categories,
        ImageAttachment image
) {
    public IndependentCopyCommand {
        Objects.requireNonNull(newOwnerId);
        Objects.requireNonNull(placeId);
        Objects.requireNonNull(dataOrigin);
        Objects.requireNonNull(content);
        Objects.requireNonNull(location);
        Objects.requireNonNull(atmospheres);
        Objects.requireNonNull(atmosphereSources);
        Objects.requireNonNull(atmosphereAnalysisStatus);
        Objects.requireNonNull(categoryAnalysisStatus);
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    /** 원문 스냅샷에서 내용만 복사한다. 원문 id·owner·image 경로는 옮기지 않는다. */
    public static IndependentCopyCommand fromOriginal(MemorySnapshot original, UUID newOwnerId,
                                                      ImageAttachment independentImage) {
        return new IndependentCopyCommand(
                newOwnerId,
                original.placeId(),
                original.dataOrigin(),
                original.content(),
                original.placeLabelSnapshot(),
                original.location(),
                original.atmospheres(),
                original.atmosphereSources(),
                original.axisDefinitionVersion(),
                original.atmosphereAnalysisStatus(),
                original.categoryAnalysisStatus(),
                original.analysisModel(),
                original.analysisPromptVersion(),
                original.categories(),
                independentImage);
    }
}
