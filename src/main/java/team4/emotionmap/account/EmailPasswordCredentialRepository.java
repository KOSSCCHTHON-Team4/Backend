package team4.emotionmap.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailPasswordCredentialRepository extends JpaRepository<EmailPasswordCredential, UUID> {
    Optional<EmailPasswordCredential> findByEmailLookupKey(String emailLookupKey);
}
