package team4.emotionmap.platform.request;

import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.request.RequestCoordinator;
import team4.emotionmap.contracts.request.RequestCoordinator.Admission;
import team4.emotionmap.contracts.request.RequestCoordinator.Claim;
import team4.emotionmap.contracts.request.RequestCoordinator.Claimed;
import team4.emotionmap.contracts.request.RequestCoordinator.Completion;
import team4.emotionmap.contracts.request.RequestCoordinator.CompletionKind;
import team4.emotionmap.contracts.request.RequestCoordinator.Fingerprint;
import team4.emotionmap.contracts.request.RequestCoordinator.Replay;
import team4.emotionmap.contracts.request.RequestCoordinator.ResourceRef;
import team4.emotionmap.contracts.request.RequestCoordinator.Route;
import team4.emotionmap.contracts.request.RequestCoordinator.Scope;
import team4.emotionmap.platform.web.ApiErrorWriter;

/**
 * PostgreSQL-backed ownership and completion fence for the three idempotent POST routes.
 *
 * <p>Every public operation starts a {@code REQUIRES_NEW}, READ_COMMITTED transaction. A claim therefore
 * commits before a caller can begin its business work, while completion keeps the request row lock and the
 * supplied resource work in one transaction. The class intentionally stores only the final resource identity;
 * it neither caches response bodies nor owns a retention policy.
 */
@Component
public class PersistentRequestCoordinator implements RequestCoordinator {

    private static final String PROCESSING = "PROCESSING";
    private static final String COMPLETED = "COMPLETED";
    private static final int MAX_CONTENTION_RETRIES = 2;

    private static final String SELECT_RECORD = """
            SELECT fingerprint_version, request_fingerprint, status, claim_token, lease_expires_at,
                   attempt_count, completed_at, memory_id, image_upload_id, report_id
            FROM api_idempotency_records
            WHERE actor_id = ?
              AND request_method = ?
              AND request_path = ?
              AND idempotency_key = ?
            """;

    private static final String SELECT_RECORD_NOWAIT = SELECT_RECORD + " FOR UPDATE NOWAIT";

    private static final String INSERT_PROCESSING = """
            INSERT INTO api_idempotency_records (
                actor_id, request_method, request_path, idempotency_key,
                fingerprint_version, request_fingerprint, status, claim_token,
                lease_expires_at, attempt_count, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'PROCESSING', ?, ?, 1, ?)
            ON CONFLICT DO NOTHING
            RETURNING lease_expires_at
            """;

    private static final String RECLAIM_EXPIRED = """
            UPDATE api_idempotency_records
            SET claim_token = ?,
                lease_expires_at = ?,
                attempt_count = ?,
                completed_at = NULL,
                memory_id = NULL,
                image_upload_id = NULL,
                report_id = NULL
            WHERE actor_id = ?
              AND request_method = ?
              AND request_path = ?
              AND idempotency_key = ?
              AND fingerprint_version = ?
              AND request_fingerprint = ?
              AND status = 'PROCESSING'
              AND claim_token = ?
              AND attempt_count = ?
              AND lease_expires_at <= ?
            RETURNING lease_expires_at
            """;

    private static final String COMPLETE_RECORD = """
            UPDATE api_idempotency_records
            SET status = 'COMPLETED',
                claim_token = NULL,
                lease_expires_at = NULL,
                completed_at = ?,
                memory_id = ?,
                image_upload_id = ?,
                report_id = ?
            WHERE actor_id = ?
              AND request_method = ?
              AND request_path = ?
              AND idempotency_key = ?
              AND fingerprint_version = ?
              AND request_fingerprint = ?
              AND status = 'PROCESSING'
              AND claim_token = ?
              AND attempt_count = ?
              AND lease_expires_at > ?
            """;

    private static final String DELETE_CURRENT_CLAIM = """
            DELETE FROM api_idempotency_records
            WHERE actor_id = ?
              AND request_method = ?
              AND request_path = ?
              AND idempotency_key = ?
              AND fingerprint_version = ?
              AND request_fingerprint = ?
              AND status = 'PROCESSING'
              AND claim_token = ?
              AND attempt_count = ?
            """;

