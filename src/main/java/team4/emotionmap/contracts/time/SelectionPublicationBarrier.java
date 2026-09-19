package team4.emotionmap.contracts.time;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Serializes publication at a selection cutoff in the caller's writable PostgreSQL transaction.
 *
 * <p>Callers acquire {@link #lock()} before any business-row lock. {@link #publicationTime()} then
 * returns a database-authoritative, microsecond-precise logical time that is strictly after an
 * already sealed cutoff. The adapter never starts or commits a transaction on the caller's behalf.
 */
public interface SelectionPublicationBarrier {

    /** Acquires the singleton publication fence for the remainder of the current transaction. */
    void lock();

    /** Returns the logical publication time after {@link #lock()} acquired the fence. */
    Instant publicationTime();

    /** Seals the given service date's cutoff after database time has reached that cutoff. */
    void sealCutoff(LocalDate serviceDate);

    /** Returns PostgreSQL {@code clock_timestamp()} in the current transaction. */
    Instant databaseNow();
}
