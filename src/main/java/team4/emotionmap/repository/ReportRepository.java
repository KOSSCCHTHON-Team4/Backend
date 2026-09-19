package team4.emotionmap.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import team4.emotionmap.domain.Report;

public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByMemoryId(Long memoryId);
}
