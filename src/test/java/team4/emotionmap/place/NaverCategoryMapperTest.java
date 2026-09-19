package team4.emotionmap.place;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;

class NaverCategoryMapperTest {

    @Test
    void mapsNaverStringsToInternalCodes() {
        assertThat(NaverCategoryMapper.map("카페,디저트>카페")).contains(PlaceCategoryCode.CAFE);
        assertThat(NaverCategoryMapper.map("음식점>한식")).contains(PlaceCategoryCode.RESTAURANT);
        assertThat(NaverCategoryMapper.map("술집>와인바")).contains(PlaceCategoryCode.BAR);
        assertThat(NaverCategoryMapper.map("여행,명소>공원")).contains(PlaceCategoryCode.PARK_WALK);
        assertThat(NaverCategoryMapper.map("문화,예술>미술관")).contains(PlaceCategoryCode.CULTURE);
        assertThat(NaverCategoryMapper.map("교육,학문>스터디카페")).contains(PlaceCategoryCode.CAFE); // 마지막 토큰 우선: 카페
        assertThat(NaverCategoryMapper.map("교육,학문>독서실")).contains(PlaceCategoryCode.STUDY_WORK);
        assertThat(NaverCategoryMapper.map("쇼핑,유통>백화점")).contains(PlaceCategoryCode.SHOPPING);
    }

    @Test
    void unknownIsOtherAndBlankIsEmpty() {
        assertThat(NaverCategoryMapper.map("기타>세차장")).contains(PlaceCategoryCode.OTHER);
        assertThat(NaverCategoryMapper.map("   ")).isEmpty();
        assertThat(NaverCategoryMapper.map(null)).isEmpty();
    }
}
