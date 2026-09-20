package team4.emotionmap.letter;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.dictionary.Atmospheres;

/**
 * 일일 선정 슬롯({@code daily_selections})·배달({@code letter_deliveries})의 SQL 접근.
 *
 * <p>슬롯은 ERD 8.3 의 claim 규칙을 따른다: {@code PROCESSING} 이면 claim_token·lease 가 있고, 끝난 상태
 * (DELIVERED·NO_CANDIDATE·EXPIRED_ERROR·SKIPPED_ACCESS) 만 completed_at 을 가진다. 모든 상태 전이는
 * {@code claim_token} 이 일치할 때만 일어나 다른 작업자가 가져간 슬롯을 덮어쓰지 않는다. 시각은 DB
 * {@code clock_timestamp()} 를 써서 {@code ck_delivery_date} 와 같은 기준으로 판정한다.
 */
@Component
@RequiredArgsConstructor
class DailySelectionStore {

    /** 이미 확정됐거나 다른 작업자가 잡고 있는 슬롯은 제외하고, 처리할 수 있는 사용자만. */
    private static final String USERS_TO_PROCESS = """
            SELECT u.id FROM app_users u
            LEFT JOIN daily_selections d ON d.user_id = u.id AND d.service_date = :date
            WHERE u.access_status = 'ACTIVE'
              AND u.mailbox_enabled_at IS NOT NULL AND u.mailbox_enabled_at <= :cutoff
              AND (d.user_id IS NULL
                   OR d.status IN ('PENDING', 'RETRYABLE_ERROR')
                   OR (d.status = 'PROCESSING' AND d.lease_expires_at < clock_timestamp()))
            ORDER BY u.id
            """;

    /** MVP_PLAN 6.2 후보 조건 중 SQL 로 거를 수 있는 것. 반경은 {@code DistanceMeters} 로 Java 에서 확인한다. */
    private static final String CANDIDATES = """
            SELECT m.id, m.content, m.crowd_level, m.spatial_feel, m.company_fit, m.stay_style,
                   m.place_lat, m.place_lng
            FROM memories m
            JOIN app_users u ON u.id = :userId
            WHERE m.distribution_type = 'LETTER'
              AND m.content_status = 'ACTIVE'
              AND m.moderation_status = 'APPROVED'
              AND m.available_at IS NOT NULL
              AND m.available_at >= u.mailbox_enabled_at
              AND m.available_at <= :cutoff
              AND m.owner_id <> :userId
              AND m.place_lat IS NOT NULL AND m.place_lng IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM letter_deliveries ld
                              WHERE ld.receiver_id = :userId AND ld.memory_id = m.id)
            """;

    private final NamedParameterJdbcTemplate jdbc;

    record Claim(UUID userId, LocalDate serviceDate, UUID token, UUID preferenceVersionId,
                 int radiusMeters, String ruleVersion, UUID randomSeed) {
    }

    record CandidateRow(UUID memoryId, String content, Atmospheres atmospheres, double lat, double lng) {
    }

    record Mailbox(double lat, double lng) {
    }

    /** 지난 날짜에 끝나지 못한 슬롯은 EXPIRED_ERROR 로 닫는다(자정 이후 전날 배달을 만들지 않음). */
    int expirePastSlots(LocalDate today) {
        return jdbc.update("""
                UPDATE daily_selections
                SET status = 'EXPIRED_ERROR', claim_token = NULL, lease_expires_at = NULL,
                    completed_at = clock_timestamp()
                WHERE service_date < :today AND status IN ('PENDING', 'PROCESSING', 'RETRYABLE_ERROR')
                """, Map.of("today", today));
    }

    List<UUID> usersToProcess(LocalDate date, Instant cutoff) {
        return jdbc.queryForList(USERS_TO_PROCESS,
                new MapSqlParameterSource().addValue("date", date).addValue("cutoff", ts(cutoff)), UUID.class);
    }

