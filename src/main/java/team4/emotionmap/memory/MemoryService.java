package team4.emotionmap.memory;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.memory.ai.ContentModerator;
import team4.emotionmap.memory.ai.Embedder;
import team4.emotionmap.memory.ai.EmotionTagger;
import team4.emotionmap.memory.dto.MemoryCreateRequest;
import team4.emotionmap.memory.dto.MemoryResponse;

/**
 * 기억 서비스.
 *
 * 저장 시 서버가 채우는 것(모두 AI 포트, 구현은 추후 · Optional 주입):
 *   - ContentModerator : 부적절 콘텐츠 필터 (차단 시 예외)
 *   - EmotionTagger    : content -> 감정 태그
 *   - Embedder         : content -> 임베딩 벡터
 * 포트 구현이 없으면 해당 단계는 건너뛴다(추후 등록만 하면 자동 연결).
 */
@Slf4j
@Service
public class MemoryService {

    private final MemoryRepository memoryRepository;
    private final Optional<ContentModerator> contentModerator;
    private final Optional<EmotionTagger> emotionTagger;
    private final Optional<Embedder> embedder;

    public MemoryService(MemoryRepository memoryRepository,
                         Optional<ContentModerator> contentModerator,
                         Optional<EmotionTagger> emotionTagger,
                         Optional<Embedder> embedder) {
        this.memoryRepository = memoryRepository;
        this.contentModerator = contentModerator;
        this.emotionTagger = emotionTagger;
        this.embedder = embedder;
    }

    @Transactional
    public MemoryResponse create(MemoryCreateRequest req) {
        // 1) 콘텐츠 필터(모더레이션) - 구현 있을 때만. 차단 시 예외.
        contentModerator.ifPresent(moderator -> {
            ContentModerator.Result result = moderator.moderate(req.content());
            if (!result.allowed()) {
                throw new IllegalArgumentException("부적절한 콘텐츠로 차단됨: " + result.reason());
            }
        });

        Memory memory = Memory.builder()
                .userId(req.userId())
                .placeId(req.placeId())
                .content(req.content())
                .imagePath(req.imagePath())
                .visibility(req.visibility())
                .build();

        // 2) 감정 태그 자동 추출 (구현 있을 때만)
        emotionTagger.ifPresent(tagger -> memory.assignEmotionTag(tagger.extract(req.content())));

        // 3) 본문 임베딩 생성 (구현 있을 때만)
        embedder.ifPresent(e -> memory.assignEmbedding(e.embed(req.content())));

        Memory saved = memoryRepository.save(memory);
        log.info("memory created: id={}, userId={}, emotionTag={}",
                saved.getId(), saved.getUserId(), saved.getEmotionTag());
        return MemoryResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public MemoryResponse get(Long id) {
        Memory memory = memoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("기억을 찾을 수 없습니다: " + id));
        return MemoryResponse.from(memory);
    }

    @Transactional(readOnly = true)
    public List<MemoryResponse> findByUser(Long userId) {
        return memoryRepository.findByUserId(userId).stream()
                .map(MemoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MemoryResponse> findByPlace(Long placeId) {
        return memoryRepository.findByPlaceId(placeId).stream()
                .map(MemoryResponse::from)
                .toList();
    }

    /** 하드 삭제 (ERD 결정: 삭제). CASCADE 로 reaction/letter/report 도 함께 삭제됨. */
    @Transactional
    public void delete(Long id) {
        memoryRepository.deleteById(id);
    }

    /** 벡터 유사도 검색: 질의 임베딩과 가장 가까운 ACTIVE 기억 top-N. */
    @Transactional(readOnly = true)
    public List<MemoryResponse> findSimilar(float[] queryEmbedding, int limit) {
        return memoryRepository.findNearestByEmbedding(queryEmbedding, limit).stream()
                .map(MemoryResponse::from)
                .toList();
    }
}
