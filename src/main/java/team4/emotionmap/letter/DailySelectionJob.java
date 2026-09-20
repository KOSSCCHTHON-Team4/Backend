package team4.emotionmap.letter;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import team4.emotionmap.account.User;
import team4.emotionmap.account.UserRepository;
import team4.emotionmap.contracts.account.PreferenceHistoryReader;
import team4.emotionmap.contracts.account.PreferenceVersionSnapshot;
import team4.emotionmap.contracts.ai.PreferenceTieBreakPort;
import team4.emotionmap.contracts.config.SelectionConfigHistory;
import team4.emotionmap.contracts.geo.DistanceMeters;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.contracts.time.SelectionPublicationBarrier;
import team4.emotionmap.contracts.time.ServiceTime;
import team4.emotionmap.memory.Memory;
import team4.emotionmap.memory.MemoryRepository;

/**
 * B02 매일 09:00(Asia/Seoul) 일일 편지 선정·배달(MVP_PLAN 6.1~6.5, ERD 8.3 의 시연용 핵심).
 *
 * <p>흐름: ① fence 잠금·cutoff 봉인 후 수신 대상을 읽는다 ② 사용자마다 같은 writable READ COMMITTED
 * 트랜잭션에서 fence를 봉인하고 설정·취향·후보를 읽으며 슬롯을 claim한다 ③ 커밋 뒤 점수·AI 동률 비교
 * ④ 짧은 최종 트랜잭션에서 계정·후보·claim·날짜를 다시 확인하고 배달 INSERT 와 DELIVERED 를 함께 커밋.
 *
 * <p>09:00 cron 외에 주기 실행(기본 5분)으로 서버가 늦게 떴거나 당일 재시도(RETRYABLE_ERROR·만료 lease)가 필요한
 * 슬롯을 같은 날 안에서만 다시 처리한다. cutoff 는 늦게 돌아도 09:00 로 고정이고, 09:00 이후 가입자·새 후보를
 * 당일 보충하지 않는다(사용자 조건 {@code mailbox_enabled_at <= cutoff}, 후보 조건 {@code available_at <= cutoff}).
 *
 * <p>GET /v1/letters/today 는 별도 구현 대상이다.
 */
@Slf4j
@Service
public class DailySelectionJob {

    private final DailySelectionStore store;
    private final PreferenceHistoryReader preferences;
    private final SelectionConfigHistory configHistory;
    private final SelectionPublicationBarrier barrier;
    private final UserRepository userRepository;
    private final MemoryRepository memoryRepository;
    private final DailyPicker picker;
    private final TransactionTemplate readCommitted;
    private final Clock clock;
    private final boolean enabled;
    private final Duration lease;
    private final ReentrantLock running = new ReentrantLock();
    private volatile LocalDate warnedMissingConfig;

