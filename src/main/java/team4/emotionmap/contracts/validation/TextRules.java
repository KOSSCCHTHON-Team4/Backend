package team4.emotionmap.contracts.validation;

import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;

/**
 * 텍스트 정규화 규칙(API_SPEC 2.2). 세 종류를 섞지 않는다.
 * <ul>
 *   <li><b>본문(content)</b>: 저장·분석·토큰 해시에 <b>같은 문자열</b>을 쓴다. trim/Unicode 정규화/개행 변경 금지.
 *       공백만이면 거절. 길이는 코드 포인트 수.</li>
 *   <li><b>선택 설명(취향 설명·신고 details)</b>: 앞뒤 공백 제거, 비면 null. 길이는 코드 포인트 수.</li>
 *   <li><b>비밀번호</b>: trim·대소문자·정규화 일절 금지. 빈 문자열만 거절. 값은 로그에 남기지 않는다.</li>
 * </ul>
 * 최대 길이는 {@code /config.limits} 값을 인자로 받는다(하드코딩 금지, D03).
 */
public final class TextRules {

    private TextRules() {
    }

    public static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    /** 본문: 원문 그대로 반환. null/공백만 → REQUIRED, 길이 초과 → TOO_LONG. */
    public static String requireContent(String content, int maxCodePoints, String field, ErrorCode errorCode) {
        if (content == null || content.isBlank()) {
            throw ContractError.of(errorCode, FieldError.required(field));
        }
        if (codePointLength(content) > maxCodePoints) {
            throw ContractError.of(errorCode, FieldError.tooLong(field));
        }
        return content;
    }

    /** 선택 설명: trim 후 비면 null. 길이는 trim 결과 기준. */
    public static String normalizeOptionalText(String value, int maxCodePoints, String field, ErrorCode errorCode) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (codePointLength(trimmed) > maxCodePoints) {
            throw ContractError.of(errorCode, FieldError.tooLong(field));
        }
        return trimmed;
    }

    /** 비밀번호: 어떤 정규화도 하지 않는다. 빈 문자열은 VALIDATION_ERROR(422). */
    public static String requirePassword(String password, String field) {
        if (password == null) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST, FieldError.required(field));
        }
        if (password.isEmpty()) {
            throw ContractError.of(ErrorCode.VALIDATION_ERROR, FieldError.invalid(field));
        }
        return password;
    }
}
