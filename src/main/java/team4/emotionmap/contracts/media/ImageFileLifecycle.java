package team4.emotionmap.contracts.media;

/** File ownership checks coupled to the same database transaction as resource publication. */
public interface ImageFileLifecycle {

    /** Requires an active transaction; protects local IO through the actual transaction outcome. */
    void protectWritesInCurrentTransaction();

    /** Uses a new READ COMMITTED transaction, verified root binding, both fences and all references. */
    CleanupResult deleteIfUnreferenced(String storageKey);

    enum CleanupResult {
        DELETED, ABSENT, REFERENCED, BUSY
    }
}
