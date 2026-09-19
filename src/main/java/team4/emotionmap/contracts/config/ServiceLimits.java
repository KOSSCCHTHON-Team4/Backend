package team4.emotionmap.contracts.config;

/**
 * {@code /config.limits} (API_SPEC 9장 {@code ServiceLimits}). 모든 값은 서버 설정에서 오며
 * 코드 어디에도 기본 숫자를 두지 않는다(D03). 각 모듈은 이 값으로 길이·용량·TTL 을 검사한다.
 */
public record ServiceLimits(
        int memoryContentMaxCodePoints,
        int preferenceDescriptionMaxCodePoints,
        int reportDetailsMaxCodePoints,
        long imageMaxBytes,
        int imageMaxWidth,
        int imageMaxHeight,
        long imageMaxPixels,
        int imageUploadTtlSeconds,
        int analysisTtlSeconds,
        int dailyDirectMemoryLimit,
        int defaultPageLimit,
        int maxPageLimit,
        int maxMapPageLimit
) {
    public ServiceLimits {
        requirePositive(memoryContentMaxCodePoints, "memoryContentMaxCodePoints");
        requirePositive(preferenceDescriptionMaxCodePoints, "preferenceDescriptionMaxCodePoints");
        requirePositive(reportDetailsMaxCodePoints, "reportDetailsMaxCodePoints");
        requirePositive(imageMaxBytes, "imageMaxBytes");
        requirePositive(imageMaxWidth, "imageMaxWidth");
        requirePositive(imageMaxHeight, "imageMaxHeight");
        requirePositive(imageMaxPixels, "imageMaxPixels");
        requirePositive(imageUploadTtlSeconds, "imageUploadTtlSeconds");
        requirePositive(analysisTtlSeconds, "analysisTtlSeconds");
        requirePositive(dailyDirectMemoryLimit, "dailyDirectMemoryLimit");
        requirePositive(defaultPageLimit, "defaultPageLimit");
        requirePositive(maxPageLimit, "maxPageLimit");
        requirePositive(maxMapPageLimit, "maxMapPageLimit");
        if (defaultPageLimit > maxPageLimit) {
            throw new IllegalArgumentException("defaultPageLimit must be <= maxPageLimit");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be >= 1");
        }
    }
}
