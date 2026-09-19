package team4.emotionmap.letter;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.letter.dto.LetterResponse;

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
