package team4.emotionmap.catalog;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import team4.emotionmap.contracts.config.SelectionConfigHistory;
import team4.emotionmap.contracts.config.SelectionConfigPublisher;
import team4.emotionmap.contracts.time.SelectionPublicationBarrier;

/** Catalog-owned database implementation of immutable selection configuration history. */
@Component
public class SelectionConfigJdbcCatalog implements SelectionConfigHistory, SelectionConfigPublisher {

    private static final Set<String> REGISTERED_RULES = Set.of("atmosphere-v1", "atmosphere-v2");

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final SelectionPublicationBarrier publicationBarrier;

    public SelectionConfigJdbcCatalog(DataSource dataSource, SelectionPublicationBarrier publicationBarrier) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
        this.publicationBarrier = publicationBarrier;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Snapshot> findAt(Instant cutoff) {
        requireReadCommittedTransaction();
        Objects.requireNonNull(cutoff, "cutoff");
        return jdbc.query("""
                SELECT revision, config_version, effective_at, nearby_radius_meters, rule_version
                FROM selection_config_versions
                WHERE effective_at <= ?
                ORDER BY effective_at DESC, revision DESC
                LIMIT 1
                """, (resultSet, rowNum) -> snapshot(resultSet.getLong("revision"),
                resultSet.getString("config_version"),
                resultSet.getObject("effective_at", OffsetDateTime.class).toInstant(),
                resultSet.getInt("nearby_radius_meters"),
                resultSet.getString("rule_version")), timestamp(cutoff)).stream().findFirst();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Snapshot append(String configVersion, Instant effectiveAt, int radiusMeters, String ruleVersion) {
        requirePayload(configVersion, effectiveAt, radiusMeters, ruleVersion);
        requireReadCommittedTransaction();
        publicationBarrier.lock();

        Optional<Snapshot> existing = findByConfigVersion(configVersion);
        if (existing.isPresent()) {
            Snapshot snapshot = existing.get();
            if (samePayload(snapshot, effectiveAt, radiusMeters, ruleVersion)) {
                return snapshot;
            }
            throw new IllegalStateException("selection config version already has a different payload");
        }

        if (effectiveAt.isBefore(publicationBarrier.publicationTime())) {
            throw new IllegalArgumentException("selection config effectiveAt predates publication");
        }
        return jdbc.queryForObject("""
                INSERT INTO selection_config_versions(config_version, effective_at, nearby_radius_meters, rule_version)
                VALUES (?, ?, ?, ?)
                RETURNING revision, config_version, effective_at, nearby_radius_meters, rule_version
                """, (resultSet, rowNum) -> snapshot(resultSet.getLong("revision"),
                resultSet.getString("config_version"),
                resultSet.getObject("effective_at", OffsetDateTime.class).toInstant(),
                resultSet.getInt("nearby_radius_meters"),
                resultSet.getString("rule_version")), configVersion, timestamp(effectiveAt), radiusMeters, ruleVersion);
    }

    private Optional<Snapshot> findByConfigVersion(String configVersion) {
        return jdbc.query("""
                SELECT revision, config_version, effective_at, nearby_radius_meters, rule_version
                FROM selection_config_versions
                WHERE config_version = ?
                """, (resultSet, rowNum) -> snapshot(resultSet.getLong("revision"),
                resultSet.getString("config_version"),
                resultSet.getObject("effective_at", OffsetDateTime.class).toInstant(),
                resultSet.getInt("nearby_radius_meters"),
                resultSet.getString("rule_version")), configVersion).stream().findFirst();
    }

    private static Snapshot snapshot(long revision, String configVersion, Instant effectiveAt, int radiusMeters,
                                     String ruleVersion) {
        return new Snapshot(revision, configVersion, effectiveAt, radiusMeters, ruleVersion);
    }

    private static boolean samePayload(Snapshot existing, Instant effectiveAt, int radiusMeters, String ruleVersion) {
        return existing.effectiveAt().equals(effectiveAt)
                && existing.radiusMeters() == radiusMeters
                && existing.ruleVersion().equals(ruleVersion);
    }

    private static void requirePayload(String configVersion, Instant effectiveAt, int radiusMeters, String ruleVersion) {
        if (configVersion == null || configVersion.isBlank()) {
            throw new IllegalArgumentException("configVersion must not be blank");
        }
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        if (effectiveAt.getNano() % 1_000 != 0) {
            throw new IllegalArgumentException("effectiveAt must have microsecond precision");
        }
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("radiusMeters must be positive");
        }
        if (ruleVersion == null || !REGISTERED_RULES.contains(ruleVersion)) {
            throw new IllegalArgumentException("ruleVersion is not registered");
        }
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private void requireReadCommittedTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("selection configuration history requires a transaction");
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalStateException("selection configuration history requires READ COMMITTED");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("cannot inspect transaction isolation", failure);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
