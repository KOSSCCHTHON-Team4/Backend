package team4.emotionmap.media;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ImageUploadRepository extends JpaRepository<ImageUpload, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ImageUpload i where i.id = :id")
    Optional<ImageUpload> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByStoragePath(String storagePath);

    @Query(value = "select exists (select 1 from image_uploads)", nativeQuery = true)
    boolean existsAnyImageUpload();

    @Query(value = """
            select *
            from image_uploads
            where status = 'STAGED'
              and expires_at <= cast(:now as timestamptz)
              and (
                  cast(:cursorExpiresAt as timestamptz) is null
                  or (expires_at, id) > (
                      cast(:cursorExpiresAt as timestamptz),
                      cast(:cursorId as uuid)
                  )
              )
            order by expires_at, id
            for update skip locked
            limit :batchSize
            """, nativeQuery = true)
    List<ImageUpload> findDueStagedAfterForUpdateSkipLocked(@Param("now") Instant now,
                                                              @Param("cursorExpiresAt") Instant cursorExpiresAt,
                                                              @Param("cursorId") UUID cursorId,
                                                              @Param("batchSize") int batchSize);

    @Query(value = """
            select exists (
                select 1
                from image_uploads
                where status = 'STAGED'
                  and expires_at <= cast(:now as timestamptz)
                  and (
                      cast(:cursorExpiresAt as timestamptz) is null
                      or (expires_at, id) > (
                          cast(:cursorExpiresAt as timestamptz),
                          cast(:cursorId as uuid)
                      )
                  )
            )
            """, nativeQuery = true)
    boolean existsDueStagedAfter(@Param("now") Instant now,
                                 @Param("cursorExpiresAt") Instant cursorExpiresAt,
                                 @Param("cursorId") UUID cursorId);
}
