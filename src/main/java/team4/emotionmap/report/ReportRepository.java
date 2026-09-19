package team4.emotionmap.report;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByMemoryId(Long memoryId);
}