    private final DataSource dataSource;
    private final EntityManager entityManager;
    private final RequestCoordinationProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PersistentRequestCoordinator(
            DataSource dataSource,
            EntityManager entityManager,
            PlatformTransactionManager transactionManager,
            RequestCoordinationProperties properties,
            Clock clock
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");

        TransactionTemplate template = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.transactions = template;
    }

    @Override
    public Admission claim(Scope scope, Fingerprint fingerprint) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(fingerprint, "fingerprint");
        requireNoAmbientTransaction();

        try {
            for (int retry = 0; retry < MAX_CONTENTION_RETRIES; retry++) {
                StoredRequest current = readCurrent(scope);
                if (current == null) {
                    Claim inserted = insertIfAbsent(scope, fingerprint);
                    if (inserted != null) {
                        return new Claimed(inserted);
                    }
                    continue;
                }

                requireMatchingFingerprint(current, fingerprint);
                if (COMPLETED.equals(current.status())) {
                    return new Replay(completedResource(scope, current));
                }

                requireProcessingShape(current);
                Instant now = currentInstant();
                if (current.leaseExpiresAt().isAfter(now)) {
                    throw requestInProgress(current, now);
                }

                try {
                    Admission reclaimed = reclaimExpired(scope, fingerprint);
                    if (reclaimed != null) {
                        return reclaimed;
                    }
                } catch (LockUnavailable ignored) {
                    Admission refreshed = claimAfterLockFailure(scope, fingerprint);
                    if (refreshed != null) {
                        return refreshed;
                    }
                }
            }
            throw unavailable();
        } catch (ContractError error) {
            throw error;
        } catch (RuntimeException ignored) {
            throw unavailable();
        }
    }

    @Override
    public Completion complete(Claim claim, Supplier<ResourceRef> createResource) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(createResource, "createResource");
        requireNoAmbientTransaction();

        try {
            return completeInternal(claim, createResource);
        } catch (ContractError error) {
            throw error;
        } catch (RuntimeException ignored) {
            throw unavailable();
        }
    }

    @Override
    public boolean abandon(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        requireNoAmbientTransaction();

        try {
            try {
                return inNewTransaction(() -> useConnection(connection -> removeIfCurrent(connection, claim)));
            } catch (LockUnavailable ignored) {
                // A separate READ COMMITTED snapshot deliberately follows a NOWAIT failure. It never guesses
                // whether a finalizer committed, and the boolean contract remains a safe no-op for this caller.
                readCurrent(claim.scope());
                return false;
            }
        } catch (ContractError error) {
            throw error;
        } catch (RuntimeException ignored) {
            throw unavailable();
        }
    }

    private Admission claimAfterLockFailure(Scope scope, Fingerprint fingerprint) {
        StoredRequest refreshed = readCurrent(scope);
        if (refreshed == null) {
            return null;
        }

        requireMatchingFingerprint(refreshed, fingerprint);
        if (COMPLETED.equals(refreshed.status())) {
            return new Replay(completedResource(scope, refreshed));
        }

        // The conflicting owner may still hold an expired row while it finalizes. The persisted deadline,
        // rather than a newly configured lease, gives the required zero-second retry value in that case.
        requireProcessingShape(refreshed);
        throw requestInProgress(refreshed, currentInstant());
    }

    private Claim insertIfAbsent(Scope scope, Fingerprint fingerprint) {
        return inNewTransaction(() -> {
            Instant now = currentInstant();
            LeaseWindow lease = configuredLease(now);
            UUID token = UUID.randomUUID();

            return useConnection(connection -> {
                try (PreparedStatement statement = connection.prepareStatement(INSERT_PROCESSING)) {
                    bindScope(statement, 1, scope);
                    statement.setInt(5, fingerprint.version());
                    statement.setBytes(6, fingerprint.digest());
                    statement.setObject(7, token);
                    setInstant(statement, 8, lease.expiresAt());
                    setInstant(statement, 9, lease.startedAt());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            return null;
                        }
                        Instant storedExpiry = instantAt(result, 1);
                        requirePersistedLease(storedExpiry, lease);
                        return new Claim(scope, fingerprint, token, 1, storedExpiry);
                    }
                }
            });
        });
    }

    private Admission reclaimExpired(Scope scope, Fingerprint fingerprint) {
        return inNewTransaction(() -> useConnection(connection -> {
            StoredRequest current = selectRecord(connection, scope, true);
            if (current == null) {
                return null;
            }

            requireMatchingFingerprint(current, fingerprint);
            if (COMPLETED.equals(current.status())) {
                return new Replay(completedResource(scope, current));
            }

            requireProcessingShape(current);
            Instant now = currentInstant();
            if (current.leaseExpiresAt().isAfter(now)) {
                throw requestInProgress(current, now);
            }

            int nextAttempt = nextAttempt(current.attemptCount());
            LeaseWindow nextLease = configuredLease(now);
            UUID nextToken = newToken(current.claimToken());
            Instant storedExpiry = renewExpiredClaim(
                    connection, scope, fingerprint, current, nextToken, nextAttempt, nextLease);
            requirePersistedLease(storedExpiry, nextLease);
            return new Claimed(new Claim(scope, fingerprint, nextToken, nextAttempt, storedExpiry));
        }));
    }

    private Completion completeInternal(Claim claim, Supplier<ResourceRef> createResource) {
        CompletionTransaction transaction = new CompletionTransaction();
        try {
            return inNewTransaction(() -> {
                transaction.registerSynchronization();
                Completion completion = completeLocked(claim, createResource, transaction);
                transaction.markWorkReturned();
                return completion;
            });
        } catch (LockUnavailable ignored) {
            return completionAfterLockFailure(claim);
        } catch (RuntimeException failure) {
            throw failureAfterCompletionTransaction(failure, claim, transaction);
        }
    }

    private Completion completeLocked(
            Claim claim,
            Supplier<ResourceRef> createResource,
            CompletionTransaction transaction
    ) {
        return useConnection(connection -> {
            StoredRequest current = selectRecord(connection, claim.scope(), true);
            if (current == null) {
                throw unavailable();
            }

            requireMatchingFingerprint(current, claim.fingerprint());
            if (COMPLETED.equals(current.status())) {
                return new Completion(CompletionKind.REPLAY, completedResource(claim.scope(), current));
            }

            requireProcessingShape(current);
            Instant now = currentInstant();
            if (!isCurrentOwner(current, claim)) {
                if (current.leaseExpiresAt().isAfter(now)) {
                    throw requestInProgress(current, now);
                }
                throw unavailable();
            }
            if (!current.leaseExpiresAt().isAfter(now)) {
                throw unavailable();
            }

            transaction.markOwnershipVerified();
            ResourceRef resource = createResource.get();
            if (resource == null || resource.route() != claim.scope().route()) {
                throw CompletionFenceFailure.INSTANCE;
            }

            // The callback may use repositories rather than saveAndFlush. Force its resource INSERT before
            // the route-specific foreign-key update below, all under this same request-row lock.
            entityManager.flush();
            Instant completionTime = currentInstant();
            int updated = completeRecord(connection, claim, resource, completionTime);
            if (updated != 1) {
                throw CompletionFenceFailure.INSTANCE;
            }
            return new Completion(CompletionKind.CREATED, resource);
        });
    }

    private Completion completionAfterLockFailure(Claim claim) {
        StoredRequest refreshed = readCurrent(claim.scope());
        if (refreshed == null) {
            throw unavailable();
        }

        requireMatchingFingerprint(refreshed, claim.fingerprint());
        if (COMPLETED.equals(refreshed.status())) {
            return new Completion(CompletionKind.REPLAY, completedResource(claim.scope(), refreshed));
        }

        // Do not run the stale callback after a lock conflict. A finalizer holding an expired row still
        // produces REQUEST_IN_PROGRESS with Retry-After: 0 until a later request can observe its outcome.
        requireProcessingShape(refreshed);
        throw requestInProgress(refreshed, currentInstant());
    }

    private ContractError failureAfterCompletionTransaction(
            RuntimeException failure,
            Claim claim,
            CompletionTransaction transaction
    ) {
        // A callback that returned normally reached commit. A commit exception is deliberately not inferred
        // to be a rollback, even if a driver later reports rollback completion.
        if (!transaction.knownRollbackBeforeCommit()) {
            return unavailable();
        }

        if (transaction.ownershipVerified()) {
            cleanupKnownRollback(claim);
        }
        return publicCallbackFailure(failure);
    }

    private void cleanupKnownRollback(Claim claim) {
        try {
            inNewTransaction(() -> useConnection(connection -> removeIfCurrent(connection, claim)));
        } catch (RuntimeException ignored) {
            // Cleanup is a best-effort CAS only after a known rollback. Retaining PROCESSING is safer than
            // deleting a row when a later transaction has already recovered this key.
        }
    }

    private boolean removeIfCurrent(Connection connection, Claim claim) throws SQLException {
        StoredRequest current = selectRecord(connection, claim.scope(), true);
        if (current == null || !matchesClaim(current, claim)) {
            return false;
        }
        return deleteCurrentClaim(connection, claim) == 1;
    }

    private StoredRequest readCurrent(Scope scope) {
        return inNewTransaction(() -> useConnection(connection -> selectRecord(connection, scope, false)));
    }

    private StoredRequest selectRecord(Connection connection, Scope scope, boolean nowait) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(nowait ? SELECT_RECORD_NOWAIT : SELECT_RECORD)) {
            bindScope(statement, 1, scope);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new StoredRequest(
                        result.getInt(1),
                        result.getBytes(2),
                        result.getString(3),
                        result.getObject(4, UUID.class),
                        instantAt(result, 5),
                        result.getInt(6),
                        instantAt(result, 7),
                        result.getObject(8, UUID.class),
                        result.getObject(9, UUID.class),
                        result.getObject(10, UUID.class));
            }
        }
    }

    private Instant renewExpiredClaim(
            Connection connection,
            Scope scope,
            Fingerprint fingerprint,
            StoredRequest current,
            UUID nextToken,
            int nextAttempt,
            LeaseWindow nextLease
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(RECLAIM_EXPIRED)) {
            statement.setObject(1, nextToken);
            setInstant(statement, 2, nextLease.expiresAt());
            statement.setInt(3, nextAttempt);
            bindScope(statement, 4, scope);
            statement.setInt(8, fingerprint.version());
            statement.setBytes(9, fingerprint.digest());
            statement.setObject(10, current.claimToken());
            statement.setInt(11, current.attemptCount());
            setInstant(statement, 12, nextLease.startedAt());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw CompletionFenceFailure.INSTANCE;
                }
                return instantAt(result, 1);
            }
        }
    }

    private int completeRecord(
            Connection connection,
            Claim claim,
            ResourceRef resource,
            Instant completionTime
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COMPLETE_RECORD)) {
            setInstant(statement, 1, completionTime);
            setUuidOrNull(statement, 2, resource.route() == Route.MEMORIES ? resource.id() : null);
            setUuidOrNull(statement, 3, resource.route() == Route.IMAGES ? resource.id() : null);
            setUuidOrNull(statement, 4, resource.route() == Route.REPORTS ? resource.id() : null);
            bindScope(statement, 5, claim.scope());
            statement.setInt(9, claim.fingerprint().version());
            statement.setBytes(10, claim.fingerprint().digest());
            statement.setObject(11, claim.token());
            statement.setInt(12, claim.attempt());
            setInstant(statement, 13, completionTime);
            return statement.executeUpdate();
        }
    }

    private int deleteCurrentClaim(Connection connection, Claim claim) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_CURRENT_CLAIM)) {
            bindScope(statement, 1, claim.scope());
            statement.setInt(5, claim.fingerprint().version());
            statement.setBytes(6, claim.fingerprint().digest());
            statement.setObject(7, claim.token());
            statement.setInt(8, claim.attempt());
            return statement.executeUpdate();
        }
    }

    private LeaseWindow configuredLease(Instant now) {
        Duration duration = properties.leaseDuration();
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw configurationUnavailable();
        }

        try {
            Instant expiresAt = now.plus(duration);
            if (!expiresAt.isAfter(now)) {
                throw configurationUnavailable();
            }
            // JDBC uses OffsetDateTime for PostgreSQL timestamptz. Validate both endpoints before changing a
            // row so an Instant outside that representation is a configuration failure rather than a partial claim.
            OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
            OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
            if (ceilSeconds(expiresAt, now) < 1) {
                throw configurationUnavailable();
            }
            return new LeaseWindow(now, expiresAt);
        } catch (DateTimeException | ArithmeticException ignored) {
            throw configurationUnavailable();
        }
    }

    private static void requirePersistedLease(Instant storedExpiry, LeaseWindow lease) {
        if (storedExpiry == null || !storedExpiry.isAfter(lease.startedAt())) {
            throw configurationUnavailable();
        }
        try {
            if (ceilSeconds(storedExpiry, lease.startedAt()) < 1) {
                throw configurationUnavailable();
            }
        } catch (DateTimeException | ArithmeticException ignored) {
            throw configurationUnavailable();
        }
    }

    private static int nextAttempt(int currentAttempt) {
        if (currentAttempt < 1 || currentAttempt == Integer.MAX_VALUE) {
            throw unavailable();
        }
        return currentAttempt + 1;
    }

    private static UUID newToken(UUID priorToken) {
        UUID token;
        do {
            token = UUID.randomUUID();
        } while (token.equals(priorToken));
        return token;
    }

    private static void requireMatchingFingerprint(StoredRequest request, Fingerprint fingerprint) {
        byte[] requestedDigest = fingerprint.digest();
        if (request.fingerprintVersion() < 1 || request.fingerprintDigest() == null
                || request.fingerprintDigest().length != 32) {
            throw unavailable();
        }
        if (request.fingerprintVersion() != fingerprint.version()
                || !Arrays.equals(request.fingerprintDigest(), requestedDigest)) {
            throw ContractError.of(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
    }

    private static void requireProcessingShape(StoredRequest request) {
        if (!PROCESSING.equals(request.status()) || request.claimToken() == null || request.leaseExpiresAt() == null
                || request.attemptCount() < 1 || request.completedAt() != null
                || request.memoryId() != null || request.imageUploadId() != null || request.reportId() != null) {
            throw unavailable();
        }
    }

    private static ResourceRef completedResource(Scope scope, StoredRequest request) {
        if (!COMPLETED.equals(request.status()) || request.claimToken() != null || request.leaseExpiresAt() != null
                || request.attemptCount() < 1 || request.completedAt() == null) {
            throw unavailable();
        }

        return switch (scope.route()) {
            case MEMORIES -> {
                if (request.memoryId() == null || request.imageUploadId() != null || request.reportId() != null) {
                    throw unavailable();
                }
                yield new ResourceRef(Route.MEMORIES, request.memoryId());
            }
            case IMAGES -> {
                if (request.memoryId() != null || request.imageUploadId() == null || request.reportId() != null) {
                    throw unavailable();
                }
                yield new ResourceRef(Route.IMAGES, request.imageUploadId());
            }
            case REPORTS -> {
                if (request.memoryId() != null || request.imageUploadId() != null || request.reportId() == null) {
                    throw unavailable();
                }
                yield new ResourceRef(Route.REPORTS, request.reportId());
            }
        };
    }

    private static boolean isCurrentOwner(StoredRequest request, Claim claim) {
        return request.attemptCount() == claim.attempt() && Objects.equals(request.claimToken(), claim.token());
    }

    private static boolean matchesClaim(StoredRequest request, Claim claim) {
        return PROCESSING.equals(request.status())
                && request.fingerprintVersion() == claim.fingerprint().version()
                && Arrays.equals(request.fingerprintDigest(), claim.fingerprint().digest())
                && isCurrentOwner(request, claim);
    }

    private static ContractError requestInProgress(StoredRequest request, Instant now) {
        requireProcessingShape(request);
        try {
            return ContractError.retryAfter(ErrorCode.REQUEST_IN_PROGRESS,
                    ceilSeconds(request.leaseExpiresAt(), now));
        } catch (DateTimeException | ArithmeticException ignored) {
            throw unavailable();
        }
    }

    private static int ceilSeconds(Instant deadline, Instant now) {
        if (!deadline.isAfter(now)) {
            return 0;
        }
        Duration remaining = Duration.between(now, deadline);
        long rounded = Math.addExact(remaining.getSeconds(), remaining.getNano() == 0 ? 0L : 1L);
        return Math.toIntExact(rounded);
    }

    private static void requireNoAmbientTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()) {
            throw unavailable();
        }
    }

    private Instant currentInstant() {
        Instant now = clock.instant();
        if (now == null) {
            throw unavailable();
        }
        return now;
    }

    private <T> T inNewTransaction(TransactionWork<T> work) {
        return transactions.execute(ignored -> work.run());
    }

    private <T> T useConnection(SqlWork<T> work) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            return work.run(connection);
        } catch (SQLException failure) {
            if (isLockUnavailable(failure)) {
                throw LockUnavailable.INSTANCE;
            }
            throw DatabaseFailure.INSTANCE;
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static boolean isLockUnavailable(SQLException failure) {
        for (SQLException current = failure; current != null; current = current.getNextException()) {
            if ("55P03".equals(current.getSQLState())) {
                return true;
            }
        }
        return failure.getCause() instanceof SQLException cause && isLockUnavailable(cause);
    }

    private static void bindScope(PreparedStatement statement, int firstIndex, Scope scope) throws SQLException {
        statement.setObject(firstIndex, scope.actorId());
        statement.setString(firstIndex + 1, scope.route().method());
        statement.setString(firstIndex + 2, scope.route().path());
        statement.setObject(firstIndex + 3, scope.key());
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setObject(index, OffsetDateTime.ofInstant(value, ZoneOffset.UTC));
    }

    private static Instant instantAt(ResultSet result, int index) throws SQLException {
        OffsetDateTime value = result.getObject(index, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static void setUuidOrNull(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, value);
        }
    }

    private static ContractError publicCallbackFailure(RuntimeException failure) {
        if (failure instanceof ContractError contractError) {
            return withoutCause(contractError);
        }
        if (failure instanceof ResponseStatusException responseStatusException) {
            return withoutCause(ApiErrorWriter.fromResponseStatus(responseStatusException));
        }
        return unavailable();
    }

    private static ContractError withoutCause(ContractError error) {
        return new ContractError(
                error.code(),
                error.code().defaultMessage(),
                error.fieldErrors(),
                error.retryAfterSeconds(),
                null);
    }

    private static ContractError configurationUnavailable() {
        return ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
    }

    private static ContractError unavailable() {
        return ContractError.of(ErrorCode.SERVICE_UNAVAILABLE);
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T run();
    }

    @FunctionalInterface
    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private record LeaseWindow(Instant startedAt, Instant expiresAt) {
    }

    private record StoredRequest(
            int fingerprintVersion,
            byte[] fingerprintDigest,
            String status,
            UUID claimToken,
            Instant leaseExpiresAt,
            int attemptCount,
            Instant completedAt,
            UUID memoryId,
            UUID imageUploadId,
            UUID reportId
    ) {
    }

    private static final class CompletionTransaction {
        private boolean ownershipVerified;
        private boolean workReturned;
        private boolean synchronizationRegistered;
        private int completionStatus = TransactionSynchronization.STATUS_UNKNOWN;

        private void registerSynchronization() {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                return;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    CompletionTransaction.this.completionStatus = status;
                }
            });
            synchronizationRegistered = true;
        }

        private void markOwnershipVerified() {
            ownershipVerified = true;
        }

        private boolean ownershipVerified() {
            return ownershipVerified;
        }

        private void markWorkReturned() {
            workReturned = true;
        }

        private boolean knownRollbackBeforeCommit() {
            return !workReturned && synchronizationRegistered
                    && completionStatus == TransactionSynchronization.STATUS_ROLLED_BACK;
        }
    }

    private static final class LockUnavailable extends RuntimeException {
        private static final LockUnavailable INSTANCE = new LockUnavailable();

        private LockUnavailable() {
            super(null, null, false, false);
        }
    }

    private static final class DatabaseFailure extends RuntimeException {
        private static final DatabaseFailure INSTANCE = new DatabaseFailure();

        private DatabaseFailure() {
            super(null, null, false, false);
        }
    }

    private static final class CompletionFenceFailure extends RuntimeException {
        private static final CompletionFenceFailure INSTANCE = new CompletionFenceFailure();

        private CompletionFenceFailure() {
            super(null, null, false, false);
        }
    }
}
