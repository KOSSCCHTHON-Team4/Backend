package team4.emotionmap.letter;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Embeddable
public record DailySelectionId(
        @Column(name = "user_id", nullable = false) UUID userId,
        @Column(name = "service_date", nullable = false) LocalDate serviceDate
) implements Serializable {
}
