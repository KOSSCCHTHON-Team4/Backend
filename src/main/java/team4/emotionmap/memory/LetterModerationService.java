package team4.emotionmap.memory;

import java.io.InputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import team4.emotionmap.contracts.ai.ModerationPort;
import team4.emotionmap.contracts.ai.ModerationRequest;
import team4.emotionmap.contracts.ai.ModerationResult;
import team4.emotionmap.contracts.events.MemoryPublishedEvent;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.media.LocalImageStore;
import team4.emotionmap.contracts.media.StoredImageMeta;
import team4.emotionmap.contracts.media.SanitizedImage;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.ModerationStatus;
import team4.emotionmap.contracts.time.SelectionPublicationBarrier;
import team4.emotionmap.memory.ai.AiProperties;

/**
 * A07 배달 전 안전 검사(기획 §1 "안전 판정"). 직접 LETTER 를 저장한 뒤 본문(+사진)을 ModerationPort 로 검사하고
 * 결과를 저장한다. APPROVED 면 최초 available_at 을 확정하고 장소 프로필을 다시 계산한 뒤
 * {@link MemoryPublishedEvent} 를 발행한다(→ notification 매칭). PRIVATE 는 검사하지 않는다.
 *
 * <p>AI 호출은 트랜잭션 밖에서 하고 상태 저장만 짧은 트랜잭션으로 한다. 상류 오류(ERROR)·프로세스 중단으로 PENDING 이
 * 남지 않도록 {@link #retryPending()} 이 DB 상태 기반으로 주기 재시도한다.
 */
@Slf4j
@Service
public class LetterModerationService {

    private final MemoryRepository memoryRepository;
    private final ModerationPort moderationPort;
    private final LocalImageStore localImageStore;
    private final PlaceProfileService placeProfileService;
    private final ApplicationEventPublisher events;
    private final AiProperties aiProperties;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final SelectionPublicationBarrier publicationBarrier;

    public LetterModerationService(MemoryRepository memoryRepository, ModerationPort moderationPort,
                                   LocalImageStore localImageStore, PlaceProfileService placeProfileService,
                                   ApplicationEventPublisher events, AiProperties aiProperties, Clock clock,
                                   PlatformTransactionManager transactionManager,
                                   SelectionPublicationBarrier publicationBarrier) {
        this.memoryRepository = memoryRepository;
        this.moderationPort = moderationPort;
        this.localImageStore = localImageStore;
        this.placeProfileService = placeProfileService;
        this.events = events;
        this.aiProperties = aiProperties;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.publicationBarrier = publicationBarrier;
    }

    /** 요청 스레드에서 생성 커밋 직후 호출. 실패해도 생성 201 은 이미 확정이다(배달 성공 ≠ 생성 성공). */
    public void moderate(UUID memoryId) {
        Optional<Memory> loaded = memoryRepository.findById(memoryId);
        if (loaded.isEmpty()) {
            return;
        }
        Memory memory = loaded.get();
        if (memory.getDistributionType() != DistributionType.LETTER
                || memory.getContentStatus() != ContentStatus.ACTIVE
                || !(memory.getModerationStatus() == ModerationStatus.PENDING
                    || memory.getModerationStatus() == ModerationStatus.ERROR)) {
            return;
        }
        Duration timeout = aiProperties.timeout() == null ? Duration.ofSeconds(8) : aiProperties.timeout();
        ModerationResult result;
        try {
            result = moderationPort.moderate(new ModerationRequest(memory.getContent(), imageOf(memory), timeout));
        } catch (RuntimeException e) {
            // 우리 쪽 어댑터/저장소 장애: 배달 차단 상태(ERROR)로 두고 재시도에 맡긴다.
            log.warn("moderation adapter failure memory={} : {}", memoryId, e.getClass().getSimpleName());
            result = null;
        }
        ModerationStatus verdict = result == null ? ModerationStatus.ERROR : result.verdict().toStatus();
        apply(memoryId, verdict);
    }

    /** 상태 저장은 짧은 트랜잭션. 같은 빈 안의 자기 호출이라 애노테이션 프록시 대신 TransactionTemplate 을 쓴다. */
    void apply(UUID memoryId, ModerationStatus verdict) {
        transactionTemplate.executeWithoutResult(status -> {
            publicationBarrier.lock();
            Memory memory = memoryRepository.findByIdForUpdate(memoryId).orElse(null);
            if (memory == null || memory.getDistributionType() != DistributionType.LETTER
                    || memory.getModerationStatus() == ModerationStatus.APPROVED) {
                return; // 이미 승인됐으면 최초 시각을 건드리지 않는다.
            }
            memory.applyModeration(verdict, publicationBarrier.publicationTime());
            log.info("moderation applied memory={} verdict={} availableAt={}", memoryId, verdict, memory.getAvailableAt());
            if (verdict == ModerationStatus.APPROVED) {
                placeProfileService.recompute(memory.getPlaceId());
                // AFTER_COMMIT 리스너(notification.MatchingService)가 받는다.
                events.publishEvent(new MemoryPublishedEvent(memory.getId(), memory.getPlaceId(), memory.getOwnerId()));
            }
        });
    }

    /** DB 상태 기반 재시도: PENDING/ERROR 로 남은 LETTER 를 오래된 것부터 다시 검사한다. */
    @Scheduled(fixedDelayString = "${app.moderation.retry-delay:PT5M}", initialDelayString = "${app.moderation.retry-initial-delay:PT1M}")
    public void retryPending() {
        Instant olderThan = clock.instant().minus(Duration.ofSeconds(30));
        List<UUID> ids = memoryRepository.findModerationRetryCandidates(
                List.of(ModerationStatus.PENDING, ModerationStatus.ERROR), olderThan, org.springframework.data.domain.Limit.of(20));
        for (UUID id : ids) {
            try {
                moderate(id);
            } catch (RuntimeException e) {
                log.warn("moderation retry failed memory={} : {}", id, e.getClass().getSimpleName());
            }
        }
    }

    /** 저장된(이미 살균된) 사진을 다시 읽어 AI 에 전달한다. 읽기 실패는 사진 없음으로 축소하지 않고 예외로 올린다. */
    private SanitizedImage imageOf(Memory memory) {
        if (memory.getImagePath() == null) {
            return null;
        }
        StoredImageMeta metadata = localImageStore.describe(memory.getImagePath())
                .orElseThrow(() -> ContractError.of(ErrorCode.IMAGE_FILE_UNAVAILABLE));
        try (InputStream input = localImageStore.open(memory.getImagePath())) {
            byte[] bytes = input.readAllBytes();
            if (bytes.length != metadata.sizeBytes()) {
                throw ContractError.of(ErrorCode.IMAGE_FILE_UNAVAILABLE);
            }
            return new SanitizedImage(bytes, metadata.mediaType(), metadata.width(), metadata.height());
        } catch (IOException ignored) {
            throw ContractError.of(ErrorCode.IMAGE_FILE_UNAVAILABLE);
        }
    }
}
