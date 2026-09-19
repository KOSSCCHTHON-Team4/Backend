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
            WITH bound AS (
              SELECT COALESCE(CAST(:upperId AS uuid), (
                SELECT p.id
                FROM places p
                WHERE p.lng BETWEEN :minLng AND :maxLng
                  AND p.lat BETWEEN :minLat AND :maxLat
                  AND EXISTS (
                    SELECT 1
                    FROM memories m
                    WHERE m.place_id = p.id
                      AND m.content_status = 'ACTIVE'
                      AND (m.owner_id = :userId OR (
                        m.distribution_type = 'LETTER'
                        AND m.moderation_status = 'APPROVED'
                        AND EXISTS (
                          SELECT 1
                          FROM letter_deliveries d
                          WHERE d.memory_id = m.id AND d.receiver_id = :userId
                        )
                      ))
                  )
                ORDER BY p.id DESC
                LIMIT 1
              )) AS upper_id
            ), candidates AS MATERIALIZED (
              SELECT p.id, p.lat, p.lng, p.label, p.naver_title, p.naver_address,
                     p.category_code, p.category_source, b.upper_id
              FROM places p
              CROSS JOIN bound b
              WHERE p.lng BETWEEN :minLng AND :maxLng
                AND p.lat BETWEEN :minLat AND :maxLat
                AND (CAST(:afterId AS uuid) IS NULL OR p.id > CAST(:afterId AS uuid))
                AND p.id <= b.upper_id
                AND EXISTS (
                  SELECT 1
                  FROM memories m
                  WHERE m.place_id = p.id
                    AND m.content_status = 'ACTIVE'
                    AND (m.owner_id = :userId OR (
                      m.distribution_type = 'LETTER'
                      AND m.moderation_status = 'APPROVED'
                      AND EXISTS (
                        SELECT 1
                        FROM letter_deliveries d
                        WHERE d.memory_id = m.id AND d.receiver_id = :userId
                      )
                    ))
                )
              ORDER BY p.id ASC
              LIMIT :fetchLimit
            ), page AS MATERIALIZED (
              SELECT *
              FROM candidates
              ORDER BY id ASC
              LIMIT :pageLimit
            ), page_meta AS (
              SELECT COUNT(*) > :pageLimit AS has_more
              FROM candidates
            ), visible AS MATERIALIZED (
              SELECT m.id, m.place_id, m.origin_kind, m.distribution_type,
                     m.moderation_status, m.created_at, m.crowd_level,
                     m.spatial_feel, m.company_fit, m.stay_style, m.category_pred
              FROM page p
              JOIN memories m ON m.place_id = p.id
              WHERE m.content_status = 'ACTIVE'
                AND (m.owner_id = :userId OR (
                  m.distribution_type = 'LETTER'
                  AND m.moderation_status = 'APPROVED'
                  AND EXISTS (
                    SELECT 1
                    FROM letter_deliveries d
                    WHERE d.memory_id = m.id AND d.receiver_id = :userId
                  )
                ))
            ), visible_counts AS (
              SELECT place_id, COUNT(DISTINCT id) AS memory_count
              FROM visible
              GROUP BY place_id
            ), profile_reviews AS MATERIALIZED (
              SELECT *
              FROM visible
              WHERE origin_kind = 'DIRECT'
                AND (distribution_type = 'PRIVATE' OR moderation_status = 'APPROVED')
            ), profile_sums AS (
              SELECT place_id, COUNT(*) AS review_count,
                     SUM(CAST(crowd_level AS double precision) ORDER BY created_at DESC, id DESC) AS crowd_sum,
                     SUM(CAST(spatial_feel AS double precision) ORDER BY created_at DESC, id DESC) AS spatial_sum,
                     SUM(CAST(company_fit AS double precision) ORDER BY created_at DESC, id DESC) AS company_sum,
                     SUM(CAST(stay_style AS double precision) ORDER BY created_at DESC, id DESC) AS stay_sum
              FROM profile_reviews
              GROUP BY place_id
            ), votes AS (
              SELECT r.place_id, c.id AS category_id, c.code AS category_code, COUNT(*) AS vote_count
              FROM profile_reviews r
              JOIN page p ON p.id = r.place_id
              LEFT JOIN LATERAL (
                SELECT mc.category_id
                FROM memory_categories mc
                WHERE r.category_pred IS NULL AND mc.memory_id = r.id
                ORDER BY mc.slot_no ASC
                LIMIT 1
              ) first_category ON TRUE
              JOIN place_categories c ON (
                (r.category_pred IS NOT NULL AND c.code = r.category_pred)
                OR (r.category_pred IS NULL AND c.id = first_category.category_id)
              )
              WHERE p.category_source IS DISTINCT FROM 'NAVER'
              GROUP BY r.place_id, c.id, c.code
            ), ranked_votes AS (
              SELECT place_id, category_code,
                     ROW_NUMBER() OVER (
                       PARTITION BY place_id
                       ORDER BY vote_count DESC, category_id ASC
                     ) AS rank
              FROM votes
            )
            SELECT p.id AS id, p.lat AS lat, p.lng AS lng, p.label AS label,
                   p.naver_title AS "naverTitle", p.naver_address AS "naverAddress",
                   CASE WHEN p.category_source = 'NAVER' THEN p.category_code
                        ELSE w.category_code END AS "categoryCode",
                   CASE WHEN p.category_source = 'NAVER' THEN 'NAVER'
                        WHEN w.category_code IS NOT NULL THEN 'REVIEWS'
                        ELSE NULL END AS "categorySource",
                   vc.memory_count AS "memoryCount",
                   COALESCE(ps.review_count, CAST(0 AS bigint)) AS "reviewCount",
                   COALESCE(ps.crowd_sum, CAST(0.0 AS double precision)) AS "crowdSum",
                   COALESCE(ps.spatial_sum, CAST(0.0 AS double precision)) AS "spatialSum",
                   COALESCE(ps.company_sum, CAST(0.0 AS double precision)) AS "companySum",
                   COALESCE(ps.stay_sum, CAST(0.0 AS double precision)) AS "staySum",
                   p.upper_id AS "upperId",
                   meta.has_more AS "hasMore"
            FROM page p
            JOIN visible_counts vc ON vc.place_id = p.id
            LEFT JOIN profile_sums ps ON ps.place_id = p.id
            LEFT JOIN ranked_votes w ON w.place_id = p.id AND w.rank = 1
            CROSS JOIN page_meta meta
            ORDER BY p.id ASC
            """, nativeQuery = true)
    List<VisiblePlaceRow> findVisiblePage(@Param("userId") UUID userId,
                                          @Param("minLng") double minLng,
                                          @Param("minLat") double minLat,
                                          @Param("maxLng") double maxLng,
                                          @Param("maxLat") double maxLat,
                                          @Param("afterId") UUID afterId,
                                          @Param("upperId") UUID upperId,
                                          @Param("pageLimit") int pageLimit,
                                          @Param("fetchLimit") long fetchLimit);

    interface VisiblePlaceRow {
        UUID getId();

        Double getLat();

        Double getLng();

        String getLabel();

        String getNaverTitle();

        String getNaverAddress();

        String getCategoryCode();

        String getCategorySource();

        long getMemoryCount();

        long getReviewCount();

        double getCrowdSum();

        double getSpatialSum();

        double getCompanySum();

        double getStaySum();

        UUID getUpperId();

        boolean getHasMore();
    }

    /** 권한 필터 없는 좌표 상자 조회(장소 병합·반경 내 장소 수 계산용). 정확한 거리는 호출자가 haversine 으로 확인한다. */
    @Query("select p from Place p where p.lat between :minLat and :maxLat and p.lng between :minLng and :maxLng")
    List<Place> findAllInBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat,
                             @Param("minLng") double minLng, @Param("maxLng") double maxLng);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Place p where p.id = :id")
    Optional<Place> findByIdForUpdate(@Param("id") UUID id);
}
