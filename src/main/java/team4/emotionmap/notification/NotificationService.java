package team4.emotionmap.notification;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.account.AccountAccessService;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.notification.dto.NotificationResponse;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final AccountAccessService accountAccessService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(UUID userId) {
        accountAccessService.requireActive(userId);
        return notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        accountAccessService.requireActive(userId);
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID id) {
        accountAccessService.requireActive(userId);
        Notification n = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ContractError.of(ErrorCode.RESOURCE_NOT_FOUND));
        n.markRead(clock.instant());
        return NotificationResponse.from(n);
    }
}
