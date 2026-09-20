package team4.emotionmap.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team4.emotionmap.account.AccountAccessService;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.contracts.memory.AtmosphereAnalysisStatus;
import team4.emotionmap.contracts.memory.AtmosphereSources;
import team4.emotionmap.contracts.memory.AxisSource;
import team4.emotionmap.contracts.memory.CategoryAnalysisStatus;
import team4.emotionmap.contracts.memory.CategoryAssignment;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.DataOrigin;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.MemorySnapshot;
import team4.emotionmap.contracts.memory.ModerationStatus;
import team4.emotionmap.contracts.memory.OriginKind;
import team4.emotionmap.contracts.memory.MemorySnapshotReader;
import team4.emotionmap.memory.dto.MemoryResponse;
class MemoryQueryServiceProjectionTest {

    @Test
    void ownerAndRecipientProjectionsExposeOnlyTheirRoleSpecificMetadata() {
        UUID memoryId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID placeId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-09-20T03:00:00Z");
        MemorySnapshot snapshot = new MemorySnapshot(memoryId, ownerId, placeId, DistributionType.LETTER,
                OriginKind.DIRECT, DataOrigin.PARTICIPANT, ContentStatus.ACTIVE, ModerationStatus.APPROVED,
                createdAt, "synthetic projection body", "synthetic place", new GeoPoint(37.5, 127.0),
                new Atmospheres(0.125, -0.25, 0.5, -0.75), AtmosphereSources.ALL_USER, 2,
                AtmosphereAnalysisStatus.NOT_RUN, CategoryAnalysisStatus.NOT_RUN, null, null,
                List.of(new CategoryAssignment(PlaceCategoryCode.CAFE, 1, AxisSource.USER)), null, createdAt, null);
        MemorySnapshotReader snapshots = mock(MemorySnapshotReader.class);
        MemoryReadAccess deliveries = mock(MemoryReadAccess.class);
        AccountAccessService accountAccess = mock(AccountAccessService.class);
        MemoryAccessService access = new MemoryAccessService(mock(MemoryRepository.class), deliveries);
        MemoryQueryService query = new MemoryQueryService(accountAccess, mock(MemoryReadJdbcQuery.class), snapshots,
                deliveries, access, null, null, null, null);
        MemoryReadAccess.DeliverySnapshot delivery = new MemoryReadAccess.DeliverySnapshot(deliveryId, memoryId,
                LocalDate.of(2026, 9, 20), Instant.parse("2026-09-20T03:10:00Z"), null, null);
        when(snapshots.readAll(Set.of(memoryId))).thenReturn(Map.of(memoryId, snapshot));
        when(deliveries.findDeliveries(ownerId, Set.of(memoryId))).thenReturn(Map.of());
        when(deliveries.countLikes(Set.of(memoryId))).thenReturn(Map.of(memoryId, 4L));
        when(deliveries.findDeliveries(recipientId, Set.of(memoryId))).thenReturn(Map.of(memoryId, delivery));

        MemoryResponse owner = query.get(ownerId, memoryId);
        MemoryResponse recipient = query.get(recipientId, memoryId);

        assertThat(owner.viewerRole()).isEqualTo(MemoryResponse.ViewerRole.OWNER);
        assertThat(owner.ownerState()).isNotNull();
        assertThat(owner.ownerState().likeCount()).isEqualTo(4L);
        assertThat(owner.ownerState().analysis().atmosphereStatus()).isEqualTo(AtmosphereAnalysisStatus.NOT_RUN);
        assertThat(owner.delivery()).isNull();
        assertThat(owner.categories()).containsExactly(PlaceCategoryCode.CAFE);
        assertThat(recipient.viewerRole()).isEqualTo(MemoryResponse.ViewerRole.RECIPIENT);
        assertThat(recipient.ownerState()).isNull();
        assertThat(recipient.delivery()).isNotNull();
        assertThat(recipient.delivery().deliveryId()).isEqualTo(deliveryId);
        assertThat(recipient.content()).isEqualTo("synthetic projection body");
        assertThat(recipient.place().id()).isEqualTo(placeId);
    }
}
