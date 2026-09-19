package team4.emotionmap.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import team4.emotionmap.contracts.ai.MatchReasonPort;
import team4.emotionmap.contracts.ai.MatchReasonRequest;
import team4.emotionmap.contracts.ai.MatchReasonResult;
import team4.emotionmap.contracts.ai.PreferenceVerifyPort;
import team4.emotionmap.contracts.ai.VerifyRequest;
import team4.emotionmap.contracts.ai.VerifyResult;
import team4.emotionmap.contracts.config.MatchingProperties;
import team4.emotionmap.contracts.dictionary.VibeVector;
import team4.emotionmap.contracts.events.MemoryPublishedEvent;
import team4.emotionmap.contracts.geo.DistanceMeters;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.contracts.time.ServiceTime;
import team4.emotionmap.memory.MemoryRepository;
import team4.emotionmap.place.Place;
import team4.emotionmap.place.PlaceRepository;

/**
 * 기획 §7 자동 알림 매칭. 새 리뷰가 승인돼 장소 벡터가 갱신되면({@link MemoryPublishedEvent}, 커밋 후) 저장된 활성 취향을 대상으로:
 * <ol>
 *   <li>후보 필터: 기준점 반경(1km, 반경 안 장소가 3개 미만이면 3km) AND 카테고리 필터(선택 시). 본인 리뷰 제외.</li>
 *   <li>1차(BE): s=(cos+1)/2. s≥0.85 알림, 0.65≤s&lt;0.85 2차, 그 외 탈락. 카테고리는 필터에만 쓰고 점수에는 안 쓴다.</li>
 *   <li>2차(AI): 취향 문장 + 장소 리뷰 상위 3~5개 → fit 이면 알림(reason 을 문구로). 취향 문장이 없으면 탈락.</li>
 *   <li>같은 장소 하루(KST) 1회. 알림 문구는 1차 통과 시 AI(match-reason), 실패 시 템플릿.</li>
 * </ol>
 * AI 호출은 트랜잭션 밖에서 하고 알림 INSERT 만 <b>새 트랜잭션(REQUIRES_NEW)</b>으로 짧게 커밋한다 —
 * AFTER_COMMIT 리스너 안에서 원래 트랜잭션에 참여하면 쓰기가 커밋되지 않기 때문이다.
 */
@Slf4j
@Service
public class MatchingService {

