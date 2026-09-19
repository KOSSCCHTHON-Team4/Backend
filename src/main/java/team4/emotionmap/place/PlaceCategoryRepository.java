package team4.emotionmap.place;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface PlaceCategoryRepository extends Repository<PlaceCategory, Short> {
    Optional<PlaceCategory> findById(Short id);
    Optional<PlaceCategory> findByCode(String code);
    List<PlaceCategory> findAllByOrderBySortOrder();
    PlaceCategory save(PlaceCategory category);
}
