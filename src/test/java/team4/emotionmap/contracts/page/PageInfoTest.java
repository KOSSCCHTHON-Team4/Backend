package team4.emotionmap.contracts.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PageInfoTest {

    @Test
    void hasMoreIffCursorPresent() {
        assertThat(PageInfo.last()).isEqualTo(new PageInfo(null, false));
        assertThat(PageInfo.next("c")).isEqualTo(new PageInfo("c", true));
        assertThatThrownBy(() -> new PageInfo("c", false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageInfo(null, true)).isInstanceOf(IllegalArgumentException.class);
    }
}
