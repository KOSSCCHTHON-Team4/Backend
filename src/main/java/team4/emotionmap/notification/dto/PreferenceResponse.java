package team4.emotionmap.notification.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import team4.emotionmap.contracts.dictionary.VibeVector;
import team4.emotionmap.notification.Preference;

public record PreferenceResponse(UUID id, List<String> cards, VibeVector vibe, String preferenceText,
                                 List<String> categoryFilter, double centerLat, double centerLng, int radiusM,
                                 boolean active, Instant createdAt, Instant updatedAt) {
    public static PreferenceResponse from(Preference p) {
        return new PreferenceResponse(p.getId(), p.getCards(), p.vector(), p.getPreferenceText(),
                p.getCategoryFilter() == null ? List.of() : p.getCategoryFilter(),
                p.getCenterLat(), p.getCenterLng(), p.getRadiusM(), Boolean.TRUE.equals(p.getActive()),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
