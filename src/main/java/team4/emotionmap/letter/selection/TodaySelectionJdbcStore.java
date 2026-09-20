package team4.emotionmap.letter.selection;

import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import team4.emotionmap.letter.DailySelectionStatus;
import team4.emotionmap.memory.MemoryReadAccess;

/**
 * Read-only PostgreSQL implementation for {@link TodaySelectionStore}.
 *
 * <p>The query freezes {@code clock_timestamp()} once and returns the KST service day, historical
 * cutoff eligibility/configuration facts, daily state, and delivery metadata from one statement.
 * It has no scheduler, claim, recovery, configuration, or selection-engine dependency.</p>
 */
@Component
public class TodaySelectionJdbcStore implements TodaySelectionStore {

    private static final String TODAY_READ = """
            WITH observed_clock AS MATERIALIZED (
                SELECT clock_timestamp() AS server_time
            ), requested_user AS (
                SELECT ?::uuid AS user_id
            ), schedule AS (
                SELECT server_time,
                       (server_time AT TIME ZONE 'Asia/Seoul')::date AS service_date,
                       (((server_time AT TIME ZONE 'Asia/Seoul')::date + TIME '09:00')
                           AT TIME ZONE 'Asia/Seoul') AS cutoff_at
                FROM observed_clock
            ), eligible_preference AS (
                SELECT p.id
                FROM user_preference_versions p
                CROSS JOIN requested_user requested
                CROSS JOIN schedule schedule
                WHERE p.user_id = requested.user_id
                  AND p.effective_at <= schedule.cutoff_at
                  AND p.mailbox_enabled_at <= schedule.cutoff_at
                ORDER BY p.revision DESC
                LIMIT 1
            ), configuration AS (
                SELECT EXISTS (
                    SELECT 1
                    FROM selection_config_versions config
                    CROSS JOIN schedule schedule
                    WHERE config.effective_at <= schedule.cutoff_at
                ) AS available
            )
            SELECT schedule.server_time,
                   EXISTS (SELECT 1 FROM eligible_preference) AS eligible_at_cutoff,
                   configuration.available AS configuration_available_at_cutoff,
                   selection.status AS selection_status,
                   delivery.id AS delivery_id,
                   delivery.memory_id AS delivery_memory_id,
                   delivery.service_date AS delivery_service_date,
                   delivery.delivered_at AS delivered_at,
                   delivery.read_at AS read_at,
                   delivery.liked_at AS liked_at
            FROM schedule
            CROSS JOIN requested_user requested
            CROSS JOIN configuration
            LEFT JOIN daily_selections selection
              ON selection.user_id = requested.user_id
             AND selection.service_date = schedule.service_date
            LEFT JOIN letter_deliveries delivery
              ON delivery.receiver_id = requested.user_id
             AND delivery.service_date = schedule.service_date
            """;

    private final JdbcTemplate jdbc;

    public TodaySelectionJdbcStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public Snapshot read(UUID userId) {
        Objects.requireNonNull(userId, "userId");
        Snapshot snapshot = jdbc.queryForObject(TODAY_READ, (resultSet, rowNum) -> snapshot(resultSet), userId);
        return Objects.requireNonNull(snapshot, "today selection statement returned no row");
    }

    private static Snapshot snapshot(ResultSet resultSet) throws java.sql.SQLException {
        String rawStatus = resultSet.getString("selection_status");
        UUID deliveryId = resultSet.getObject("delivery_id", UUID.class);
        MemoryReadAccess.DeliverySnapshot delivery = deliveryId == null ? null : new MemoryReadAccess.DeliverySnapshot(
                deliveryId,
                resultSet.getObject("delivery_memory_id", UUID.class),
                resultSet.getObject("delivery_service_date", LocalDate.class),
                instant(resultSet, "delivered_at"),
                instant(resultSet, "read_at"),
                instant(resultSet, "liked_at"));
        return new Snapshot(
                Objects.requireNonNull(instant(resultSet, "server_time"), "serverTime"),
                resultSet.getBoolean("eligible_at_cutoff"),
                resultSet.getBoolean("configuration_available_at_cutoff"),
                rawStatus == null ? null : DailySelectionStatus.valueOf(rawStatus),
                delivery);
    }

    private static Instant instant(ResultSet resultSet, String column) throws java.sql.SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