    private final PreferenceRepository preferenceRepository;
    private final NotificationRepository notificationRepository;
    private final PlaceRepository placeRepository;
    private final MemoryRepository memoryRepository;
    private final PreferenceVerifyPort verifyPort;
    private final MatchReasonPort reasonPort;
    private final MatchingProperties props;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    public MatchingService(PreferenceRepository preferenceRepository, NotificationRepository notificationRepository,
                           PlaceRepository placeRepository, MemoryRepository memoryRepository,
                           PreferenceVerifyPort verifyPort, MatchReasonPort reasonPort,
                           MatchingProperties props, Clock clock, PlatformTransactionManager transactionManager) {
        this.preferenceRepository = preferenceRepository;
        this.notificationRepository = notificationRepository;
        this.placeRepository = placeRepository;
        this.memoryRepository = memoryRepository;
        this.verifyPort = verifyPort;
        this.reasonPort = reasonPort;
        this.props = props;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemoryPublished(MemoryPublishedEvent event) {
        try {
            match(event);
        } catch (RuntimeException e) {
            log.warn("matching failed memory={} : {}", event.memoryId(), e.getClass().getSimpleName(), e);
        }
    }

    /** 반환값 = 생성된 알림 수. 테스트·운영 재실행용으로 공개한다. */
    public int match(MemoryPublishedEvent event) {
        Place place = placeRepository.findById(event.placeId()).orElse(null);
        if (place == null || place.vibe() == null || place.vibe().isZero()) {
            return 0;
        }
        VibeVector placeVector = place.vibe();
        GeoPoint placePoint = new GeoPoint(place.getLat(), place.getLng());
        List<String> reviews = memoryRepository.findApprovedLetterContents(place.getId(), Limit.of(props.reviewsForVerify()));
        if (reviews.isEmpty()) {
            return 0;
        }
        Instant now = clock.instant();
        LocalDate serviceDate = ServiceTime.serviceDate(now);
        MatchScorer scorer = new MatchScorer(props);
        Duration timeout = Duration.ofSeconds(8);
        int created = 0;

        for (Preference pref : preferenceRepository.findByActiveTrueAndUserIdNot(event.ownerId())) {
            if (!pref.acceptsCategory(place.category())) {
                continue;
            }
            double radius = effectiveRadius(pref);
            double distance = DistanceMeters.between(pref.center(), placePoint);
            if (distance > radius) {
                continue;
            }
            if (notificationRepository.existsByPreferenceIdAndPlaceIdAndServiceDate(pref.getId(), place.getId(), serviceDate)) {
                continue;
            }
            MatchScorer.Stage1 stage1 = scorer.evaluate(pref.vector(), placeVector, distance, radius);
            String reason;
            short stage;
            switch (stage1.decision()) {
                case NOTIFY -> {
                    stage = 1;
                    reason = explain(pref, place, reviews, timeout);
                }
                case VERIFY -> {
                    if (!pref.hasPreferenceText()) {
                        continue;
                    }
                    VerifyResult verified = verifyPort.verify(new VerifyRequest(pref.getPreferenceText(), reviews, timeout));
                    if (verified.failed() || !verified.fit()) {
                        continue;
                    }
                    stage = 2;
                    reason = verified.reason() == null || verified.reason().isBlank()
                            ? templateReason(pref, place, reviews.size()) : verified.reason();
                }
                default -> {
                    continue;
                }
            }
            if (saveNotification(pref, place, event.memoryId(), serviceDate, stage1, stage, reason, now)) {
                created++;
            }
        }
        log.info("matching done memory={} place={} notifications={}", event.memoryId(), place.getId(), created);
        return created;
    }

    /** 기획 §7 "반경 1km (3개 미만이면 3km)": 기준 반경 안에 리뷰 있는 장소가 기준 수 미만이면 대체 반경. */
    double effectiveRadius(Preference pref) {
        // 취향에 저장된 반경이 기본 반경이다(생성 시 설정 기본값 1km 로 채워진다).
        final double primary = pref.getRadiusM();
        GeoPoint center = pref.center();
        long within = placeRepository.findAllInBox(
                        center.lat() - degLat(primary), center.lat() + degLat(primary),
                        center.lng() - degLng(primary, center.lat()), center.lng() + degLng(primary, center.lat()))
                .stream()
                .filter(p -> p.getReviewCount() != null && p.getReviewCount() > 0)
                .filter(p -> DistanceMeters.between(center, new GeoPoint(p.getLat(), p.getLng())) <= primary)
                .count();
        return within < props.minPlacesForPrimaryRadius() ? Math.max(primary, props.fallbackRadiusMeters()) : primary;
    }

    private String explain(Preference pref, Place place, List<String> reviews, Duration timeout) {
        MatchReasonResult result;
        try {
            result = reasonPort.explain(new MatchReasonRequest(pref.getCards(), place.displayName(), reviews, timeout));
        } catch (RuntimeException e) {
            result = MatchReasonResult.unavailable();
        }
        return result.failed() ? templateReason(pref, place, reviews.size()) : result.reason();
    }

    static String templateReason(Preference pref, Place place, int reviewCount) {
        String name = place.displayName() == null ? "근처 장소" : place.displayName();
        return String.join(", ", pref.getCards()) + " 곳을 찾는 당신에게 — " + name + "의 리뷰 " + reviewCount
                + "개가 그 분위기를 이야기해요.";
    }

    boolean saveNotification(Preference pref, Place place, UUID memoryId, LocalDate serviceDate,
                             MatchScorer.Stage1 stage1, short stage, String reason, Instant now) {
        try {
            newTransaction.executeWithoutResult(status -> notificationRepository.save(Notification.builder()
                    .userId(pref.getUserId()).preferenceId(pref.getId()).placeId(place.getId()).memoryId(memoryId)
                    .serviceDate(serviceDate).similarity(stage1.similarity()).score(stage1.score())
                    .stage(stage).reason(reason).createdAt(now).build()));
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false; // 동시 실행으로 같은 날 같은 장소 알림이 이미 생겼다.
        }
    }

    private static double degLat(double meters) {
        return meters / 111_320.0;
    }

    private static double degLng(double meters, double lat) {
        return meters / (111_320.0 * Math.max(0.01, Math.cos(Math.toRadians(lat))));
    }
}
