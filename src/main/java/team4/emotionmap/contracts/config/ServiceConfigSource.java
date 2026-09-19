package team4.emotionmap.contracts.config;

/**
 * 현재 유효한 {@link ServiceConfig} 를 제공하는 포트. 구현은 {@code catalog} 모듈(A03).
 *
 * <p>필수 설정이 하나라도 없으면 조용히 기본값을 넣지 않고
 * {@code ContractError(CONFIGURATION_UNAVAILABLE)}(503) 을 던진다. 모든 인스턴스·FE 가 같은
 * {@code configVersion} 을 보도록 값은 프로세스 수명 동안 고정이다.
 */
public interface ServiceConfigSource {

    ServiceConfig current();

    default ServiceLimits limits() {
        return current().limits();
    }
}
