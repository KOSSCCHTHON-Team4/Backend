package team4.emotionmap.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.account.AccountAccessService;
import team4.emotionmap.account.User;
import team4.emotionmap.contracts.config.MatchingProperties;
import team4.emotionmap.contracts.config.ServiceConfigSource;
import team4.emotionmap.contracts.dictionary.CategorySelection;
import team4.emotionmap.contracts.dictionary.PlaceCategoryCode;
import team4.emotionmap.contracts.dictionary.PreferenceCard;
import team4.emotionmap.contracts.dictionary.VibeVector;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldError;
import team4.emotionmap.contracts.geo.GeoPoint;
import team4.emotionmap.contracts.validation.StrictValues;
import team4.emotionmap.contracts.validation.TextRules;
import team4.emotionmap.notification.dto.PreferenceRequest;
import team4.emotionmap.notification.dto.PreferenceResponse;

/** 취향 알림 설정 CRUD(기획 §4·§9). 위치가 없으면 온보딩 우편함을 기준점으로 쓴다. */
@Service
@RequiredArgsConstructor
public class PreferenceService {

    private final PreferenceRepository preferenceRepository;
    private final AccountAccessService accountAccessService;
    private final ServiceConfigSource serviceConfig;
    private final MatchingProperties matching;
    private final Clock clock;

    @Transactional
    public PreferenceResponse create(UUID userId, PreferenceRequest request) {
        User user = accountAccessService.requireActive(userId);
        List<PreferenceCard> cards = PreferenceCard.parse("cards", request.cards());
        VibeVector vector = PreferenceCard.toVector(cards);
        String text = normalizeText(request.preferenceText());
        List<String> filter = normalizeFilter(request.categoryFilter());
        GeoPoint center = resolveCenter(user, request);
        int radius = request.radiusM() == null ? matching.radiusMeters() : request.radiusM();
        if (radius < 1) {
            throw ContractError.of(ErrorCode.VALIDATION_ERROR, FieldError.outOfRange("radiusM"));
        }
        Instant now = clock.instant();
        Preference saved = preferenceRepository.save(Preference.builder()
                .userId(userId)
                .cards(cards.stream().map(PreferenceCard::label).toList())
                .vibeCrowd((short) vector.crowdLevel()).vibeSpatial((short) vector.spatialFeel())
                .vibeCompany((short) vector.companyFit()).vibeStay((short) vector.stayStyle())
                .preferenceText(text).categoryFilter(filter.isEmpty() ? null : filter)
                .centerLat(center.lat()).centerLng(center.lng()).radiusM(radius)
                .active(request.active() == null || request.active())
                .createdAt(now).updatedAt(now).build());
        return PreferenceResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PreferenceResponse> list(UUID userId) {
        accountAccessService.requireActive(userId);
        return preferenceRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PreferenceResponse::from).toList();
    }

    @Transactional
    public PreferenceResponse update(UUID userId, UUID id, PreferenceRequest request) {
        accountAccessService.requireActive(userId);
        Preference pref = preferenceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ContractError.of(ErrorCode.RESOURCE_NOT_FOUND));
        List<PreferenceCard> cards = PreferenceCard.parse("cards", request.cards());
        pref.update(cards, normalizeText(request.preferenceText()), normalizeFilter(request.categoryFilter()),
                request.active(), clock.instant());
        return PreferenceResponse.from(pref);
    }

    @Transactional
    public PreferenceResponse setActive(UUID userId, UUID id, boolean active) {
        accountAccessService.requireActive(userId);
        Preference pref = preferenceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ContractError.of(ErrorCode.RESOURCE_NOT_FOUND));
        pref.setActive(active, clock.instant());
        return PreferenceResponse.from(pref);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        accountAccessService.requireActive(userId);
        Preference pref = preferenceRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ContractError.of(ErrorCode.RESOURCE_NOT_FOUND));
        // 알림 이력이 FK(RESTRICT) 로 참조하므로 물리 삭제 대신 비활성화한다.
        pref.setActive(false, clock.instant());
    }

    private String normalizeText(String text) {
        return TextRules.normalizeOptionalText(text, serviceConfig.limits().preferenceDescriptionMaxCodePoints(),
                "preferenceText", ErrorCode.VALIDATION_ERROR);
    }

    private static List<String> normalizeFilter(List<String> filter) {
        if (filter == null || filter.isEmpty()) {
            return List.of();
        }
        // 카테고리 필터는 8종 중 최대 8개까지 허용(중복·미지 코드 거절). 상한 3 은 경험 분류에만 적용된다.
        List<String> distinct = filter.stream().distinct().toList();
        if (distinct.size() != filter.size()) {
            throw ContractError.of(ErrorCode.VALIDATION_ERROR, FieldError.duplicate("categoryFilter"));
        }
        for (int i = 0; i < distinct.size(); i++) {
            if (PlaceCategoryCode.fromCode(distinct.get(i)).isEmpty()) {
                throw ContractError.of(ErrorCode.VALIDATION_ERROR, FieldError.invalid("categoryFilter[" + i + "]"));
            }
        }
        return distinct;
    }

    private static GeoPoint resolveCenter(User user, PreferenceRequest request) {
        if (request.centerLat() != null || request.centerLng() != null) {
            return StrictValues.requireCoordinates(request.centerLat(), request.centerLng(),
                    "centerLat", "centerLng", ErrorCode.VALIDATION_ERROR);
        }
        if (user.getMailboxLat() == null || user.getMailboxLng() == null) {
            throw ContractError.of(ErrorCode.VALIDATION_ERROR, FieldError.required("centerLat"),
                    FieldError.required("centerLng"));
        }
        return new GeoPoint(user.getMailboxLat(), user.getMailboxLng());
    }

    static {
        // CategorySelection 은 경험 분류(최대 3) 전용이므로 여기서는 참조만 남긴다.
        Object unused = CategorySelection.class;
    }
}
