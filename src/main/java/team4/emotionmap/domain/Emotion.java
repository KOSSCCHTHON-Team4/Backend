package team4.emotionmap.domain;

/**
 * 감정 유형. DB 에는 name() 문자열로 저장한다 (@Enumerated(EnumType.STRING)).
 * 값을 추가/삭제할 때는 이미 저장된 데이터와의 호환을 고려한다.
 */
public enum Emotion {
    JOY,
    SADNESS,
    ANGER,
    FEAR,
    SURPRISE,
    DISGUST,
    NEUTRAL
}
