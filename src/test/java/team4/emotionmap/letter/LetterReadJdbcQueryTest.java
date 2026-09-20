package team4.emotionmap.letter;

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

class LetterReadJdbcQueryTest {

    @Test
    void acceptsAnImmutableSingletonIdSetForDeliveryAndLikeBulkReads() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        doNothing().when(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(RowCallbackHandler.class));

        UUID receiverId = UUID.randomUUID();
        UUID memoryId = UUID.randomUUID();
        LetterReadJdbcQuery query = new LetterReadJdbcQuery(jdbc);

        Map<UUID, team4.emotionmap.memory.MemoryReadAccess.DeliverySnapshot> deliveries =
                query.findDeliveries(receiverId, Set.of(memoryId));
        Map<UUID, Long> likes = query.countLikes(Set.of(memoryId));

        assertThat(deliveries).isEmpty();
        assertThat(likes).containsEntry(memoryId, 0L);
    }
}
