package team4.emotionmap.platform.time;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import team4.emotionmap.contracts.time.SelectionPublicationBarrier;
import team4.emotionmap.contracts.time.ServiceTime;

/** PostgreSQL-backed singleton fence; all methods join the caller's transaction. */
@Component
public class PostgresSelectionPublicationBarrier implements SelectionPublicationBarrier {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public PostgresSelectionPublicationBarrier(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() {
        requireWritableReadCommittedTransaction();
        if (TransactionSynchronizationManager.getResource(this) instanceof FenceState) {
            return;
        }

        List<FenceState> states = jdbc.query(
                "SELECT sealed_through FROM selection_cutoff_fence WHERE id = 1 FOR UPDATE",
                (resultSet, rowNum) -> {
                    OffsetDateTime sealedThrough = resultSet.getObject("sealed_through", OffsetDateTime.class);
                    return new FenceState(sealedThrough == null ? null : sealedThrough.toInstant());
                });
        if (states.size() != 1) {
            throw new IllegalStateException("selection publication fence is unavailable");
        }
        bind(states.getFirst());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Instant publicationTime() {
        FenceState state = requireLock();
        Instant now = databaseNow();
        if (state.sealedThrough == null) {
            return now;
        }
        Instant minimum = state.sealedThrough.plus(1, ChronoUnit.MICROS);
        return now.isBefore(minimum) ? minimum : now;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void sealCutoff(LocalDate serviceDate) {
        FenceState state = requireLock();
        Instant cutoff = ServiceTime.cutoff(serviceDate);
        Instant now = databaseNow();
        if (now.isBefore(cutoff)) {
            throw new IllegalStateException("cannot seal a future selection cutoff");
        }

        jdbc.update("""
                UPDATE selection_cutoff_fence
                SET sealed_through = CASE
                    WHEN sealed_through IS NULL OR sealed_through < ? THEN ?
                    ELSE sealed_through
                END
                WHERE id = 1
                """, timestamp(cutoff), timestamp(cutoff));
        if (state.sealedThrough == null || state.sealedThrough.isBefore(cutoff)) {
            state.sealedThrough = cutoff;
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Instant databaseNow() {
        requireWritableReadCommittedTransaction();
        Instant now = jdbc.queryForObject(
                "SELECT clock_timestamp()", (resultSet, rowNum) ->
                        resultSet.getObject(1, OffsetDateTime.class).toInstant());
        if (now == null) {
            throw new IllegalStateException("database clock is unavailable");
        }
        return now;
    }

    private void bind(FenceState state) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("selection publication barrier requires transaction synchronization");
        }
        TransactionSynchronizationManager.bindResource(this, state);
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int completionStatus) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(PostgresSelectionPublicationBarrier.this);
                }
            });
        } catch (RuntimeException failure) {
            TransactionSynchronizationManager.unbindResourceIfPossible(this);
            throw failure;
        }
    }

    private FenceState requireLock() {
        requireWritableReadCommittedTransaction();
        Object state = TransactionSynchronizationManager.getResource(this);
        if (state instanceof FenceState fenceState) {
            return fenceState;
        }
        throw new IllegalStateException("selection publication fence must be locked first");
    }

    private void requireWritableReadCommittedTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("selection publication barrier requires a writable transaction");
        }
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalStateException("selection publication barrier requires READ COMMITTED");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("cannot inspect transaction isolation", failure);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static final class FenceState {
        private Instant sealedThrough;

        private FenceState(Instant sealedThrough) {
            this.sealedThrough = sealedThrough;
        }
    }
}
