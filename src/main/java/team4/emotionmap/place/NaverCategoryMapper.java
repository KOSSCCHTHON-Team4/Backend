package team4.emotionmap.place;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;

/**
 * 네이버 장소 카테고리 문자열 → 자체 8종 매핑 규칙(기획 §5 "매핑 테이블(규칙)").
 * 입력 예: {@code "카페,디저트>카페"}, {@code "음식점>한식"}. 구분자(",", ">", "/")로 나눈 토큰의
 * <b>마지막(가장 구체적인) 토큰부터</b> 키워드 규칙을 적용하고, 어떤 규칙에도 맞지 않으면 OTHER 다.
 * 빈 입력은 empty(미등록 장소 → AI 추론 경로).
 */
public final class NaverCategoryMapper {

    private record Rule(PlaceCategoryCode code, String... keywords) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(PlaceCategoryCode.CAFE, "카페", "커피", "디저트", "베이커리", "제과", "빙수", "티룸", "브런치카페"),
            new Rule(PlaceCategoryCode.BAR, "술집", "주점", "펍", "호프", "맥주", "와인", "이자카야", "포차", "칵테일", "요리주점", "와인바", "칵테일바"),
            new Rule(PlaceCategoryCode.RESTAURANT, "음식점", "식당", "한식", "일식", "중식", "양식", "분식", "고기", "치킨",
                    "피자", "햄버거", "국수", "초밥", "돈까스", "레스토랑", "뷔페", "브런치", "패스트푸드", "요리"),
            new Rule(PlaceCategoryCode.PARK_WALK, "공원", "산책", "산책로", "하천", "호수", "둘레길", "등산", "수목원", "정원", "광장", "강변"),
            new Rule(PlaceCategoryCode.CULTURE, "박물관", "미술관", "전시", "갤러리", "공연", "극장", "영화", "문화", "서점", "책방", "도서관", "기념관"),
            new Rule(PlaceCategoryCode.STUDY_WORK, "스터디", "독서실", "코워킹", "공유오피스", "작업실", "학원", "세미나"),
            new Rule(PlaceCategoryCode.SHOPPING, "쇼핑", "백화점", "마트", "시장", "매장", "편집숍", "잡화", "의류", "쇼핑몰", "상점", "아울렛", "편의점")
    );

    private NaverCategoryMapper() {
    }

    public static Optional<PlaceCategoryCode> map(String naverCategory) {
        if (naverCategory == null || naverCategory.isBlank()) {
            return Optional.empty();
        }
        String[] tokens = naverCategory.split("[,>/]");
        // 구체적인 토큰(뒤)부터 검사한다: "카페,디저트>카페" → 카페, "문화,예술>서점" → 서점(CULTURE)
        List<String> ordered = Arrays.stream(tokens).map(t -> t.strip().toLowerCase(Locale.ROOT))
                .filter(t -> !t.isEmpty()).toList().reversed();
        for (String token : ordered) {
            for (Rule rule : RULES) {
                for (String keyword : rule.keywords()) {
                    if (token.contains(keyword)) {
                        return Optional.of(rule.code());
                    }
                }
            }
        }
        return Optional.of(PlaceCategoryCode.OTHER);
    }
}
