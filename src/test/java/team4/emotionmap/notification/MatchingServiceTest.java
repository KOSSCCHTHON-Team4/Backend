package team4.emotionmap.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import team4.emotionmap.contracts.ai.MatchReasonPort;
import team4.emotionmap.contracts.ai.MatchReasonResult;
import team4.emotionmap.contracts.ai.PreferenceVerifyPort;
import team4.emotionmap.contracts.ai.VerifyResult;
import team4.emotionmap.contracts.config.MatchingProperties;
import team4.emotionmap.contracts.dictionary.VibeVector;
import team4.emotionmap.contracts.events.MemoryPublishedEvent;
import team4.emotionmap.memory.MemoryRepository;
import team4.emotionmap.place.Place;
import team4.emotionmap.place.PlaceRepository;

/** 기획 §7 흐름: 반경·카테고리 필터 → 1차 → 2차 → 하루 1회 → 알림 저장. DB 없이 목으로 검증. */
class MatchingServiceTest {

    private final PreferenceRepository preferences = mock(PreferenceRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final PlaceRepository places = mock(PlaceRepository.class);
    private final MemoryRepository memories = mock(MemoryRepository.class);
    private final PreferenceVerifyPort verify = mock(PreferenceVerifyPort.class);
    private final MatchReasonPort reason = mock(MatchReasonPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-20T03:00:00Z"), ZoneOffset.UTC);
    private final MatchingService service = new MatchingService(preferences, notifications, places, memories,
            verify, reason, MatchingProperties.defaults(), clock,
            mock(org.springframework.transaction.PlatformTransactionManager.class));

    private final UUID author = UUID.randomUUID();
    private final UUID receiver = UUID.randomUUID();
    private final UUID memoryId = UUID.randomUUID();
    private Place place;

    @BeforeEach
    void setUp() {
        place = Place.builder().id(UUID.randomUUID()).lat(37.6109).lng(126.9977).label("조용한 카페").build();
        place.updateProfile(new VibeVector(-0.5, -0.5, -0.5, -0.5), 2, null, clock.instant());
        when(places.findById(place.getId())).thenReturn(Optional.of(place));
        when(places.findAllInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(place));
        when(memories.findApprovedLetterContents(eq(place.getId()), any())).thenReturn(List.of("조용히 오래 머물기 좋아요", "혼자 책 읽기 좋은 곳"));
        when(notifications.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reason.explain(any())).thenReturn(MatchReasonResult.of("조용히 혼자 머물 곳을 찾는 당신에게 딱이에요."));
    }

    private Preference preference(String text, double lat, double lng, List<String> filter) {
        return Preference.builder().id(UUID.randomUUID()).userId(receiver)
                .cards(List.of("조용한", "혼자 가기 좋은")).vibeCrowd((short) -1).vibeSpatial((short) 0)
                .vibeCompany((short) -1).vibeStay((short) 0).preferenceText(text).categoryFilter(filter)
                .centerLat(lat).centerLng(lng).radiusM(1000).active(true).build();
    }

    @Test
    void stageOneNotifiesWithAiReason() {
        when(preferences.findByActiveTrueAndUserIdNot(author)).thenReturn(List.of(preference(null, 37.6109, 126.9977, null)));
        int created = service.match(new MemoryPublishedEvent(memoryId, place.getId(), author));
        assertThat(created).isEqualTo(1);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getStage()).isEqualTo((short) 1);
        assertThat(n.getSimilarity()).isGreaterThanOrEqualTo(0.85);
        assertThat(n.getReason()).contains("당신에게");
        assertThat(n.getUserId()).isEqualTo(receiver);
        verify(verify, never()).verify(any());
    }

    @Test
    void outsideRadiusIsSkippedUnlessFallbackApplies() {
        // 2.2km 동쪽. 기본 1km 안에 리뷰 있는 장소가 1개(<3) → 3km 로 확장되어 후보에 든다.
        when(preferences.findByActiveTrueAndUserIdNot(author)).thenReturn(List.of(preference(null, 37.6140, 127.0220, null)));
        assertThat(service.match(new MemoryPublishedEvent(memoryId, place.getId(), author))).isEqualTo(1);

        // 반경 안 장소가 3개 이상이면 1km 유지 → 2.2km 는 탈락
        Place other1 = Place.builder().id(UUID.randomUUID()).lat(37.614).lng(127.022).reviewCount(1).build();
        Place other2 = Place.builder().id(UUID.randomUUID()).lat(37.615).lng(127.021).reviewCount(1).build();
        Place other3 = Place.builder().id(UUID.randomUUID()).lat(37.613).lng(127.023).reviewCount(1).build();
        when(places.findAllInBox(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(other1, other2, other3));
        assertThat(service.match(new MemoryPublishedEvent(memoryId, place.getId(), author))).isEqualTo(0);
    }

    @Test
    void categoryFilterAppliesOnlyAsFilter() {
        place.updateProfile(new VibeVector(-0.5, -0.5, -0.5, -0.5), 2,
                team4.emotionmap.contracts.dictionary.PlaceCategoryCode.CAFE, clock.instant());
        when(preferences.findByActiveTrueAndUserIdNot(author)).thenReturn(List.of(
                preference(null, 37.6109, 126.9977, List.of("BAR"))));
        assertThat(service.match(new MemoryPublishedEvent(memoryId, place.getId(), author))).isEqualTo(0);
        when(preferences.findByActiveTrueAndUserIdNot(author)).thenReturn(List.of(
                preference(null, 37.6109, 126.9977, List.of("CAFE", "BAR"))));
        assertThat(service.match(new MemoryPublishedEvent(memoryId, place.getId(), author))).isEqualTo(1);
    }

    @Test
    void ambiguousZoneNeedsPreferenceTextAndAiFit() {
        // 장소 벡터를 살짝 다른 방향으로: s ≈ 0.70 (VERIFY 구간)
        place.updateProfile(new VibeVector(-0.4, 0.4, 0.0, 0.4), 3, null, clock.instant());
        Preference noText = preference(null, 37.6109, 126.9977, null);
        when(preferences.findByActiveTrueAndUserIdNot(author)).thenReturn(List.of(noText));
        assertThat(service.match(new MemoryPublishedEvent(memoryId, place.getId(), author))).isEqualTo(0);
        verify(verify, never()).verify(any());

        Preference withText = preference("비 오는 날 혼자 책 읽기 좋은 곳", 37.6109, 126.9977, null);
        when(preferences.findByActiveTrueAndUserIdNot(author)).thenReturn(List.of(withText));
        when(verify.verify(any())).thenReturn(VerifyResult.failed("AI_DOWN"));
        assertThat(service.match(new MemoryPublishedEvent(memoryId, place.getId(), author))).isEqualTo(0);

        when(verify.verify(any())).thenReturn(VerifyResult.of(true, 0.8, "혼자 책 읽기 좋다는 리뷰가 있어요.", List.of()));
        assertThat(service.match(new MemoryPublishedEvent(memoryId, place.getId(), author))).isEqualTo(1);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        assertThat(captor.getValue().getStage()).isEqualTo((short) 2);
        assertThat(captor.getValue().getReason()).isEqualTo("혼자 책 읽기 좋다는 리뷰가 있어요.");
    }

    @Test
    void samePlaceOncePerDayAndTemplateFallback() {
        Preference pref = preference(null, 37.6109, 126.9977, null);
        when(preferences.findByActiveTrueAndUserIdNot(author)).thenReturn(List.of(pref));
        when(notifications.existsByPreferenceIdAndPlaceIdAndServiceDate(eq(pref.getId()), eq(place.getId()), any())).thenReturn(true);
        assertThat(service.match(new MemoryPublishedEvent(memoryId, place.getId(), author))).isEqualTo(0);

        when(notifications.existsByPreferenceIdAndPlaceIdAndServiceDate(eq(pref.getId()), eq(place.getId()), any())).thenReturn(false);
        when(reason.explain(any())).thenReturn(MatchReasonResult.unavailable());
        assertThat(service.match(new MemoryPublishedEvent(memoryId, place.getId(), author))).isEqualTo(1);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        assertThat(captor.getValue().getReason()).contains("조용한, 혼자 가기 좋은").contains("조용한 카페");
    }

    @Test
    void placeWithoutVectorProducesNothing() {
        Place empty = Place.builder().id(UUID.randomUUID()).lat(37.6).lng(127.0).build();
        when(places.findById(empty.getId())).thenReturn(Optional.of(empty));
        assertThat(service.match(new MemoryPublishedEvent(memoryId, empty.getId(), author))).isEqualTo(0);
    }
}
