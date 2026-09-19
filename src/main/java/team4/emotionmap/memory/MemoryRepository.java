package team4.emotionmap.memory;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.ModerationStatus;

public interface MemoryRepository extends JpaRepository<Memory, UUID> {
    List<Memory> findByOwnerIdAndContentStatusOrderByCreatedAtDesc(UUID ownerId, ContentStatus contentStatus);
    List<Memory> findByPlaceIdAndContentStatusOrderByCreatedAtDesc(UUID placeId, ContentStatus contentStatus);
    boolean existsByImagePath(String imagePath);
    boolean existsByImagePathIsNotNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Memory m where m.id = :id")
    Optional<Memory> findByIdForUpdate(@Param("id") UUID id);

    /** 안전 검사 재시도 대상: ACTIVE LETTER 중 상태가 목록에 있고 생성이 기준 시각 이전인 것, 오래된 순. */
    @Query("select m.id from Memory m where m.distributionType = team4.emotionmap.contracts.memory.DistributionType.LETTER"
            + " and m.contentStatus = team4.emotionmap.contracts.memory.ContentStatus.ACTIVE"
            + " and m.moderationStatus in :statuses and m.createdAt < :olderThan order by m.createdAt asc")
    List<UUID> findModerationRetryCandidates(@Param("statuses") List<ModerationStatus> statuses,
                                             @Param("olderThan") Instant olderThan, Limit limit);

    /** 장소의 공개 가능한(승인 LETTER) 리뷰 본문, 최신순. 2차 판정·알림 문구용. */
    @Query("select m.content from Memory m where m.placeId = :placeId"
            + " and m.contentStatus = team4.emotionmap.contracts.memory.ContentStatus.ACTIVE"
            + " and m.distributionType = team4.emotionmap.contracts.memory.DistributionType.LETTER"
            + " and m.moderationStatus = team4.emotionmap.contracts.memory.ModerationStatus.APPROVED"
            + " and m.originKind = team4.emotionmap.contracts.memory.OriginKind.DIRECT order by m.createdAt desc")
    List<String> findApprovedLetterContents(@Param("placeId") UUID placeId, Limit limit);
}
