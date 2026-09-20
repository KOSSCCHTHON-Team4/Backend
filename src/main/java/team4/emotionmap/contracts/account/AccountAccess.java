package team4.emotionmap.contracts.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import team4.emotionmap.contracts.geo.GeoPoint;

/**
 * 계정의 <b>현재</b> 접근 상태 스냅샷. 토큰 발급 시점 상태가 아니라 조회 시점 DB 값이다.
 *
 * @param userId           내부 계정 ID
 * @param status           현재 접근 상태
 * @param mailboxEnabledAt 온보딩 완료·수신 시작 시각. 온보딩 전 null
 * @param mailbox          현재 수신 위치. 온보딩 전 null, 온보딩 후 이동할 수 있음
 */
public record AccountAccess(UUID userId, AccountStatus status, Instant mailboxEnabledAt, GeoPoint mailbox) {

    public AccountAccess {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(status, "status");
        if ((mailboxEnabledAt == null) != (mailbox == null)) {
            throw new IllegalArgumentException("mailboxEnabledAt and mailbox must be both present or both absent");
        }
    }

    /** hasOnboarded 는 위치+최초 취향 버전의 원자적 완료에서 파생한다(API_SPEC 3.3). */
    public boolean hasOnboarded() {
        return mailboxEnabledAt != null;
    }
}
