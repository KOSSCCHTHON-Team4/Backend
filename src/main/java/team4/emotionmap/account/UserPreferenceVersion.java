package team4.emotionmap.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "user_preference_versions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "revision"}),
        @UniqueConstraint(columnNames = {"id", "user_id"})
})
@Immutable
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UserPreferenceVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private Long revision;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant effectiveAt = Instant.now();

    @Column(nullable = false, updatable = false)
    private Short crowdLevel;

    @Column(nullable = false, updatable = false)
    private Short spatialFeel;

    @Column(nullable = false, updatable = false)
    private Short companyFit;

    @Column(nullable = false, updatable = false)
    private Short stayStyle;

    @Column(columnDefinition = "text", updatable = false)
    private String description;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Short axisDefinitionVersion = 1;
}
