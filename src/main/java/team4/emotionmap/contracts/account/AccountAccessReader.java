package team4.emotionmap.contracts.account;

import java.util.Optional;
import java.util.UUID;

/**
 * BE1 제공 · BE2 소비. 계정의 현재 접근 상태를 DB 현재값으로 다시 읽는다(C02).
 * 브라우저가 보낸 userId 나 토큰의 과거 발급 상태를 신뢰하지 않는다.
 */
public interface AccountAccessReader {

    /** 존재하지 않는 계정은 empty. */
    Optional<AccountAccess> findAccess(UUID userId);

    /**
     * ACTIVE 가 아니면 상태별 403 {@code ContractError}, 없는 계정은 {@code INVALID_TOKEN}(401).
     * 온보딩 여부는 검사하지 않는다(온보딩 전 허용 경로가 있으므로 호출자가 판단).
     */
    default AccountAccess requireActive(UUID userId) {
        AccountAccess access = findAccess(userId)
                .orElseThrow(() -> team4.emotionmap.contracts.error.ContractError.of(
                        team4.emotionmap.contracts.error.ErrorCode.INVALID_TOKEN));
        if (!access.status().allowsAccess()) {
            throw team4.emotionmap.contracts.error.ContractError.of(access.status().denialCode());
        }
        return access;
    }

    /** ACTIVE 이면서 온보딩까지 끝난 계정만 통과. 아니면 {@code ONBOARDING_REQUIRED}(403). */
    default AccountAccess requireOnboarded(UUID userId) {
        AccountAccess access = requireActive(userId);
        if (!access.hasOnboarded()) {
            throw team4.emotionmap.contracts.error.ContractError.of(
                    team4.emotionmap.contracts.error.ErrorCode.ONBOARDING_REQUIRED);
        }
        return access;
    }
}
