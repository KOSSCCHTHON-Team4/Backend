package team4.emotionmap.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.dictionary.PreferenceCard;
import team4.emotionmap.contracts.dictionary.VibeVector;
import team4.emotionmap.contracts.geo.GeoPoint;

/**
 * 취향 알림 설정(기획 §9 Preference). 카드 2~4장 → 벡터 U(안 고른 축 0), 선택 자연어(2차 검증용),
 * 선택 카테고리 필터, 기준 위치·반경, 활성 여부.
 */
@Entity
@Table(name = "preferences")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Preference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** 카드 라벨 목록(축 라벨과 동일 문자열). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<String> cards;

    @Column(name = "vibe_crowd", nullable = false)
    private Short vibeCrowd;

    @Column(name = "vibe_spatial", nullable = false)
    private Short vibeSpatial;

    @Column(name = "vibe_company", nullable = false)
    private Short vibeCompany;

    @Column(name = "vibe_stay", nullable = false)
    private Short vibeStay;

    @Column(name = "preference_text", columnDefinition = "text")
    private String preferenceText;

    /** 카테고리 코드 목록. null/빈 목록 = 필터 없음. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "category_filter", columnDefinition = "jsonb")
    private List<String> categoryFilter;

    @Column(name = "center_lat", nullable = false)
    private Double centerLat;

    @Column(name = "center_lng", nullable = false)
    private Double centerLng;

    @Column(name = "radius_m", nullable = false)
    private Integer radiusM;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public VibeVector vector() {
        return new VibeVector(vibeCrowd, vibeSpatial, vibeCompany, vibeStay);
    }

    public GeoPoint center() {
        return new GeoPoint(centerLat, centerLng);
    }

    public boolean hasPreferenceText() {
        return preferenceText != null && !preferenceText.isBlank();
    }

    public boolean acceptsCategory(PlaceCategoryCode code) {
        if (categoryFilter == null || categoryFilter.isEmpty()) {
            return true;
        }
        return code != null && categoryFilter.contains(code.name());
    }

    public void update(List<PreferenceCard> newCards, String text, List<String> filter, Boolean isActive, Instant now) {
        VibeVector v = PreferenceCard.toVector(newCards);
        this.cards = newCards.stream().map(PreferenceCard::label).toList();
        this.vibeCrowd = (short) v.crowdLevel();
        this.vibeSpatial = (short) v.spatialFeel();
        this.vibeCompany = (short) v.companyFit();
        this.vibeStay = (short) v.stayStyle();
        this.preferenceText = text;
        this.categoryFilter = filter == null || filter.isEmpty() ? null : List.copyOf(filter);
        if (isActive != null) {
            this.active = isActive;
        }
        this.updatedAt = now;
    }

    public void setActive(boolean isActive, Instant now) {
        this.active = isActive;
        this.updatedAt = now;
    }
}
