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
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team4.emotionmap.contracts.account.AccountStatus;
import team4.emotionmap.contracts.geo.GeoPoint;

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

    @Column(name = "mailbox_lat")
    private Double mailboxLat;

    @Column(name = "mailbox_lng")
    private Double mailboxLng;

    @Column(name = "mailbox_enabled_at")
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

    public GeoPoint mailbox() {
        if (mailboxLat == null || mailboxLng == null || mailboxEnabledAt == null) {
            throw new IllegalStateException("Mailbox is not configured");
        }
        return normalizedMailbox(mailboxLat, mailboxLng);
    }

    public void recordPreferenceChange(Instant changedAt) {
        if (mailboxEnabledAt == null || changedAt == null) {
            throw new IllegalStateException("Preference changes require an onboarded account and timestamp");
        }
        updatedAt = changedAt;
    }

    public void completeOnboarding(GeoPoint mailbox, Instant enabledAt) {
        if (mailboxLat != null || mailboxLng != null || mailboxEnabledAt != null) {
            throw new IllegalStateException("Mailbox is already configured");
        }
        assignMailbox(mailbox, enabledAt);
    }

    public boolean relocateMailbox(GeoPoint mailbox, Instant changedAt) {
        if (mailboxEnabledAt == null) {
            throw new IllegalStateException("Mailbox relocation requires an onboarded account");
        }
        Objects.requireNonNull(changedAt, "changedAt");
        GeoPoint requested = Objects.requireNonNull(mailbox, "mailbox");
        GeoPoint normalized = normalizedMailbox(requested.lat(), requested.lng());
        if (mailbox().equals(normalized)) {
            return false;
        }
        assignMailbox(normalized, changedAt);
        return true;
    }

    static GeoPoint normalizedMailbox(double lat, double lng) {
        return new GeoPoint(normalizeZero(lat), normalizeZero(lng));
    }

    private void assignMailbox(GeoPoint mailbox, Instant enabledAt) {
        GeoPoint requested = Objects.requireNonNull(mailbox, "mailbox");
        Instant timestamp = Objects.requireNonNull(enabledAt, "enabledAt");
        GeoPoint normalized = normalizedMailbox(requested.lat(), requested.lng());
        mailboxLat = normalized.lat();
        mailboxLng = normalized.lng();
        mailboxEnabledAt = timestamp;
        updatedAt = timestamp;
    }

    private static double normalizeZero(double value) {
        return value == 0.0d ? 0.0d : value;
    }
}