    public DailySelectionJob(DailySelectionStore store,
                             PreferenceHistoryReader preferences,
                             SelectionConfigHistory configHistory,
                             SelectionPublicationBarrier barrier,
                             UserRepository userRepository,
                             MemoryRepository memoryRepository,
                             PreferenceTieBreakPort tieBreak,
                             PlatformTransactionManager transactionManager,
                             Clock clock,
                             @Value("${app.daily-selection.enabled:true}") boolean enabled,
                             @Value("${app.daily-selection.lease:PT2M}") Duration lease,
                             @Value("${app.daily-selection.tiebreak-timeout:PT15S}") Duration tieBreakTimeout) {
        this.store = store;
        this.preferences = preferences;
        this.configHistory = configHistory;
        this.barrier = barrier;
        this.userRepository = userRepository;
        this.memoryRepository = memoryRepository;
        this.picker = new DailyPicker(tieBreak, tieBreakTimeout);
        this.readCommitted = new TransactionTemplate(transactionManager);
        this.readCommitted.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.clock = clock;
        this.enabled = enabled;
        this.lease = lease;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void atCutoff() {
        runToday();
    }

    /** 늦은 기동·당일 재시도용. 오늘 cutoff 이전이면 아무것도 하지 않는다. */
    @Scheduled(fixedDelayString = "${app.daily-selection.catch-up-delay:PT5M}",
            initialDelayString = "${app.daily-selection.initial-delay:PT1M}")
    public void catchUp() {
        runToday();
    }

    /** 오늘 서비스 날짜의 선정을 한 번 돈다. 같은 프로세스에서 겹쳐 돌지 않는다. 처리한 사용자 수를 돌려준다. */
    public int runToday() {
        if (!enabled || !running.tryLock()) {
            return 0;
        }
        try {
            Instant now = clock.instant();
            LocalDate date = ServiceTime.serviceDate(now);
            Instant cutoff = ServiceTime.cutoff(date);
            if (now.isBefore(cutoff)) {
                return 0;
            }
            int expired = store.expirePastSlots(date);
            if (expired > 0) {
                log.info("daily selection expired {} unfinished slot(s) before {}", expired, date);
            }
            List<UUID> users;
            try {
                users = readCommitted.execute(status -> {
                    barrier.lock();
                    barrier.sealCutoff(date);
                    return store.usersToProcess(date, cutoff);
                });
            } catch (IllegalStateException e) {
                // 예: DB 시계가 아직 09:00 전(앱·DB 시계 차이). 다음 주기에 다시 시도한다.
                log.info("daily selection not ready for {}: {}", date, e.getMessage());
                return 0;
            }
            int processed = 0;
            for (UUID userId : users) {
                try {
                    processUser(userId, date, cutoff);
                    processed++;
                } catch (RuntimeException e) {
                    log.warn("daily selection failed user={} date={} : {}", userId, date, e.getClass().getSimpleName());
                }
            }
            if (processed > 0) {
                log.info("daily selection {} processed {} user(s)", date, processed);
            }
            return processed;
        } finally {
            running.unlock();
        }
    }

    void processUser(UUID userId, LocalDate date, Instant cutoff) {
        SelectionInput input = readCommitted.execute(status -> {
            barrier.lock();
            barrier.sealCutoff(date);
            Optional<SelectionConfigHistory.Snapshot> config = configHistory.findAt(cutoff);
            if (config.isEmpty()) {
                if (!date.equals(warnedMissingConfig)) {
                    log.warn("daily selection skipped: no selection_config_versions row effective at {} ({})", cutoff, date);
                    warnedMissingConfig = date;
                }
                return null;
            }
            Optional<PreferenceVersionSnapshot> atCutoff = preferences.findAt(userId, cutoff);
            if (atCutoff.isEmpty()) {
                return null; // cutoff 이전 취향이 없으면 슬롯을 만들지 않는다
            }
            Optional<DailySelectionStore.Claim> claimed = store.claim(userId, date, cutoff, atCutoff.get().versionId(),
                    config.get().radiusMeters(), config.get().ruleVersion(), lease);
            if (claimed.isEmpty()) {
                return null; // 이미 끝났거나 다른 작업자가 처리 중
            }
            DailySelectionStore.Claim claim = claimed.get();
            // 재시도에서도 처음 고정한 취향 버전과 설정을 유지한다.
            Optional<PreferenceVersionSnapshot> pinned = preferences.findVersion(userId, claim.preferenceVersionId());
            if (pinned.isEmpty()) {
                store.markRetryable(claim, "PREFERENCE_VERSION_MISSING");
                return null;
            }
            PreferenceVersionSnapshot pinnedSnapshot = pinned.get();
            return new SelectionInput(claim, pinnedSnapshot,
                    store.candidates(userId, cutoff, pinnedSnapshot.mailboxEnabledAt()));
        });
        if (input == null) {
            return;
        }
        try {
            select(input, cutoff);
        } catch (RuntimeException e) {
            store.markRetryable(input.claim(), "UNEXPECTED_" + e.getClass().getSimpleName());
            throw e;
        }
    }

    private record SelectionInput(DailySelectionStore.Claim claim, PreferenceVersionSnapshot preference,
                                  List<DailySelectionStore.CandidateRow> candidates) {
    }

    private void select(SelectionInput input, Instant cutoff) {
        DailySelectionStore.Claim claim = input.claim();
        PreferenceVersionSnapshot pinned = input.preference();
        GeoPoint home = pinned.mailbox();
        List<DailyPicker.Candidate> candidates = new ArrayList<>();
        for (DailySelectionStore.CandidateRow row : input.candidates()) {
            if (DistanceMeters.between(home, new GeoPoint(row.lat(), row.lng())) <= claim.radiusMeters()) {
                candidates.add(new DailyPicker.Candidate(row.memoryId(), row.content(), row.atmospheres()));
            }
        }
        Optional<DailyPicker.Pick> pick = picker.pick(pinned.atmospheres(), pinned.description(),
                candidates, claim.randomSeed());
        if (pick.isEmpty()) {
            store.finishNoCandidate(claim);
            return;
        }
        if ("atmosphere-v1".equals(claim.ruleVersion()) && pick.get().fixedScore() % 1.0 != 0.0) {
            // v1 슬롯은 정수 점수만 저장할 수 있다(ck_daily_v1_fixed_score_integral). 연속 축은 atmosphere-v2 설정 필요.
            store.markRetryable(claim, "RULE_V1_NON_INTEGRAL_SCORE");
            return;
        }
        finish(claim, pinned, cutoff, pick.get());
    }

    /** 잠금 순서: 사용자 → 경험 → 슬롯 → 배달 (좋아요 흐름과 같은 순서). */
    private void finish(DailySelectionStore.Claim claim, PreferenceVersionSnapshot pinned, Instant cutoff,
                        DailyPicker.Pick pick) {
        readCommitted.executeWithoutResult(status -> {
            User user = userRepository.findByIdForUpdate(claim.userId()).orElse(null);
            if (user == null || !user.isActive()) {
                store.skipAccess(claim);
                return;
            }
            Memory memory = memoryRepository.findByIdForUpdate(pick.memoryId()).orElse(null);
            if (!stillEligible(memory, user, pinned, claim, cutoff)) {
                store.markRetryable(claim, "CANDIDATE_CHANGED"); // 다음 주기에 다시 계산
                return;
            }
            if (!store.lockOwnedSlot(claim)) {
                return; // claim 을 잃음(다른 작업자)
            }
            if (!store.stillServiceDate(claim.serviceDate())) {
                status.setRollbackOnly(); // 자정을 넘김: 전날 배달을 만들지 않는다. 다음 실행에서 EXPIRED_ERROR
                return;
            }
            if (store.deliveryExists(claim.userId(), claim.serviceDate(), pick.memoryId())) {
                store.markRetryable(claim, "DELIVERY_CONFLICT");
                return;
            }
            store.deliver(claim, pick);
            log.info("daily selection delivered user={} date={} method={} candidates={} ties={}",
                    claim.userId(), claim.serviceDate(), pick.method(), pick.candidateCount(), pick.topTieCount());
        });
    }

    private static boolean stillEligible(Memory memory, User user, PreferenceVersionSnapshot pinned,
                                         DailySelectionStore.Claim claim, Instant cutoff) {
        if (memory == null || !memory.isDeliverable() || memory.getOwnerId().equals(user.getId())
                || memory.getPlaceLat() == null || memory.getPlaceLng() == null
                || user.getMailboxLat() == null || user.getMailboxLng() == null || user.getMailboxEnabledAt() == null) {
            return false;
        }
        Instant availableAt = memory.getAvailableAt();
        if (availableAt.isBefore(pinned.mailboxEnabledAt()) || availableAt.isAfter(cutoff)) {
            return false;
        }
        double distance = DistanceMeters.between(pinned.mailbox(),
                new GeoPoint(memory.getPlaceLat(), memory.getPlaceLng()));
        return distance <= claim.radiusMeters();
    }
}
