package team4.emotionmap.letter;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface DailySelectionRepository extends Repository<DailySelection, DailySelectionId> {

    Optional<DailySelection> findById(DailySelectionId id);

    DailySelection save(DailySelection selection);
}
