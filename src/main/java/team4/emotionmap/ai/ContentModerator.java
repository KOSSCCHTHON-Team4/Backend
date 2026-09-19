package team4.emotionmap.ai;

/**
 * 기억 본문의 부적절 콘텐츠를 판정하는 포트(모더레이션/필터).
 * 구현체는 추후 Claude(sonnet-5) 등으로 채운다(API 키 발급 후).
 * 아직 구현이 없으므로 서비스는 Optional 주입으로 없을 수도 있음을 전제로 동작한다.
 */
public interface ContentModerator {

    /**
     * @param content 기억 본문
     * @return 판정 결과 (허용 여부 + 사유)
     */
    Result moderate(String content);

    /**
     * @param allowed 게시 허용 여부
     * @param reason  차단 시 사유 (허용이면 null 가능)
     */
    record Result(boolean allowed, String reason) {
        public static Result allow() {
            return new Result(true, null);
        }

        public static Result block(String reason) {
            return new Result(false, reason);
        }
    }
}
