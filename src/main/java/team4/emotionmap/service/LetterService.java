package team4.emotionmap.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.domain.LetterDelivery;
import team4.emotionmap.dto.LetterResponse;
import team4.emotionmap.repository.LetterDeliveryRepository;

@Service
@RequiredArgsConstructor
public class LetterService {

    private final LetterDeliveryRepository letterDeliveryRepository;

    /** 수신자의 추천 편지 목록. */
    @Transactional(readOnly = true)
    public List<LetterResponse> findForReceiver(Long receiverId) {
        return letterDeliveryRepository.findByReceiverId(receiverId).stream()
                .map(LetterResponse::from)
                .toList();
    }

    /** 편지 읽음 처리. */
    @Transactional
    public LetterResponse markRead(Long id) {
        LetterDelivery letter = letterDeliveryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("편지를 찾을 수 없습니다: " + id));
        letter.markRead();
        return LetterResponse.from(letter);
    }
}
