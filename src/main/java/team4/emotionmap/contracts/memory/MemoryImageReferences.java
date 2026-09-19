package team4.emotionmap.contracts.memory;

/** Joins the caller's transaction and includes ACTIVE, HIDDEN and DELETED memory references. */
public interface MemoryImageReferences {

    boolean existsReference(String storageKey);

    /** Empty-root activation must not bypass memory ownership or ignore historical references. */
    boolean existsAnyReference();
}
