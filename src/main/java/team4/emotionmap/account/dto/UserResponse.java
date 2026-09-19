package team4.emotionmap.account.dto;

import java.time.Instant;
import java.util.UUID;
import team4.emotionmap.account.User;
import team4.emotionmap.account.UserPreferenceVersion;

public record UserResponse(
        UUID id,
        String email,
        boolean hasOnboarded,
        Mailbox mailbox,
        Atmospheres atmospheres,
        String preferenceDescription,
        String preferenceVersion,
        Instant preferenceEffectiveAt
) {
    public static UserResponse from(User user, String email, UserPreferenceVersion preference) {
        boolean onboarded = user.getMailboxEnabledAt() != null && preference != null;
        return new UserResponse(user.getId(), email, onboarded,
                onboarded ? new Mailbox(user.getMailboxLat(), user.getMailboxLng(), user.getMailboxEnabledAt()) : null,
                onboarded ? Atmospheres.from(preference) : null,
                onboarded ? preference.getDescription() : null,
                onboarded ? preference.getRevision().toString() : null,
                onboarded ? preference.getEffectiveAt() : null);
    }

    public record Mailbox(Double lat, Double lng, Instant enabledAt) { }
}
