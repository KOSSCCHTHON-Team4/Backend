package team4.emotionmap.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import team4.emotionmap.contracts.account.AccountStatus;
@Entity
@Table(name = "app_users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(columnDefinition = "text")
    private String nickname;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private AccountStatus accessStatus = AccountStatus.PENDING;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private AppRole appRole = AppRole.USER;

    private Double mailboxLat;
    private Double mailboxLng;
    private Instant mailboxEnabledAt;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public boolean isActive() {
        return accessStatus == AccountStatus.ACTIVE;
    }

    public void recordPreferenceChange(Instant changedAt) {
        if (mailboxEnabledAt == null || changedAt == null) {
            throw new IllegalStateException("Preference changes require an onboarded account and timestamp");
        }
        updatedAt = changedAt;
    }

    public void completeOnboarding(double lat, double lng, Instant enabledAt) {
        if (mailboxEnabledAt != null) {
            throw new IllegalStateException("Mailbox location is immutable after onboarding");
        }
        if (!Double.isFinite(lat) || lat < -90 || lat > 90
                || !Double.isFinite(lng) || lng < -180 || lng > 180 || enabledAt == null) {
            throw new IllegalArgumentException("Invalid mailbox location or enablement time");
        }
        mailboxLat = lat;
        mailboxLng = lng;
        mailboxEnabledAt = enabledAt;
        updatedAt = enabledAt;
    }
}
