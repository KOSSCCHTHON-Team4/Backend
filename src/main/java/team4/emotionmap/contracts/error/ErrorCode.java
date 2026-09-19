package team4.emotionmap.contracts.error;

/**
 * API_SPEC 10장 "오류 코드 전체 목록"의 1:1 사본. FE 는 {@code code} 로만 분기한다.
 *
 * <p>HTTP 상태와 대표 message 는 명세 표를 그대로 옮겼다. 새 코드는 OpenAPI → BE → FE mock 을
 * 같은 변경 단위로 갱신한다(WORK_PLAN §6.2). message 에 SQL·경로·작성자 정보를 넣지 않는다.
 */
public enum ErrorCode {
    // --- 인증·계정 ---
    INVALID_CREDENTIALS(401, "이메일 또는 비밀번호가 올바르지 않습니다"),
    /** 예약 코드. 로그인 경로에서는 발생하지 않는다(공개 회원가입 없음). */
    EMAIL_ALREADY_EXISTS(409, "이미 사용 중인 이메일입니다"),
    TOO_MANY_ATTEMPTS(429, "로그인 시도 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요"),
    AUTH_REQUIRED(401, "로그인이 필요합니다"),
    TOKEN_EXPIRED(401, "로그인이 만료되었습니다. 다시 로그인해 주세요"),
    INVALID_TOKEN(401, "인증 정보를 확인할 수 없습니다"),
    INVITATION_REQUIRED(403, "초대된 계정만 사용할 수 있습니다"),
    ACCOUNT_SUSPENDED(403, "이용이 제한된 계정입니다"),
    ACCOUNT_CLOSED(403, "종료된 계정입니다"),
    ONBOARDING_REQUIRED(403, "초기 설정을 완료해 주세요"),

    // --- 요청 구조·검증 ---
    INVALID_JSON(400, "JSON 형식이 올바르지 않습니다"),
    DUPLICATE_JSON_KEY(400, "중복된 JSON 키가 있습니다"),
    INVALID_REQUEST(400, "요청 형식이 올바르지 않습니다"),
    VALIDATION_ERROR(422, "입력값을 확인해 주세요"),
    INVALID_ATMOSPHERES(422, "분위기 4축을 각각 -1 또는 1로 입력해 주세요"),
    INVALID_CATEGORIES(422, "정의된 카테고리를 중복 없이 최대 3개 선택해 주세요"),
    IMMUTABLE_FIELD(422, "변경할 수 없는 항목이 포함되어 있습니다"),

    // --- 온보딩·취향 ---
    ONBOARDING_ALREADY_COMPLETED(409, "초기 설정은 이미 완료되었습니다"),
    PREFERENCE_VERSION_CONFLICT(409, "다른 요청에서 취향 설정이 변경되었습니다. 최신 설정을 확인해 주세요"),

    // --- 자원·권한 ---
    RESOURCE_NOT_FOUND(404, "요청한 항목을 찾을 수 없습니다"),
    MEMORY_UNAVAILABLE(410, "더 이상 열람할 수 없는 경험입니다"),

    // --- 이미지 ---
    IMAGE_NOT_FOUND(404, "사용할 수 있는 업로드 이미지를 찾을 수 없습니다"),
    IMAGE_UPLOAD_EXPIRED(410, "이미지 업로드 보관 시간이 만료되었습니다. 다시 업로드해 주세요"),
    IMAGE_ALREADY_ATTACHED(409, "이미 사용한 이미지입니다"),
    IMAGE_TOO_LARGE(413, "이미지 용량이 허용 범위를 초과했습니다"),
    UNSUPPORTED_IMAGE_TYPE(415, "JPG 또는 PNG 이미지만 사용할 수 있습니다"),
    INVALID_IMAGE(422, "올바른 이미지 파일이 아닙니다"),
    IMAGE_DIMENSIONS_EXCEEDED(422, "이미지 해상도가 허용 범위를 초과했습니다"),
    IMAGE_STORAGE_UNAVAILABLE(503, "이미지를 저장하지 못했습니다. 다시 시도해 주세요"),
    IMAGE_FILE_UNAVAILABLE(503, "이미지 파일을 불러오지 못했습니다"),

    // --- 분석 확인값 ---
    ANALYSIS_TOKEN_INVALID(422, "분석 결과를 확인할 수 없습니다"),
    ANALYSIS_TOKEN_EXPIRED(422, "분석 결과가 만료되었습니다. 다시 분석하거나 직접 분류해 주세요"),
    ANALYSIS_CONTENT_MISMATCH(422, "분석한 본문과 저장할 본문이 다릅니다"),

    // --- 한도 ---
    DAILY_WRITE_LIMIT_EXCEEDED(429, "오늘 작성할 수 있는 경험 수를 초과했습니다"),
    RATE_LIMITED(429, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요"),

    // --- 조회 ---
    INVALID_CURSOR(400, "페이지 커서가 올바르지 않습니다"),
    CURSOR_CONTEXT_MISMATCH(400, "조회 조건이 변경되었습니다. 첫 페이지부터 다시 조회해 주세요"),
    INVALID_BBOX(400, "지도 조회 범위가 올바르지 않습니다"),
    PLACE_COORDINATE_MISMATCH(422, "선택한 장소와 좌표가 일치하지 않습니다"),

    // --- 생성 재시도 ---
    IDEMPOTENCY_KEY_REQUIRED(400, "재시도 식별자가 필요합니다"),
    IDEMPOTENCY_KEY_REUSED(409, "같은 재시도 식별자에 다른 요청이 사용되었습니다"),
    REQUEST_IN_PROGRESS(409, "동일한 요청을 처리하고 있습니다"),

    // --- 서버·의존성 ---
    COPY_FAILED(503, "보관을 완료하지 못했습니다. 다시 시도해 주세요"),
    CONFIGURATION_UNAVAILABLE(503, "서비스 설정을 불러올 수 없습니다"),
    SERVICE_UNAVAILABLE(503, "일시적으로 요청을 처리할 수 없습니다"),
    SERVER_ERROR(500, "요청 처리 중 오류가 발생했습니다");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }

    /** 401 계열. 응답에 {@code WWW-Authenticate: Bearer} 를 포함해야 한다(API_SPEC 2.3). */
    public boolean isUnauthorized() {
        return httpStatus == 401;
    }
}
