package team4.emotionmap.memory;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** SQL ownership/visibility queries for memory pages. Category and delivery metadata are bulked later. */
@Component
public class MemoryReadJdbcQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public MemoryReadJdbcQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public List<PageRow> bookmarks(UUID viewerId, PageKey after, PageKey upper, int fetchLimit) {
        MapSqlParameterSource parameters = base(viewerId, after, upper, fetchLimit);
        StringBuilder sql = new StringBuilder("""
                SELECT m.id, m.created_at
                FROM memories m
                WHERE m.owner_id = :viewerId
                  AND m.distribution_type = 'PRIVATE'
                  AND m.content_status = 'ACTIVE'
                """);
        appendKeyset(sql, parameters, after, upper);
        return query(sql, parameters);
    }

    public List<PageRow> ownLetters(UUID viewerId, PageKey after, PageKey upper, int fetchLimit) {
        MapSqlParameterSource parameters = base(viewerId, after, upper, fetchLimit);
        StringBuilder sql = new StringBuilder("""
                SELECT m.id, m.created_at
                FROM memories m
                WHERE m.owner_id = :viewerId
                  AND m.distribution_type = 'LETTER'
                  AND m.content_status = 'ACTIVE'
                """);
        appendKeyset(sql, parameters, after, upper);
        return query(sql, parameters);
    }

    public List<PageRow> forPlace(UUID viewerId, UUID placeId, PageKey after, PageKey upper, int fetchLimit) {
        MapSqlParameterSource parameters = base(viewerId, after, upper, fetchLimit)
                .addValue("placeId", Objects.requireNonNull(placeId, "placeId"));
        StringBuilder sql = new StringBuilder("""
                SELECT m.id, m.created_at
                FROM memories m
                WHERE m.place_id = :placeId
                  AND m.content_status = 'ACTIVE'
                  AND (
                      m.owner_id = :viewerId
                      OR (
                          m.distribution_type = 'LETTER'
                          AND m.moderation_status = 'APPROVED'
                          AND EXISTS (
                              SELECT 1
                              FROM letter_deliveries d
                              WHERE d.receiver_id = :viewerId AND d.memory_id = m.id
                          )
                      )
                  )
                """);
        appendKeyset(sql, parameters, after, upper);
        return query(sql, parameters);
    }

    private static MapSqlParameterSource base(UUID viewerId, PageKey after, PageKey upper, int fetchLimit) {
        Objects.requireNonNull(viewerId, "viewerId");
        if (fetchLimit < 1) {
            throw new IllegalArgumentException("fetchLimit must be positive");
        }
        return new MapSqlParameterSource().addValue("viewerId", viewerId).addValue("fetchLimit", fetchLimit);
    }

    private static void appendKeyset(StringBuilder sql, MapSqlParameterSource parameters,
                                     PageKey after, PageKey upper) {
        if (after != null) {
            sql.append("""
                      AND (m.created_at < :afterCreatedAt
                           OR (m.created_at = :afterCreatedAt AND m.id < :afterId))
                    """);
            parameters.addValue("afterCreatedAt", timestamp(after.createdAt()))
                    .addValue("afterId", after.id());
        }
        if (upper != null) {
            sql.append("""
                      AND (m.created_at < :upperCreatedAt
                           OR (m.created_at = :upperCreatedAt AND m.id <= :upperId))
                    """);
            parameters.addValue("upperCreatedAt", timestamp(upper.createdAt()))
                    .addValue("upperId", upper.id());
        }
        sql.append(" ORDER BY m.created_at DESC, m.id DESC LIMIT :fetchLimit");
    }

    private List<PageRow> query(StringBuilder sql, MapSqlParameterSource parameters) {
        return jdbc.query(sql.toString(), parameters, (resultSet, rowNum) -> new PageRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant()));
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public record PageKey(Instant createdAt, UUID id) {
        public PageKey {
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(id, "id");
        }
    }

    public record PageRow(UUID id, Instant createdAt) {
        public PageRow {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(createdAt, "createdAt");
        }

        public PageKey key() {
            return new PageKey(createdAt, id);
        }
    }
}
