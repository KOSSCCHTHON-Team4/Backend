package team4.emotionmap.place;

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
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.dictionary.VibeVector;

/**
 * 내부 핀. 기획 §9 Place: naver_title/address/category, category(+source), vibe_avg, review_count.
 * 분위기 벡터는 우리 사용자의 리뷰(경험)로만 계산하며 네이버 설명·리뷰는 쓰지 않는다(기획 §6).
 */
@Entity
@Table(name = "places")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Place {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(columnDefinition = "text")
    private String label;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Column(name = "naver_title", columnDefinition = "text")
    private String naverTitle;

    @Column(name = "naver_address", columnDefinition = "text")
    private String naverAddress;

    @Column(name = "naver_category", columnDefinition = "text")
    private String naverCategory;

    @Column(name = "category_code", columnDefinition = "text")
    private String categoryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", columnDefinition = "text")
    private PlaceCategorySource categorySource;

    @Builder.Default
    @Column(name = "review_count", nullable = false)
    private Integer reviewCount = 0;

    @Column(name = "vibe_crowd")
    private Double vibeCrowd;

    @Column(name = "vibe_spatial")
    private Double vibeSpatial;

    @Column(name = "vibe_company")
    private Double vibeCompany;

    @Column(name = "vibe_stay")
    private Double vibeStay;

    @Column(name = "vibe_updated_at")
    private Instant vibeUpdatedAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isNaverRegistered() {
        return naverTitle != null && !naverTitle.isBlank();
    }

    /** 리뷰가 하나도 없으면 null. */
    public VibeVector vibe() {
        if (vibeCrowd == null || vibeSpatial == null || vibeCompany == null || vibeStay == null) {
            return null;
        }
        return new VibeVector(vibeCrowd, vibeSpatial, vibeCompany, vibeStay);
    }

    public PlaceCategoryCode category() {
        return categoryCode == null ? null : PlaceCategoryCode.fromCode(categoryCode).orElse(null);
    }

    /** 표시명: label → 네이버 상호명 → null. */
    public String displayName() {
        if (label != null && !label.isBlank()) {
            return label;
        }
        return isNaverRegistered() ? naverTitle : null;
    }

    /**
     * 네이버 장소 정보를 붙인다(최초 1회). 카테고리는 NAVER 매핑이 리뷰 다수결보다 우선한다(기획 §5).
     */
    public void attachNaver(String title, String address, String naverCategoryRaw, PlaceCategoryCode mapped) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("naver title is required");
        }
        this.naverTitle = title.strip();
        this.naverAddress = address == null || address.isBlank() ? null : address.strip();
        this.naverCategory = naverCategoryRaw == null || naverCategoryRaw.isBlank() ? null : naverCategoryRaw.strip();
        if (mapped != null) {
            this.categoryCode = mapped.name();
            this.categorySource = PlaceCategorySource.NAVER;
        }
    }

    /**
     * 리뷰 집계 결과를 반영한다(기획 §5·§6). NAVER 카테고리가 있으면 카테고리는 바꾸지 않는다.
     *
     * @param vibe         P = 평균 × n/(n+2). 리뷰 0건이면 null
     * @param majority     리뷰별 추론 다수결 카테고리. 없으면 null
     */
    public void updateProfile(VibeVector vibe, int count, PlaceCategoryCode majority, Instant at) {
        if (count < 0) {
            throw new IllegalArgumentException("review count must be >= 0");
        }
        this.reviewCount = count;
        if (vibe == null) {
            this.vibeCrowd = this.vibeSpatial = this.vibeCompany = this.vibeStay = null;
        } else {
            this.vibeCrowd = vibe.crowdLevel();
            this.vibeSpatial = vibe.spatialFeel();
            this.vibeCompany = vibe.companyFit();
            this.vibeStay = vibe.stayStyle();
        }
        this.vibeUpdatedAt = at;
        if (categorySource != PlaceCategorySource.NAVER) {
            if (majority == null) {
                this.categoryCode = null;
                this.categorySource = null;
            } else {
                this.categoryCode = majority.name();
                this.categorySource = PlaceCategorySource.REVIEWS;
            }
        }
    }
}
