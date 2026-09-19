package team4.emotionmap.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team4.emotionmap.domain.Place;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * 지도 영역(bounding box) 내 장소 조회.
     * bbox = (minLng, minLat, maxLng, maxLat).
     */
    @Query("""
            SELECT p FROM Place p
            WHERE p.lat BETWEEN :minLat AND :maxLat
              AND p.lng BETWEEN :minLng AND :maxLng
            """)
    List<Place> findInBoundingBox(@Param("minLng") double minLng,
                                  @Param("minLat") double minLat,
                                  @Param("maxLng") double maxLng,
                                  @Param("maxLat") double maxLat);
}
