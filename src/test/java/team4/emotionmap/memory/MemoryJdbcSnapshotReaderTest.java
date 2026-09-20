package team4.emotionmap.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import team4.emotionmap.contracts.memory.MemorySnapshot;

class MemoryJdbcSnapshotReaderTest {

    @Test
    void acceptsAnImmutableSingletonIdSetForAnEmptyBulkResult() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        doNothing().when(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(RowCallbackHandler.class));

        UUID memoryId = UUID.randomUUID();
        Map<UUID, MemorySnapshot> snapshots = new MemoryJdbcSnapshotReader(jdbc).readAll(Set.of(memoryId));

        assertThat(snapshots).isEmpty();
    }
}
