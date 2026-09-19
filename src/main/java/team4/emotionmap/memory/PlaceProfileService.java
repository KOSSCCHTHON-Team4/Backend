package team4.emotionmap.memory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.contracts.config.MatchingProperties;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.dictionary.VibeVector;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.ModerationStatus;
import team4.emotionmap.contracts.memory.OriginKind;
import team4.emotionmap.place.Place;
import team4.emotionmap.place.PlaceRepository;

/**
 * 장소 프로필 집계(기획 §5·§6). 집계 대상 리뷰 = 그 장소의 ACTIVE·DIRECT 경험 중
 * PRIVATE 전부 + 안전 승인된 LETTER. 좋아요 사본(LETTER_COPY)은 같은 리뷰의 복제이므로 제외한다.
 *
 * <ul>
 *   <li>P = 리뷰 ±1 벡터 평균 × n/(n+smoothing)</li>
 *   <li>카테고리 = 리뷰별 추론(category_pred, 없으면 1슬롯 카테고리) 다수결. 네이버 카테고리가 있으면 그대로 둔다.</li>
 * </ul>
 * 네이버 설명·리뷰는 쓰지 않고 우리 사용자의 리뷰만 계산한다.
 */
@Service
@RequiredArgsConstructor
public class PlaceProfileService {

    private final PlaceRepository placeRepository;
    private final MemoryRepository memoryRepository;
    private final MemoryCategoryRepository memoryCategoryRepository;
    private final MatchingProperties matching;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRED)
    public Place recompute(UUID placeId) {
        Place place = placeRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new IllegalStateException("Place not found: " + placeId));
        List<Memory> reviews = memoryRepository.findByPlaceIdAndContentStatusOrderByCreatedAtDesc(placeId, ContentStatus.ACTIVE)
                .stream().filter(PlaceProfileService::countsAsReview).toList();

        List<VibeVector> samples = new ArrayList<>(reviews.size());
        Map<PlaceCategoryCode, Integer> votes = new EnumMap<>(PlaceCategoryCode.class);
        for (Memory m : reviews) {
            samples.add(m.vibe());
            PlaceCategoryCode vote = categoryOf(m);
            if (vote != null) {
                votes.merge(vote, 1, Integer::sum);
            }
        }
        VibeVector vibe = samples.isEmpty() ? null : VibeVector.shrunkMean(samples, matching.placeVibeSmoothing());
        place.updateProfile(vibe, samples.size(), majority(votes), clock.instant());
        return place;
    }

    static boolean countsAsReview(Memory m) {
        if (m.getContentStatus() != ContentStatus.ACTIVE || m.getOriginKind() != OriginKind.DIRECT) {
            return false;
        }
        return m.getDistributionType() == DistributionType.PRIVATE
                || m.getModerationStatus() == ModerationStatus.APPROVED;
    }

    private PlaceCategoryCode categoryOf(Memory m) {
        if (m.getCategoryPred() != null) {
            return PlaceCategoryCode.fromCode(m.getCategoryPred()).orElse(null);
        }
        List<MemoryCategory> categories = memoryCategoryRepository.findByIdMemoryIdOrderBySlotNo(m.getId());
        if (categories.isEmpty()) {
            return null;
        }
        Short categoryId = categories.getFirst().getId().categoryId();
        for (PlaceCategoryCode code : PlaceCategoryCode.ordered()) {
            if (code.id() == categoryId) {
                return code;
            }
        }
        return null;
    }

    /** 최다 득표. 동률이면 사전 순서(id)가 앞선 코드. 표가 없으면 null. */
    static PlaceCategoryCode majority(Map<PlaceCategoryCode, Integer> votes) {
        PlaceCategoryCode best = null;
        int bestVotes = 0;
        for (PlaceCategoryCode code : PlaceCategoryCode.ordered()) {
            int v = votes.getOrDefault(code, 0);
            if (v > bestVotes) {
                best = code;
                bestVotes = v;
            }
        }
        return best;
    }
}
