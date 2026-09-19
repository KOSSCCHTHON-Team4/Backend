package team4.emotionmap.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * report 테이블 매핑 (V1__init.sql).
 * Memory 신고. reason 은 자유 텍스트. 중복 신고 허용(제약 없음).
 */
@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;

    @Column(name = "memory_id", nullable = false)
    private Long memoryId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    private Report(Long reporterId, Long memoryId, String reason) {
        this.reporterId = reporterId;
        this.memoryId = memoryId;
        this.reason = reason;
        this.createdAt = OffsetDateTime.now();
    }
}
