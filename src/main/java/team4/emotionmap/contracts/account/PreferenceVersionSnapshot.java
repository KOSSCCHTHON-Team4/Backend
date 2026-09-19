package team4.emotionmap.contracts.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import team4.emotionmap.contracts.dictionary.Atmospheres;

/**
 * 불변 취향 버전 한 건({@code user_preference_versions} 한 행).
 *
 * @param versionId              내부 버전 UUID (API 에 노출하지 않음)
 * @param userId                 소유 사용자
 * @param revision               사용자별 증가 번호. API 의 {@code preferenceVersion} 은 이 값의 10진 문자열
 * @param effectiveAt            서버가 정한 적용 시각
 * @param atmospheres            4축 전체 값
 * @param description            선택 자연어 취향(공백은 null 로 정규화됨)
 * @param axisDefinitionVersion  이 행에 기록된 4축 사전 버전(현재 2, 역사 행은 1일 수 있음)
 */
public record PreferenceVersionSnapshot(
        UUID versionId,
        UUID userId,
        long revision,
        Instant effectiveAt,
        Atmospheres atmospheres,
        String description,
        int axisDefinitionVersion
) {
    public PreferenceVersionSnapshot {
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(atmospheres, "atmospheres");
        if (revision < 1) {
            throw new IllegalArgumentException("revision starts at 1");
        }
    }

    /** API 노출용 {@code preferenceVersion}. bigint 정밀도 보존을 위해 문자열. */
    public String preferenceVersion() {
        return Long.toString(revision);
    }
}
