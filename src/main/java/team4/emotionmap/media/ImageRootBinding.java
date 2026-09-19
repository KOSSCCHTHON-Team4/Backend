package team4.emotionmap.media;

import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.memory.MemoryImageReferences;

/**
 * Verifies the explicit database-to-root ownership binding and performs the one supported empty
 * root activation. Normal application startup never calls {@link #activateEmpty}.
 */
@Component
public class ImageRootBinding {

    static final String MARKER_FILE_NAME = ".emotionmap-storage.json";
    private static final int MARKER_MAX_BYTES = 256;
    private static final Pattern MARKER = Pattern.compile("\\A\\{\\\"formatVersion\\\":1,\\\"datasetId\\\":\\\""
            + "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\\",\\\"rootId\\\":\\\""
            + "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\\"\\}\\z");

    private final EntityManager entityManager;
    private final StorageProperties storageProperties;
    private final ImageUploadRepository imageUploadRepository;
    private final MemoryImageReferences memoryImageReferences;
    private final ImageFileFence imageFileFence;
    private final TransactionTemplate transactionTemplate;

    public ImageRootBinding(EntityManager entityManager, PlatformTransactionManager transactionManager,
                            StorageProperties storageProperties, ImageUploadRepository imageUploadRepository,
                            MemoryImageReferences memoryImageReferences, ImageFileFence imageFileFence) {
        this.entityManager = entityManager;
        this.storageProperties = storageProperties;
        this.imageUploadRepository = imageUploadRepository;
        this.memoryImageReferences = memoryImageReferences;
        this.imageFileFence = imageFileFence;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * Completes a fresh binding/locator/marker transaction before a caller opens a streaming file.
     * It does not create, adopt, or repair the configured root.
     */
    Path requireReadableRoot() {
        Path root = transactionTemplate.execute(status -> requireBoundInCurrentTransaction(entityManager,
                storageProperties, ErrorCode.IMAGE_FILE_UNAVAILABLE));
        if (root == null) {
            throw ContractError.of(ErrorCode.IMAGE_FILE_UNAVAILABLE);
        }
        return root;
    }

    /**
     * Explicitly binds only a new empty configured root to an unbound, empty dataset.
     *
     * <p>This method intentionally has no root argument: the configured storage root is the only
     * candidate it can initialize. A failure leaves partial marker/lock state in place so normal
     * startup remains fail-closed rather than guessing how to recover it.
     */
    public void activateEmpty(UUID expectedDatasetId, String expectedDatabase, String expectedSchema) {
        if (expectedDatasetId == null || isBlank(expectedDatabase) || isBlank(expectedSchema)) {
            throw new IllegalArgumentException("Image storage activation arguments are invalid");
        }
        Path configuredRoot = configuredRootForActivation();
        transactionTemplate.executeWithoutResult(status -> activateEmptyInTransaction(expectedDatasetId,
                expectedDatabase, expectedSchema, configuredRoot));
    }

    static Path requireBoundInCurrentTransaction(EntityManager entityManager, StorageProperties storageProperties,
                                                  ErrorCode unavailableCode) {
        try {
            requireSchemaObjects(entityManager, unavailableCode);
            DatabaseLocator current = observeLocator(entityManager, unavailableCode);
            Binding binding = lockBinding(entityManager, false, unavailableCode);
            if (!binding.isBound() || !binding.matches(current)) {
                throw ContractError.of(unavailableCode);
            }
            Path root = configuredExistingRoot(storageProperties, unavailableCode);
            Marker marker = readMarker(root, unavailableCode);
            if (!binding.datasetId.equals(marker.datasetId) || !binding.rootId.equals(marker.rootId)) {
                throw ContractError.of(unavailableCode);
            }
            Path lockFile = root.resolve(ImageFileFence.LOCK_FILE_NAME);
            if (!Files.isRegularFile(lockFile, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(lockFile)) {
                throw ContractError.of(unavailableCode);
            }
            return root;
        } catch (ContractError error) {
            throw error;
        } catch (IOException | RuntimeException ignored) {
            throw ContractError.of(unavailableCode);
        }
    }

    private void activateEmptyInTransaction(UUID expectedDatasetId, String expectedDatabase,
                                            String expectedSchema, Path configuredRoot) {
        requireSchemaObjects(entityManager, ErrorCode.CONFIGURATION_UNAVAILABLE);
        DatabaseLocator current = observeLocator(entityManager, ErrorCode.CONFIGURATION_UNAVAILABLE);
        Binding binding = lockBinding(entityManager, true, ErrorCode.CONFIGURATION_UNAVAILABLE);
        if (!binding.isUnbound() || !binding.datasetId.equals(expectedDatasetId)
                || !expectedDatabase.equals(current.databaseName) || !expectedSchema.equals(current.schemaName)) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
        if (imageUploadRepository.existsAnyImageUpload() || memoryImageReferences.existsAnyReference()) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        }
        Path root = prepareConfiguredRootForActivation(configuredRoot);
        requireNoEntriesBeforeActivationLock(root);

        ImageFileFence.RootGuard rootGuard = imageFileFence.acquireActivationExclusive(root);
        boolean completionRegistered = false;
        try {
            imageFileFence.releaseAfterTransactionCompletion(rootGuard);
            completionRegistered = true;
            requireOnlyNewLockFile(root);
            UUID rootId = UUID.randomUUID();
            writeMarker(root, binding.datasetId, rootId);
            int updated = entityManager.createNativeQuery("""
                    update image_storage_binding
                    set root_id = :rootId,
                        bound_database_name = :databaseName,
                        bound_database_oid = :databaseOid,
                        bound_server_address = cast(:serverAddress as inet),
                        bound_server_port = :serverPort,
                        bound_schema_name = :schemaName,
                        bound_schema_oid = :schemaOid
                    where singleton = true and root_id is null
                    """)
                    .setParameter("rootId", rootId)
                    .setParameter("databaseName", current.databaseName)
                    .setParameter("databaseOid", current.databaseOid)
                    .setParameter("serverAddress", current.serverAddress)
                    .setParameter("serverPort", current.serverPort)
                    .setParameter("schemaName", current.schemaName)
                    .setParameter("schemaOid", current.schemaOid)
                    .executeUpdate();
            if (updated != 1) {
                throw ContractError.of(ErrorCode.INVALID_REQUEST);
            }
        } finally {
            if (!completionRegistered) {
                rootGuard.close();
            }
        }
    }

    private static Binding lockBinding(EntityManager entityManager, boolean forUpdate, ErrorCode unavailableCode) {
        try {
            String query = """
                    select dataset_id, root_id, bound_database_name, bound_database_oid,
                           cast(bound_server_address as text), bound_server_port, bound_schema_name, bound_schema_oid
                    from image_storage_binding
                    where singleton = true
                    """ + " for " + (forUpdate ? "update" : "share");
            @SuppressWarnings("unchecked")
            List<Object[]> rows = entityManager.createNativeQuery(query).getResultList();
            if (rows.size() != 1) {
                throw ContractError.of(unavailableCode);
            }
            Object[] row = rows.getFirst();
            return new Binding(asUuid(row[0], unavailableCode), nullableUuid(row[1], unavailableCode),
                    nullableString(row[2]), nullableLong(row[3]), nullableString(row[4]), nullableInteger(row[5]),
                    nullableString(row[6]), nullableLong(row[7]));
        } catch (ContractError error) {
            throw error;
        } catch (RuntimeException ignored) {
            throw ContractError.of(unavailableCode);
        }
    }

    private static DatabaseLocator observeLocator(EntityManager entityManager, ErrorCode unavailableCode) {
        try {
            Object[] row = (Object[]) entityManager.createNativeQuery("""
                    select current_database(),
                           (select cast(oid as bigint) from pg_database where datname = current_database()),
                           cast(inet_server_addr() as text),
                           inet_server_port(),
                           current_schema(),
                           (select cast(oid as bigint) from pg_namespace where nspname = current_schema())
                    """).getSingleResult();
            String databaseName = requiredString(row[0], unavailableCode);
            Long databaseOid = nullableLong(row[1]);
            String serverAddress = nullableString(row[2]);
            Integer serverPort = nullableInteger(row[3]);
            String schemaName = nullableString(row[4]);
            Long schemaOid = nullableLong(row[5]);
            if (databaseOid == null || databaseOid < 1 || isBlank(serverAddress) || serverPort == null
                    || serverPort < 1 || serverPort > 65535 || isBlank(schemaName)
                    || schemaOid == null || schemaOid < 1) {
                throw ContractError.of(unavailableCode);
            }
            return new DatabaseLocator(databaseName, databaseOid, serverAddress, serverPort, schemaName, schemaOid);
        } catch (ContractError error) {
            throw error;
        } catch (RuntimeException ignored) {
            throw ContractError.of(unavailableCode);
        }
    }

    private static void requireSchemaObjects(EntityManager entityManager, ErrorCode unavailableCode) {
        try {
            Object result = entityManager.createNativeQuery("""
                    select to_regclass(format('%I.%I', current_schema(), 'image_storage_binding')) is not null
                       and to_regclass(format('%I.%I', current_schema(), 'image_uploads')) is not null
                       and to_regclass(format('%I.%I', current_schema(), 'memories')) is not null
                    """).getSingleResult();
            if (!Boolean.TRUE.equals(result)) {
                throw ContractError.of(unavailableCode);
            }
        } catch (ContractError error) {
            throw error;
        } catch (RuntimeException ignored) {
            throw ContractError.of(unavailableCode);
        }
    }

    private static Path configuredExistingRoot(StorageProperties storageProperties, ErrorCode unavailableCode)
            throws IOException {
        String configured = storageProperties.uploadDir();
        if (isBlank(configured)) {
            throw ContractError.of(unavailableCode);
        }
        Path root = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw ContractError.of(unavailableCode);
        }
        return root.toRealPath();
    }

