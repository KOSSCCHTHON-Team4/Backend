package team4.emotionmap.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record MemoryCategoryId(
        @Column(name = "memory_id", nullable = false) UUID memoryId,
        @Column(name = "category_id", nullable = false) Short categoryId
) implements Serializable {
}
