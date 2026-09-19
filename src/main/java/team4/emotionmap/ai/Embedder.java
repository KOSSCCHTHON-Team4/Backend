package team4.emotionmap.ai;

/**
 * 본문을 임베딩 벡터로 변환하는 포트.
 * 구현체는 추후 임베딩 제공자(Voyage 등) 연동으로 채운다(API 키 발급 후).
 * Claude 는 임베딩 모델이 아니므로 여기 구현이 될 수 없다.
 * 아직 구현이 없으므로 서비스는 Optional 주입으로 없을 수도 있음을 전제로 동작한다.
 */
public interface Embedder {

    /**
     * @param content 기억 본문
     * @return 임베딩 벡터 (차원 = Memory.EMBEDDING_DIMENSION)
     */
    float[] embed(String content);
}
