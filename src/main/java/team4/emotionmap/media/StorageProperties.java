package team4.emotionmap.media;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;

/**
 * app.storage.* 설정 바인딩.
 *
 * <p>이미지는 로컬 파일시스템의 uploadDir 아래에 UUID 키로 저장한다. 정리 주기와 배치 크기는 새 파일을
 * 만들기 위한 필수 설정이며, 값이 없거나 유효하지 않으면 기존 바인딩된 파일 읽기는 유지하되 새 쓰기와
 * 정리는 닫는다.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String uploadDir,
        Long multipartRequestOverheadBytes,
        Duration cleanupInterval,
        Integer cleanupBatchSize
) {

    public boolean hasUsableCleanupConfiguration() {
        if (cleanupInterval == null || cleanupBatchSize == null || cleanupBatchSize < 1
                || cleanupInterval.isNegative() || cleanupInterval.isZero()) {
            return false;
        }
        try {
            return cleanupInterval.toNanos() > 0;
        } catch (ArithmeticException ignored) {
            return false;
        }
    }

    public void requireUsableCleanupConfiguration() {
        if (!hasUsableCleanupConfiguration()) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
    }

    public long requiredCleanupIntervalNanos() {
        requireUsableCleanupConfiguration();
        return cleanupInterval.toNanos();
    }

    public int requiredCleanupBatchSize() {
        requireUsableCleanupConfiguration();
        return cleanupBatchSize;
    }
}
