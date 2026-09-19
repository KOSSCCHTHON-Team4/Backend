package team4.emotionmap.memory;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team4.emotionmap.contracts.memory.AxisSource;

@Entity
@Table(name = "memory_categories", uniqueConstraints =
        @UniqueConstraint(columnNames = {"memory_id", "slot_no"}))
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemoryCategory {
    @EmbeddedId
    private MemoryCategoryId id;

    @Column(name = "slot_no", nullable = false)
    private Short slotNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_source", nullable = false, columnDefinition = "text")
    private AxisSource assignmentSource;

    @Column(name = "label_snapshot", nullable = false, columnDefinition = "text")
    private String labelSnapshot;

    @Builder.Default
    @Column(name = "taxonomy_version", nullable = false)
    private Short taxonomyVersion = 1;

    public MemoryCategory copyForMemory(UUID memoryId) {
        return builder().id(new MemoryCategoryId(memoryId, id.categoryId()))
                .slotNo(slotNo).assignmentSource(assignmentSource)
                .labelSnapshot(labelSnapshot).taxonomyVersion(taxonomyVersion).build();
    }
}
