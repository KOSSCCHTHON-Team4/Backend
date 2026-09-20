package team4.emotionmap.contracts.account;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.geo.GeoPoint;

/**
 * 불변 취향·수신 위치 버전 한 건({@code user_preference_versions} 한 행).
 *
 * @param versionId              내부 버전 UUID (API 에 노출하지 않음)
 * @param userId                 소유 사용자
 * @param revision               사용자별 증가 번호. API 의 {@code preferenceVersion} 은 이 값의 10진 문자열
 * @param effectiveAt            서버가 정한 적용 시각
 * @param atmospheres            4축 전체 값
 * @param description            선택 자연어 취향(공백은 null 로 정규화됨)
 * @param axisDefinitionVersion  이 행에 기록된 4축 사전 버전(현재 2, 역사 행은 1일 수 있음)
 * @param mailbox                이 버전이 적용된 수신 위치
 * @param mailboxEnabledAt       이 위치가 실제로 설정·이동된 publication 시각
 */
public record PreferenceVersionSnapshot(
        UUID versionId,
        UUID userId,
        long revision,
        Instant effectiveAt,
        Atmospheres atmospheres,
        String description,
        int axisDefinitionVersion,
        GeoPoint mailbox,
        Instant mailboxEnabledAt
) {
    public PreferenceVersionSnapshot {
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(atmospheres, "atmospheres");
        mailbox = Objects.requireNonNull(mailbox, "mailbox");
        mailboxEnabledAt = Objects.requireNonNull(mailboxEnabledAt, "mailboxEnabledAt");
        if (revision < 1) {
            throw new IllegalArgumentException("revision starts at 1");
        }
        if (mailboxEnabledAt.isAfter(effectiveAt)) {
            throw new IllegalArgumentException("mailboxEnabledAt must not follow effectiveAt");
        }
        mailbox = new GeoPoint(normalizeZero(mailbox.lat()), normalizeZero(mailbox.lng()));
    }

    /** API 노출용 {@code preferenceVersion}. bigint 정밀도 보존을 위해 문자열. */
    public String preferenceVersion() {
        return Long.toString(revision);
    }

    private static double normalizeZero(double value) {
        return value == 0.0d ? 0.0d : value;
    }
}
