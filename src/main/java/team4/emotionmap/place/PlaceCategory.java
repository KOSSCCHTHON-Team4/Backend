package team4.emotionmap.place;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "place_categories")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlaceCategory {
    @Id
    private Short id;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String code;

    @Column(nullable = false, columnDefinition = "text")
    private String label;

    @Column(nullable = false, columnDefinition = "text")
    private String definition;

    @Column(name = "sort_order", nullable = false, unique = true)
    private Short sortOrder;

    @Builder.Default
    @Column(name = "taxonomy_version", nullable = false)
    private Short taxonomyVersion = 1;
}
