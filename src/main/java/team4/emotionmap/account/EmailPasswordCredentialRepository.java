package team4.emotionmap.account;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailPasswordCredentialRepository extends JpaRepository<EmailPasswordCredential, UUID> {
    Optional<EmailPasswordCredential> findByEmailLookupKey(String emailLookupKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from EmailPasswordCredential credential where credential.emailLookupKey = :emailLookupKey")
    Optional<EmailPasswordCredential> findByEmailLookupKeyForUpdate(@Param("emailLookupKey") String emailLookupKey);
}
