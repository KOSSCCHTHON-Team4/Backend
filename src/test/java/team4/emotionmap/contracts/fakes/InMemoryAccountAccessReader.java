package team4.emotionmap.contracts.fakes;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import team4.emotionmap.contracts.account.AccountAccess;
import team4.emotionmap.contracts.account.AccountAccessReader;
import team4.emotionmap.contracts.account.AccountStatus;
import team4.emotionmap.contracts.geo.GeoPoint;

/** BE2 테스트용 가짜 계정 상태 저장소. put 으로 현재값을 바꾸면 다음 조회부터 반영된다(현재값 재확인 계약). */
public final class InMemoryAccountAccessReader implements AccountAccessReader {

    private final Map<UUID, AccountAccess> accounts = new ConcurrentHashMap<>();

    public InMemoryAccountAccessReader put(AccountAccess access) {
        accounts.put(access.userId(), access);
        return this;
    }

    public InMemoryAccountAccessReader activeOnboarded(UUID userId, GeoPoint mailbox, Instant enabledAt) {
        return put(new AccountAccess(userId, AccountStatus.ACTIVE, enabledAt, mailbox));
    }

    public InMemoryAccountAccessReader withStatus(UUID userId, AccountStatus status) {
        AccountAccess current = accounts.get(userId);
        return put(current == null
                ? new AccountAccess(userId, status, null, null)
                : new AccountAccess(userId, status, current.mailboxEnabledAt(), current.mailbox()));
    }

    @Override
    public Optional<AccountAccess> findAccess(UUID userId) {
        return Optional.ofNullable(accounts.get(userId));
    }
}
