package team4.emotionmap.place;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
