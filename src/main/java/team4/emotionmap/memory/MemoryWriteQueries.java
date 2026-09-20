package team4.emotionmap.memory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.contracts.time.ServiceTime;

/** Write-side SQL that is deliberately separate from the shared read repository. */
@Component
public class MemoryWriteQueries {
    private final JdbcTemplate jdbc;

    public MemoryWriteQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    /** Caller holds the owner's row lock for this whole transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long countDirectForServiceDay(UUID ownerId, Instant instant) {
        Objects.requireNonNull(ownerId, "ownerId");
        LocalDate serviceDate = ServiceTime.serviceDate(Objects.requireNonNull(instant, "instant"));
        Instant start = serviceDate.atStartOfDay(ServiceTime.ZONE).toInstant();
        Instant end = serviceDate.plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
        Long count = jdbc.queryForObject("""
                select count(*)
                from memories
                where owner_id = ?
                  and origin_kind = 'DIRECT'
                  and created_at >= ?
                  and created_at < ?
                """, Long.class,
                ownerId,
                OffsetDateTime.ofInstant(start, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(end, ZoneOffset.UTC));
        return count == null ? 0L : count;
    }
}
