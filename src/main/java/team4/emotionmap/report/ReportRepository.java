package team4.emotionmap.report;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    List<Report> findByMemoryId(UUID memoryId);
}
