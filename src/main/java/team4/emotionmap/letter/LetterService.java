package team4.emotionmap.letter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.AccountAccessService;
import team4.emotionmap.account.User;
import team4.emotionmap.account.UserRepository;
import team4.emotionmap.letter.dto.LetterLikeResponse;
import team4.emotionmap.letter.dto.LetterResponse;
import team4.emotionmap.media.ImageStorageService;
import team4.emotionmap.contracts.memory.DistributionType;
import team4.emotionmap.memory.Memory;
import team4.emotionmap.memory.MemoryAccessService;
import team4.emotionmap.memory.MemoryCategoryRepository;
import team4.emotionmap.memory.MemoryRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class LetterService {

    private final LetterDeliveryRepository letterDeliveryRepository;
    private final MemoryRepository memoryRepository;
    private final MemoryCategoryRepository memoryCategoryRepository;
    private final MemoryAccessService memoryAccessService;
    private final AccountAccessService accountAccessService;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;

    @Transactional(readOnly = true)
    public List<LetterResponse> findForReceiver(UUID receiverId) {
        accountAccessService.requireActive(receiverId);
        return letterDeliveryRepository.findByReceiverIdOrderByDeliveredAtDescIdDesc(receiverId).stream()
                .map(delivery -> LetterResponse.from(delivery, availability(receiverId, delivery.getMemoryId())))
                .toList();
    }

    @Transactional
    public LetterResponse markRead(UUID receiverId, UUID deliveryId) {
        accountAccessService.requireActive(receiverId);
        UUID memoryId = requireDeliveredMemoryId(receiverId, deliveryId);
        Memory source = memoryRepository.findByIdForUpdate(memoryId)
                .orElseThrow(LetterService::unavailable);
        LetterDelivery delivery = requireLockedDelivery(receiverId, deliveryId);
        requireAvailable(receiverId, source);
        delivery.markRead(Instant.now());
        return LetterResponse.from(delivery, LetterAvailability.AVAILABLE);
    }

    @Transactional
    public LetterLikeResponse like(UUID receiverId, UUID deliveryId) {
        User receiver = userRepository.findByIdForUpdate(receiverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED"));
        AccountAccessService.requireActive(receiver);
        UUID memoryId = requireDeliveredMemoryId(receiverId, deliveryId);
        // Source precedes delivery everywhere, including concurrent read/delete operations.
        Memory source = memoryRepository.findByIdForUpdate(memoryId).orElse(null);
        LetterDelivery delivery = requireLockedDelivery(receiverId, deliveryId);
        if (delivery.getLikedAt() != null) {
            return new LetterLikeResponse(deliveryId, LetterLikeResponse.Status.ALREADY_COPIED,
                    delivery.getLikedAt(), null);
        }
        requireAvailable(receiverId, source);

        String imagePath = copyImageForTransaction(source.getImagePath());
        Memory copy = memoryRepository.save(source.copyForOwner(receiverId, imagePath));
        memoryCategoryRepository.findByIdMemoryIdOrderBySlotNo(memoryId)
                .forEach(category -> memoryCategoryRepository.save(category.copyForMemory(copy.getId())));
        delivery.markLiked(Instant.now());
        return new LetterLikeResponse(deliveryId, LetterLikeResponse.Status.COPIED,
                delivery.getLikedAt(), copy.getId());
    }

    private LetterAvailability availability(UUID receiverId, UUID memoryId) {
        return memoryRepository.findById(memoryId)
                .filter(memory -> memoryAccessService.isReadable(receiverId, memory))
                .map(memory -> LetterAvailability.AVAILABLE)
                .orElse(LetterAvailability.UNAVAILABLE);
    }

    private UUID requireDeliveredMemoryId(UUID receiverId, UUID deliveryId) {
        return letterDeliveryRepository.findMemoryIdByIdAndReceiverId(deliveryId, receiverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LETTER_NOT_FOUND"));
    }

    private LetterDelivery requireLockedDelivery(UUID receiverId, UUID deliveryId) {
        return letterDeliveryRepository.findByIdAndReceiverIdForUpdate(deliveryId, receiverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "LETTER_NOT_FOUND"));
    }

    private void requireAvailable(UUID receiverId, Memory source) {
        if (source == null || source.getDistributionType() != DistributionType.LETTER
                || source.getOwnerId().equals(receiverId)
                || !memoryAccessService.isReadable(receiverId, source)) {
            throw unavailable();
        }
    }

    private String copyImageForTransaction(String sourcePath) {
        if (sourcePath == null) {
            return null;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Image copies require a synchronized database transaction");
        }
        String copyPath = imageStorageService.copy(sourcePath);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // An unknown outcome may have committed: never delete its potentially referenced file.
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        imageStorageService.delete(copyPath);
                    } catch (RuntimeException exception) {
                        log.warn("Failed to clean up an image after a rolled-back letter copy");
                    }
                }
            }
        });
        return copyPath;
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.GONE, "LETTER_UNAVAILABLE");
    }
}
