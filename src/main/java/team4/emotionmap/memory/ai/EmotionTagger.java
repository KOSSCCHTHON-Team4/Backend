package team4.emotionmap.memory.ai;

import team4.emotionmap.memory.Emotion;

/**
 * 본문에서 감정 태그를 추출하는 포트.
 * 구현체는 추후 Claude(sonnet-5) 연동으로 채운다(API 키 발급 후).
 * 아직 구현이 없으므로 서비스는 이 포트가 없을 수도 있음을 전제로 동작한다(Optional 주입).
 */
public interface EmotionTagger {

    /**
     * @param content 기억 본문
     * @return 추출된 감정 태그
     */
    Emotion extract(String content);
}
