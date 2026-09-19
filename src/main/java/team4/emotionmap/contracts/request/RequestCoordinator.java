package team4.emotionmap.contracts.request;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Durable request ownership; entry points require no ambient business transaction. */
public interface RequestCoordinator {

    Admission claim(Scope scope, Fingerprint fingerprint);

    /** Runs resource creation and completion in the same transaction, at most once for this claim. */
    Completion complete(Claim claim, Supplier<ResourceRef> createResource);

    /** Removes only this still-processing owner after a known pre-completion failure. */
    boolean abandon(Claim claim);

    enum Route {
        MEMORIES("/v1/memories"),
        IMAGES("/v1/images"),
        REPORTS("/v1/reports");

        private final String path;

        Route(String path) {
            this.path = path;
        }

        public String method() {
            return "POST";
        }

        public String path() {
            return path;
        }
    }

    record Scope(UUID actorId, Route route, UUID key) {
        public Scope {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(key, "key");
        }
    }

    final class Fingerprint {
        private final int version;
        private final byte[] digest;

        public Fingerprint(int version, byte[] digest) {
            Objects.requireNonNull(digest, "digest");
            if (version < 1 || digest.length != 32) {
                throw new IllegalArgumentException("Fingerprint requires a positive version and SHA-256 digest");
            }
            this.version = version;
            this.digest = digest.clone();
        }

        public int version() {
            return version;
        }

        public byte[] digest() {
            return digest.clone();
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof Fingerprint value
                    && version == value.version && Arrays.equals(digest, value.digest);
        }

        @Override
        public int hashCode() {
            return 31 * Integer.hashCode(version) + Arrays.hashCode(digest);
        }

        @Override
        public String toString() {
            return "Fingerprint[version=" + version + ", digest=REDACTED]";
        }
    }

    record Claim(Scope scope, Fingerprint fingerprint, UUID token, int attempt, Instant leaseExpiresAt) {
        public Claim {
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(fingerprint, "fingerprint");
            Objects.requireNonNull(token, "token");
            Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
            if (attempt < 1) {
                throw new IllegalArgumentException("Claim attempt must be positive");
            }
        }
    }

    record ResourceRef(Route route, UUID id) {
        public ResourceRef {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(id, "id");
        }
    }

    sealed interface Admission permits Claimed, Replay {
    }

    record Claimed(Claim claim) implements Admission {
        public Claimed {
            Objects.requireNonNull(claim, "claim");
        }
    }

    record Replay(ResourceRef resource) implements Admission {
        public Replay {
            Objects.requireNonNull(resource, "resource");
        }
    }

    enum CompletionKind {
        CREATED, REPLAY
    }

    record Completion(CompletionKind kind, ResourceRef resource) {
        public Completion {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(resource, "resource");
        }
    }
}
