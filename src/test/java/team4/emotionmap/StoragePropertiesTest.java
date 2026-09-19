package team4.emotionmap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import team4.emotionmap.config.StorageProperties;

/**
 * DB 비연결 단위테스트.
 *
 * 정책상 CI 에서는 DB(로컬 전용)에 연결하지 않으므로,
 * 스프링 컨텍스트나 DB 를 띄우지 않는 순수 단위테스트만 둔다.
 * DB 가 필요한 통합테스트는 각자 로컬 PostgreSQL 로만 수동 수행한다.
 */
class StoragePropertiesTest {

    @Test
    void bindsFields() {
        StorageProperties props =
                new StorageProperties("./uploads", java.util.List.of("jpg", "png"), 10_485_760L);
        assertThat(props.uploadDir()).isEqualTo("./uploads");
        assertThat(props.allowedExtensions()).containsExactly("jpg", "png");
        assertThat(props.maxSizeBytes()).isEqualTo(10_485_760L);
    }
}
