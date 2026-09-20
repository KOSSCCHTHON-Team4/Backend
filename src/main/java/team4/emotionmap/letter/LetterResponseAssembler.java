package team4.emotionmap.letter;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.MemorySnapshot;
import team4.emotionmap.contracts.memory.ModerationStatus;
import team4.emotionmap.letter.dto.LetterResponse;
import team4.emotionmap.memory.MemoryReadAccess;
import team4.emotionmap.memory.dto.MemoryResponse;

/** Shared safe assembler for history and today's delivery. */
@Component
public class LetterResponseAssembler {

    public LetterResponse assemble(MemoryReadAccess.DeliverySnapshot delivery, MemorySnapshot source) {
        Objects.requireNonNull(delivery, "delivery");
        Objects.requireNonNull(source, "source");
        if (!delivery.memoryId().equals(source.id())) {
            throw new IllegalStateException("delivery source does not match snapshot");
        }
        if (!isAvailable(source)) {
            return new LetterResponse(delivery.deliveryId(), delivery.memoryId(), delivery.serviceDate(),
                    delivery.deliveredAt(), delivery.readAt(), delivery.likedAt(), LetterAvailability.UNAVAILABLE,
                    unavailableReason(source), null, null, null, List.of(), null, null, null, null);
        }
        List<PlaceCategoryCode> categories = source.categories().stream().map(assignment -> assignment.code()).toList();
        MemoryResponse.CategoryStatus categoryStatus = categories.isEmpty()
                ? MemoryResponse.CategoryStatus.UNCLASSIFIED : MemoryResponse.CategoryStatus.CLASSIFIED;
        return new LetterResponse(delivery.deliveryId(), delivery.memoryId(), delivery.serviceDate(),
                delivery.deliveredAt(), delivery.readAt(), delivery.likedAt(), LetterAvailability.AVAILABLE,
                null, source.content(), source.hasImage() ? "/v1/memories/" + source.id() + "/image" : null,
                source.atmospheres(), categories, categoryStatus,
                new MemoryResponse.PlaceSnapshot(source.placeId(), source.location().lat(), source.location().lng(),
                        source.placeLabelSnapshot()),
                source.createdAt(), source.dataOrigin());
    }

    public boolean isAvailable(MemorySnapshot source) {
        return source.contentStatus() == ContentStatus.ACTIVE
                && source.distributionType() == team4.emotionmap.contracts.memory.DistributionType.LETTER
                && source.moderationStatus() == ModerationStatus.APPROVED;
    }

    private static LetterResponse.UnavailableReason unavailableReason(MemorySnapshot source) {
        return source.contentStatus() == ContentStatus.DELETED
                ? LetterResponse.UnavailableReason.DELETED : LetterResponse.UnavailableReason.HIDDEN;
    }
}