    private Path configuredRootForActivation() {
        String configured = storageProperties.uploadDir();
        if (isBlank(configured)) {
            throw new IllegalArgumentException("Image storage root is invalid");
        }
        try {
            Path root = Path.of(configured);
            if (!root.isAbsolute()) {
                throw new IllegalArgumentException("Image storage root must be absolute for activation");
            }
            return root.toAbsolutePath().normalize();
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (RuntimeException ignored) {
            throw new IllegalArgumentException("Image storage root is invalid");
        }
    }

    private static Path prepareConfiguredRootForActivation(Path root) {
        try {
            if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
                    throw ContractError.of(ErrorCode.INVALID_REQUEST);
                }
            } else {
                Files.createDirectories(root);
            }
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
                throw ContractError.of(ErrorCode.INVALID_REQUEST);
            }
            return root.toRealPath();
        } catch (ContractError error) {
            throw error;
        } catch (FileAlreadyExistsException ignored) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        } catch (IOException | RuntimeException ignored) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
    }

    private static void requireNoEntriesBeforeActivationLock(Path root) {
        try (var entries = Files.list(root)) {
            if (entries.findAny().isPresent()) {
                throw ContractError.of(ErrorCode.INVALID_REQUEST);
            }
        } catch (ContractError error) {
            throw error;
        } catch (IOException ignored) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
    }

    private static void requireOnlyNewLockFile(Path root) {
        Path lockFile = root.resolve(ImageFileFence.LOCK_FILE_NAME);
        try (var entries = Files.list(root)) {
            boolean safe = entries.allMatch(entry -> entry.equals(lockFile));
            if (!safe || !Files.isRegularFile(lockFile, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(lockFile)) {
                throw ContractError.of(ErrorCode.INVALID_REQUEST);
            }
        } catch (ContractError error) {
            throw error;
        } catch (IOException ignored) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
    }

    private static void writeMarker(Path root, UUID datasetId, UUID rootId) {
        Path marker = root.resolve(MARKER_FILE_NAME);
        byte[] content = ("{\"formatVersion\":1,\"datasetId\":\"" + datasetId
                + "\",\"rootId\":\"" + rootId + "\"}").getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(marker, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
            forceDirectory(root);
        } catch (FileAlreadyExistsException ignored) {
            throw ContractError.of(ErrorCode.INVALID_REQUEST);
        } catch (IOException ignored) {
            throw ContractError.of(ErrorCode.CONFIGURATION_UNAVAILABLE);
        }
    }

    private static Marker readMarker(Path root, ErrorCode unavailableCode) throws IOException {
        Path marker = root.resolve(MARKER_FILE_NAME);
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(marker)
                || Files.size(marker) > MARKER_MAX_BYTES) {
            throw ContractError.of(unavailableCode);
        }
        Matcher matcher = MARKER.matcher(Files.readString(marker, StandardCharsets.UTF_8));
        if (!matcher.matches()) {
            throw ContractError.of(unavailableCode);
        }
        return new Marker(UUID.fromString(matcher.group(1)), UUID.fromString(matcher.group(2)));
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static UUID asUuid(Object value, ErrorCode unavailableCode) {
        UUID uuid = nullableUuid(value, unavailableCode);
        if (uuid == null) {
            throw ContractError.of(unavailableCode);
        }
        return uuid;
    }

    private static UUID nullableUuid(Object value, ErrorCode unavailableCode) {
        if (value == null) {
            return null;
        }
        try {
            return value instanceof UUID uuid ? uuid : UUID.fromString(value.toString());
        } catch (IllegalArgumentException ignored) {
            throw ContractError.of(unavailableCode);
        }
    }

    private static String requiredString(Object value, ErrorCode unavailableCode) {
        String string = nullableString(value);
        if (isBlank(string)) {
            throw ContractError.of(unavailableCode);
        }
        return string;
    }

    private static String nullableString(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static Integer nullableInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Binding(UUID datasetId, UUID rootId, String databaseName, Long databaseOid,
                           String serverAddress, Integer serverPort, String schemaName, Long schemaOid) {
        private boolean isUnbound() {
            return rootId == null && databaseName == null && databaseOid == null && serverAddress == null
                    && serverPort == null && schemaName == null && schemaOid == null;
        }

        private boolean isBound() {
            return rootId != null && !isBlank(databaseName) && databaseOid != null && databaseOid > 0
                    && !isBlank(serverAddress) && serverPort != null && serverPort >= 1 && serverPort <= 65535
                    && !isBlank(schemaName) && schemaOid != null && schemaOid > 0;
        }

        private boolean matches(DatabaseLocator locator) {
            return Objects.equals(databaseName, locator.databaseName)
                    && Objects.equals(databaseOid, locator.databaseOid)
                    && Objects.equals(serverAddress, locator.serverAddress)
                    && Objects.equals(serverPort, locator.serverPort)
                    && Objects.equals(schemaName, locator.schemaName)
                    && Objects.equals(schemaOid, locator.schemaOid);
        }
    }

    private record DatabaseLocator(String databaseName, long databaseOid, String serverAddress, int serverPort,
                                   String schemaName, long schemaOid) {
    }

    private record Marker(UUID datasetId, UUID rootId) {
    }
}
