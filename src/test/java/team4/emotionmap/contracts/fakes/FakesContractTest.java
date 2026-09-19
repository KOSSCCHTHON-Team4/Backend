package team4.emotionmap.contracts.fakes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import team4.emotionmap.contracts.account.AccountAccess;
import team4.emotionmap.contracts.account.AccountStatus;
import team4.emotionmap.contracts.account.PreferenceVersionSnapshot;
import team4.emotionmap.contracts.dictionary.Atmospheres;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.fixtures.DictionaryFixtures;
import team4.emotionmap.contracts.media.ImageMediaType;
import team4.emotionmap.contracts.media.SanitizedImage;
import team4.emotionmap.contracts.media.StoredImageMeta;
import team4.emotionmap.contracts.memory.ContentStatus;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.contracts.memory.ImageAttachment;
import team4.emotionmap.contracts.memory.IndependentCopyCommand;
import team4.emotionmap.contracts.memory.MemorySnapshot;
import team4.emotionmap.contracts.memory.OriginKind;

/** 가짜 구현이 포트 계약의 의미(§4.1 09시 재현, §4.4 독립 사본, C02 현재값 재확인)를 그대로 보이는지 고정한다. */
class FakesContractTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static Instant kst(String date, int hour, int minute) {
        return ZonedDateTime.of(java.time.LocalDate.parse(date), java.time.LocalTime.of(hour, minute), KST).toInstant();
    }

    @Test
    void findAtReturnsVersionEffectiveAtCutoffNotLatest() {
        InMemoryPreferenceHistoryReader reader = new InMemoryPreferenceHistoryReader();
        UUID user = DictionaryFixtures.userId(1);
        PreferenceVersionSnapshot v1 = reader.append(user, new Atmospheres(-1, -1, -1, -1), "v1", kst("2026-09-19", 8, 40));
        PreferenceVersionSnapshot v2 = reader.append(user, new Atmospheres(1, 1, 1, 1), null, kst("2026-09-19", 10, 0));

        Instant cutoff = kst("2026-09-19", 9, 0);
        assertThat(reader.findAt(user, cutoff)).contains(v1);                 // 11:00 복구 시에도 09:00 은 v1
        assertThat(reader.findLatest(user)).contains(v2);
        assertThat(reader.findAt(user, kst("2026-09-20", 9, 0))).contains(v2); // 다음 날은 그날 기준 최신
        assertThat(reader.findAt(user, kst("2026-09-19", 8, 0))).isEmpty();
        assertThat(v2.preferenceVersion()).isEqualTo("2");
        assertThat(reader.all(user)).hasSize(2);                               // 과거 버전은 남아 있다
    }

    @Test
    void accountAccessIsReReadAndMappedToDenialCodes() {
        UUID user = DictionaryFixtures.userId(2);
        InMemoryAccountAccessReader reader = new InMemoryAccountAccessReader()
                .put(new AccountAccess(user, AccountStatus.PENDING, null, null));
        assertThat(catchThrowableOfType(ContractError.class, () -> reader.requireActive(user)).code())
                .isEqualTo(ErrorCode.INVITATION_REQUIRED);

        reader.withStatus(user, AccountStatus.ACTIVE);
        assertThat(reader.requireActive(user).hasOnboarded()).isFalse();
        assertThat(catchThrowableOfType(ContractError.class, () -> reader.requireOnboarded(user)).code())
                .isEqualTo(ErrorCode.ONBOARDING_REQUIRED);

        reader.activeOnboarded(user, DictionaryFixtures.DEMO_CENTER, kst("2026-09-19", 8, 40));
        assertThat(reader.requireOnboarded(user).mailbox()).isEqualTo(DictionaryFixtures.DEMO_CENTER);

        reader.withStatus(user, AccountStatus.SUSPENDED);   // 토큰이 살아 있어도 현재값으로 거절
        assertThat(catchThrowableOfType(ContractError.class, () -> reader.requireActive(user)).code())
                .isEqualTo(ErrorCode.ACCOUNT_SUSPENDED);
        assertThat(catchThrowableOfType(ContractError.class, () -> reader.requireActive(UUID.randomUUID())).code())
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void independentCopyCarriesContentButNoLinkToOriginal() {
        Clock clock = Clock.fixed(kst("2026-09-19", 9, 5), KST);
        InMemoryMemoryStore store = new InMemoryMemoryStore(clock);
        InMemoryLocalImageStore images = new InMemoryLocalImageStore();

        UUID author = DictionaryFixtures.userId(1);
        UUID receiver = DictionaryFixtures.userId(2);
        StoredImageMeta original = images.store(new SanitizedImage(new byte[]{1, 2, 3}, ImageMediaType.JPEG, 1, 1));
        MemorySnapshot letter = DictionaryFixtures.approvedLetter(DictionaryFixtures.memoryId(1), author, kst("2026-09-18", 20, 0));
        letter = new MemorySnapshot(letter.id(), letter.ownerId(), letter.placeId(), letter.distributionType(),
                letter.originKind(), letter.dataOrigin(), letter.contentStatus(), letter.moderationStatus(),
                letter.availableAt(), letter.content(), letter.placeLabelSnapshot(), letter.location(),
                letter.atmospheres(), letter.atmosphereSources(), letter.axisDefinitionVersion(),
                letter.atmosphereAnalysisStatus(), letter.categoryAnalysisStatus(), letter.analysisModel(),
                letter.analysisPromptVersion(), letter.categories(),
                new ImageAttachment(original.storageKey(), original.mediaType(), original.sizeBytes()),
                letter.createdAt(), null);
        store.put(letter);
        assertThat(letter.isDeliveryCandidateBase()).isTrue();

        MemorySnapshot locked = store.lockAndRead(letter.id()).orElseThrow();
        StoredImageMeta copyFile = images.duplicateIndependent(locked.image().storageKey());
        assertThat(copyFile.storageKey()).isNotEqualTo(original.storageKey());
        assertThat(copyFile.storageKey()).doesNotContain(letter.id().toString());

        UUID copyId = store.insertIndependentCopy(IndependentCopyCommand.fromOriginal(locked, receiver,
                new ImageAttachment(copyFile.storageKey(), copyFile.mediaType(), copyFile.sizeBytes())));
        MemorySnapshot copy = store.read(copyId).orElseThrow();
        assertThat(copy.ownerId()).isEqualTo(receiver);
        assertThat(copy.distributionType()).isEqualTo(DistributionType.PRIVATE);
        assertThat(copy.originKind()).isEqualTo(OriginKind.LETTER_COPY);
        assertThat(copy.availableAt()).isNull();
        assertThat(copy.isDeliveryCandidateBase()).isFalse();
        assertThat(copy.content()).isEqualTo(letter.content());
        assertThat(copy.categories()).isEqualTo(letter.categories());
        assertThat(copy.toString()).doesNotContain(letter.id().toString());

        // 원문 삭제 뒤에도 사본·사본 파일은 유지된다(§4.5).
        assertThat(store.markDeleted(letter.id())).isTrue();
        assertThat(store.markDeleted(letter.id())).isFalse();
        assertThat(store.read(letter.id()).orElseThrow().contentStatus()).isEqualTo(ContentStatus.DELETED);
        assertThat(store.read(copyId).orElseThrow().contentStatus()).isEqualTo(ContentStatus.ACTIVE);
        assertThat(images.describe(copyFile.storageKey())).isPresent();
        assertThat(images.describe("../etc/passwd")).isEmpty();
    }
}
