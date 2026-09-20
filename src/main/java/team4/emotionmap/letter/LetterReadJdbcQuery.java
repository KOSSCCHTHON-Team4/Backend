package team4.emotionmap.letter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import team4.emotionmap.contracts.dictionary.AtmosphereAxis;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.memory.MemoryReadAccess;

/** Letter-owned bulk delivery metadata, likes, and receiver history keyset queries. */
@Component
public class LetterReadJdbcQuery {

    private final NamedParameterJdbcTemplate jdbc;

    public LetterReadJdbcQuery(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    public boolean hasDelivery(UUID receiverId, UUID memoryId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("receiverId", Objects.requireNonNull(receiverId, "receiverId"))
                .addValue("memoryId", Objects.requireNonNull(memoryId, "memoryId"));
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS(
                    SELECT 1
                    FROM letter_deliveries
                    WHERE receiver_id = :receiverId AND memory_id = :memoryId
                )
                """, parameters, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    public Map<UUID, MemoryReadAccess.DeliverySnapshot> findDeliveries(UUID receiverId, Set<UUID> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return Map.of();
        }
        for (UUID memoryId : memoryIds) {
            if (memoryId == null) {
                throw new IllegalArgumentException("memoryIds must not contain null");
            }
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("receiverId", Objects.requireNonNull(receiverId, "receiverId"))
                .addValue("memoryIds", List.copyOf(memoryIds));
        Map<UUID, MemoryReadAccess.DeliverySnapshot> deliveries = new HashMap<>();
        jdbc.query("""
                SELECT id, memory_id, service_date, delivered_at, read_at, liked_at
                FROM letter_deliveries
                WHERE receiver_id = :receiverId AND memory_id IN (:memoryIds)
                """, parameters, resultSet -> {
            UUID memoryId = resultSet.getObject("memory_id", UUID.class);
            MemoryReadAccess.DeliverySnapshot delivery = new MemoryReadAccess.DeliverySnapshot(
                    resultSet.getObject("id", UUID.class), memoryId,
                    resultSet.getObject("service_date", LocalDate.class),
                    instant(resultSet.getObject("delivered_at", OffsetDateTime.class)),
                    instant(resultSet.getObject("read_at", OffsetDateTime.class)),
                    instant(resultSet.getObject("liked_at", OffsetDateTime.class)));
            if (deliveries.put(memoryId, delivery) != null) {
                throw new IllegalStateException("duplicate delivery for receiver and memory");
            }
        });
        return Map.copyOf(deliveries);
    }

    public Map<UUID, Long> countLikes(Set<UUID> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return Map.of();
        }
        for (UUID memoryId : memoryIds) {
            if (memoryId == null) {
                throw new IllegalArgumentException("memoryIds must not contain null");
            }
        }
        Map<UUID, Long> counts = new HashMap<>();
        for (UUID memoryId : memoryIds) {
            counts.put(memoryId, 0L);
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource("memoryIds", List.copyOf(memoryIds));
        jdbc.query("""
                SELECT memory_id, COUNT(*) AS like_count
                FROM letter_deliveries
                WHERE memory_id IN (:memoryIds) AND liked_at IS NOT NULL
                GROUP BY memory_id
                """, parameters, resultSet -> {
            UUID memoryId = resultSet.getObject("memory_id", UUID.class);
            long count = resultSet.getLong("like_count");
            if (counts.put(memoryId, count) == null) {
                throw new IllegalStateException("like count returned an unrequested memory");
            }
        });
        return Map.copyOf(counts);
    }

    public List<DeliveryRow> findPage(UUID receiverId, LetterFilter filter,
                                      PageKey after, PageKey upper, int fetchLimit) {
        Objects.requireNonNull(receiverId, "receiverId");
        Objects.requireNonNull(filter, "filter");
        if (fetchLimit < 1) {
            throw new IllegalArgumentException("fetchLimit must be positive");
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("receiverId", receiverId)
                .addValue("fetchLimit", fetchLimit);
        StringBuilder sql = new StringBuilder("SELECT d.id, d.memory_id, d.service_date, d.delivered_at, d.read_at, d.liked_at "
                + "FROM letter_deliveries d ");
        if (filter.isFiltered()) {
            sql.append("JOIN memories m ON m.id = d.memory_id ");
        }
        sql.append("WHERE d.receiver_id = :receiverId ");
        if (filter.isFiltered()) {
            appendFilter(sql, parameters, filter);
        }
        appendKeyset(sql, parameters, after, upper);
        return jdbc.query(sql.toString(), parameters, (resultSet, rowNum) -> new DeliveryRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("memory_id", UUID.class),
                resultSet.getObject("service_date", LocalDate.class),
                instant(resultSet.getObject("delivered_at", OffsetDateTime.class)),
                instant(resultSet.getObject("read_at", OffsetDateTime.class)),
                instant(resultSet.getObject("liked_at", OffsetDateTime.class))));
    }

    private static void appendFilter(StringBuilder sql, MapSqlParameterSource parameters, LetterFilter filter) {
        // Every filtered result must still be currently safe; unfiltered history retains tombstones.
        sql.append("AND m.content_status = 'ACTIVE' AND m.distribution_type = 'LETTER' "
                + "AND m.moderation_status = 'APPROVED' ");
        int parameterIndex = 0;
        boolean hasAxisCondition = false;
        for (AtmosphereAxis axis : AtmosphereAxis.ordered()) {
            for (LetterFilter.AxisRange range : filter.rangesFor(axis)) {
                if (!hasAxisCondition) {
                    sql.append("AND (");
                    hasAxisCondition = true;
                } else {
                    sql.append(" OR ");
                }
                String min = "axisMin" + parameterIndex;
                String max = "axisMax" + parameterIndex++;
                sql.append("(m.").append(axis.columnName()).append(" >= :").append(min)
                        .append(" AND m.").append(axis.columnName()).append(" <= :").append(max).append(")");
                parameters.addValue(min, range.min()).addValue(max, range.max());
            }
        }
        if (hasAxisCondition) {
            sql.append(") ");
        }
        if (!filter.categories().isEmpty()) {
            sql.append("AND EXISTS (SELECT 1 FROM memory_categories mc WHERE mc.memory_id = m.id "
                    + "AND mc.category_id IN (:categoryIds)) ");
            parameters.addValue("categoryIds", filter.categories().stream()
                    .map(PlaceCategoryCode::id).toList());
        }
    }

    private static void appendKeyset(StringBuilder sql, MapSqlParameterSource parameters,
                                     PageKey after, PageKey upper) {
        if (after != null) {
            sql.append("AND (d.delivered_at < :afterDeliveredAt "
                    + "OR (d.delivered_at = :afterDeliveredAt AND d.id < :afterId)) ");
            parameters.addValue("afterDeliveredAt", timestamp(after.deliveredAt()))
                    .addValue("afterId", after.id());
        }
        if (upper != null) {
            sql.append("AND (d.delivered_at < :upperDeliveredAt "
                    + "OR (d.delivered_at = :upperDeliveredAt AND d.id <= :upperId)) ");
            parameters.addValue("upperDeliveredAt", timestamp(upper.deliveredAt()))
                    .addValue("upperId", upper.id());
        }
        sql.append("ORDER BY d.delivered_at DESC, d.id DESC LIMIT :fetchLimit");
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    public record PageKey(Instant deliveredAt, UUID id) {
        public PageKey {
            Objects.requireNonNull(deliveredAt, "deliveredAt");
            Objects.requireNonNull(id, "id");
        }
    }

    public record DeliveryRow(UUID deliveryId, UUID memoryId, LocalDate serviceDate, Instant deliveredAt,
                              Instant readAt, Instant likedAt) {
        public DeliveryRow {
            Objects.requireNonNull(deliveryId, "deliveryId");
            Objects.requireNonNull(memoryId, "memoryId");
            Objects.requireNonNull(serviceDate, "serviceDate");
            Objects.requireNonNull(deliveredAt, "deliveredAt");
        }

        public PageKey key() {
            return new PageKey(deliveredAt, deliveryId);
        }

        public MemoryReadAccess.DeliverySnapshot snapshot() {
            return new MemoryReadAccess.DeliverySnapshot(deliveryId, memoryId, serviceDate, deliveredAt, readAt, likedAt);
        }
    }
}
