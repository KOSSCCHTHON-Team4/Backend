package team4.emotionmap.contracts.page;

/**
 * 목록 응답 {@code {items, pageInfo}} 의 pageInfo (API_SPEC 9장).
 * 불변 조건: {@code hasMore == (nextCursor != null)}. 커서는 사용자·경로·필터·정렬 범위에 귀속되고
 * 서명은 {@code contracts.signing.SignedValueCodec} 의 {@code PAGE_CURSOR} 용도로만 만든다.
 */
public record PageInfo(String nextCursor, boolean hasMore) {

    public PageInfo {
        if (hasMore != (nextCursor != null)) {
            throw new IllegalArgumentException("hasMore must be true iff nextCursor is present");
        }
    }

    public static PageInfo last() {
        return new PageInfo(null, false);
    }

    public static PageInfo next(String nextCursor) {
        return new PageInfo(nextCursor, true);
    }
}