    /**
     * 슬롯을 만들거나(처음) 이어받아(RETRYABLE·PENDING·lease 만료) PROCESSING 으로 잡는다. 이미 끝났거나 다른
     * 작업자가 잡고 있으면 empty. 재시도는 처음 고정한 취향 버전·반경·규칙을 그대로 쓴다(ERD 6.1).
     */
    Optional<Claim> claim(UUID userId, LocalDate date, Instant cutoff, UUID preferenceVersionId,
                          int radiusMeters, String ruleVersion, Duration lease) {
        UUID token = UUID.randomUUID();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId).addValue("date", date).addValue("cutoff", ts(cutoff))
                .addValue("prefId", preferenceVersionId).addValue("radius", radiusMeters)
                .addValue("rule", ruleVersion).addValue("token", token)
                .addValue("leaseSecs", (double) lease.toMillis() / 1000.0);
        List<Claim> inserted = jdbc.query("""
                INSERT INTO daily_selections (user_id, service_date, cutoff_at, preference_version_id, radius_m,
                    rule_version, status, attempt_count, claim_token, lease_expires_at, last_attempt_at)
                VALUES (:userId, :date, :cutoff, :prefId, :radius, :rule, 'PROCESSING', 1, :token,
                    clock_timestamp() + make_interval(secs => :leaseSecs), clock_timestamp())
                ON CONFLICT (user_id, service_date) DO NOTHING
                RETURNING preference_version_id, radius_m, rule_version, random_seed
                """, params, (rs, i) -> new Claim(userId, date, token, rs.getObject(1, UUID.class),
                rs.getInt(2), rs.getString(3), rs.getObject(4, UUID.class)));
        if (!inserted.isEmpty()) {
            return Optional.of(inserted.getFirst());
        }
        List<Claim> resumed = jdbc.query("""
                UPDATE daily_selections
                SET status = 'PROCESSING', claim_token = :token,
                    lease_expires_at = clock_timestamp() + make_interval(secs => :leaseSecs),
                    attempt_count = attempt_count + 1, last_attempt_at = clock_timestamp()
                WHERE user_id = :userId AND service_date = :date
                  AND (status IN ('PENDING', 'RETRYABLE_ERROR')
                       OR (status = 'PROCESSING' AND lease_expires_at < clock_timestamp()))
                RETURNING preference_version_id, radius_m, rule_version, random_seed
                """, params, (rs, i) -> new Claim(userId, date, token, rs.getObject(1, UUID.class),
                rs.getInt(2), rs.getString(3), rs.getObject(4, UUID.class)));
        return resumed.stream().findFirst();
    }

    Optional<Mailbox> mailbox(UUID userId) {
        return jdbc.query("SELECT mailbox_lat, mailbox_lng FROM app_users WHERE id = :userId AND mailbox_lat IS NOT NULL",
                Map.of("userId", userId), (rs, i) -> new Mailbox(rs.getDouble(1), rs.getDouble(2))).stream().findFirst();
    }

    List<CandidateRow> candidates(UUID userId, Instant cutoff) {
        return jdbc.query(CANDIDATES,
                new MapSqlParameterSource().addValue("userId", userId).addValue("cutoff", ts(cutoff)),
                (rs, i) -> new CandidateRow(rs.getObject("id", UUID.class), rs.getString("content"),
                        new Atmospheres(rs.getDouble("crowd_level"), rs.getDouble("spatial_feel"),
                                rs.getDouble("company_fit"), rs.getDouble("stay_style")),
                        rs.getDouble("place_lat"), rs.getDouble("place_lng")));
    }

    // ------------------------------------------------------------ 최종 트랜잭션 안에서만 호출

    /** 슬롯 행을 잠그고 아직 이 작업자의 claim 인지 확인한다. */
    boolean lockOwnedSlot(Claim claim) {
        List<Boolean> owned = jdbc.query("""
                SELECT status = 'PROCESSING' AND claim_token = :token
                FROM daily_selections WHERE user_id = :userId AND service_date = :date FOR UPDATE
                """, slotParams(claim), (rs, i) -> rs.getBoolean(1));
        return !owned.isEmpty() && owned.getFirst();
    }

    /** DB 시각 기준 서비스 날짜가 아직 이 슬롯의 날짜인지(자정을 넘긴 늦은 확정 방지). */
    boolean stillServiceDate(LocalDate date) {
        Boolean same = jdbc.queryForObject(
                "SELECT (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date = :date",
                Map.of("date", date), Boolean.class);
        return Boolean.TRUE.equals(same);
    }

    boolean deliveryExists(UUID userId, LocalDate date, UUID memoryId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM letter_deliveries
                               WHERE receiver_id = :userId AND (service_date = :date OR memory_id = :memoryId))
                """, new MapSqlParameterSource().addValue("userId", userId).addValue("date", date)
                .addValue("memoryId", memoryId), Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    /** 배달 행 INSERT 와 DELIVERED 전이를 같은 트랜잭션에서(MVP_PLAN 11.2). delivered_at 은 DB 기본값. */
    void deliver(Claim claim, DailyPicker.Pick pick) {
        jdbc.update("INSERT INTO letter_deliveries (receiver_id, service_date, memory_id) VALUES (:userId, :date, :memoryId)",
                slotParams(claim).addValue("memoryId", pick.memoryId()));
        int updated = jdbc.update("""
                UPDATE daily_selections
                SET status = 'DELIVERED', claim_token = NULL, lease_expires_at = NULL, last_error_code = NULL,
                    candidate_count = :candidateCount, top_tie_count = :topTieCount, fixed_score = :fixedScore,
                    tie_break_method = :method, completed_at = clock_timestamp()
                WHERE user_id = :userId AND service_date = :date AND claim_token = :token
                """, slotParams(claim).addValue("candidateCount", pick.candidateCount())
                .addValue("topTieCount", pick.topTieCount()).addValue("fixedScore", pick.fixedScore())
                .addValue("method", pick.method().name()));
        if (updated != 1) {
            throw new IllegalStateException("daily selection claim lost before delivery");
        }
    }

    // ------------------------------------------------------------ 짧은 단독 전이 (claim 일치 시에만)

    boolean finishNoCandidate(Claim claim) {
        return jdbc.update("""
                UPDATE daily_selections
                SET status = 'NO_CANDIDATE', claim_token = NULL, lease_expires_at = NULL, last_error_code = NULL,
                    candidate_count = 0, top_tie_count = 0, fixed_score = NULL, tie_break_method = NULL,
                    completed_at = clock_timestamp()
                WHERE user_id = :userId AND service_date = :date AND claim_token = :token
                """, slotParams(claim)) == 1;
    }

    boolean skipAccess(Claim claim) {
        return jdbc.update("""
                UPDATE daily_selections
                SET status = 'SKIPPED_ACCESS', claim_token = NULL, lease_expires_at = NULL,
                    completed_at = clock_timestamp()
                WHERE user_id = :userId AND service_date = :date AND claim_token = :token
                """, slotParams(claim)) == 1;
    }

    /** 같은 날 다시 시도할 수 있는 실패. completed_at 은 비워 둔다(ck_daily_completed). */
    boolean markRetryable(Claim claim, String errorCode) {
        return jdbc.update("""
                UPDATE daily_selections
                SET status = 'RETRYABLE_ERROR', claim_token = NULL, lease_expires_at = NULL,
                    last_error_code = :code
                WHERE user_id = :userId AND service_date = :date AND claim_token = :token
                """, slotParams(claim).addValue("code", errorCode)) == 1;
    }

    private static MapSqlParameterSource slotParams(Claim claim) {
        return new MapSqlParameterSource().addValue("userId", claim.userId())
                .addValue("date", claim.serviceDate()).addValue("token", claim.token());
    }

    private static OffsetDateTime ts(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
