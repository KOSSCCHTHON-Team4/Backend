package team4.emotionmap.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "email_password_credentials")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EmailPasswordCredential {

    @Id
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String email;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String emailLookupKey;

    @Column(nullable = false, columnDefinition = "text")
    private String passwordHash;

    @Builder.Default
    @Column(nullable = false)
    private Instant passwordChangedAt = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    /** Initial credential provisioning must use the same lookup normalization as login. */
    public static String normalizeEmailLookupKey(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
