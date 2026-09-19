package team4.emotionmap.place;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, UUID> {
    @Query(value = """
            select p.* from places p
            where p.lat between :minLat and :maxLat
              and p.lng between :minLng and :maxLng
              and exists (
                select 1 from memories m
                where m.place_id = p.id and m.content_status = 'ACTIVE'
                  and (m.owner_id = :userId or (
                    m.distribution_type = 'LETTER' and m.moderation_status = 'APPROVED'
                    and exists (select 1 from letter_deliveries d
                                where d.memory_id = m.id and d.receiver_id = :userId)
                  ))
              )
            order by p.id
            """, nativeQuery = true)
    List<Place> findInBoundingBox(@Param("userId") UUID userId,
                                 @Param("minLng") double minLng,
                                 @Param("minLat") double minLat,
                                 @Param("maxLng") double maxLng,
                                 @Param("maxLat") double maxLat);

    /** 권한 필터 없는 좌표 상자 조회(장소 병합·반경 내 장소 수 계산용). 정확한 거리는 호출자가 haversine 으로 확인한다. */
    @Query("select p from Place p where p.lat between :minLat and :maxLat and p.lng between :minLng and :maxLng")
    List<Place> findAllInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
                             @Param("minLng") double minLng, @Param("maxLng") double maxLng);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Place p where p.id = :id")
    Optional<Place> findByIdForUpdate(@Param("id") UUID id);
}
