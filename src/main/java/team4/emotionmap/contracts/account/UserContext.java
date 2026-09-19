package team4.emotionmap.contracts.account;

import java.util.Optional;
import java.util.UUID;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;

/**
 * 현재 요청의 인증 주체(C02 Principal). 구현은 platform.security 가 SecurityContext 에서 읽는다.
 * <b>식별자 타입은 UUID</b> 로 확정한다(ERD 1.0 uuid PK). 현재 baseline 의 Long principal 은 A01 에서 교체된다.
 *
 * <p>여기서 얻은 ID 는 "누가 요청했나"일 뿐이다. 계정 상태·온보딩은 {@link AccountAccessReader} 로
 * 매 요청 현재값을 다시 확인한다.
 */
public interface UserContext {

    Optional<UUID> currentUserId();

    default UUID requireUserId() {
        return currentUserId().orElseThrow(() -> ContractError.of(ErrorCode.AUTH_REQUIRED));
    }
}
