package team4.emotionmap.report;

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

@Entity
@Table(name = "reports")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Column(name = "memory_id", nullable = false, updatable = false)
    private UUID memoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private ReportReason reason;

    @Column(columnDefinition = "text", updatable = false)
    private String details;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "text")
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "handled_by")
    private UUID handledBy;

    @Column(name = "handling_note", columnDefinition = "text")
    private String handlingNote;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "handled_at")
    private Instant handledAt;
}
