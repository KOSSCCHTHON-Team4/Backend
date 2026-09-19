package team4.emotionmap.memory;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team4.emotionmap.account.AccountAccessService;
import team4.emotionmap.account.User;
import team4.emotionmap.account.UserRepository;
import team4.emotionmap.media.ImageStorageService;
import team4.emotionmap.media.ImageUpload;
import team4.emotionmap.media.ImageUploadService;
import team4.emotionmap.media.StoredImage;
import team4.emotionmap.memory.dto.MemoryCreateRequest;
import team4.emotionmap.memory.dto.MemoryResponse;
import team4.emotionmap.place.Place;
import team4.emotionmap.place.PlaceCategory;
import team4.emotionmap.place.PlaceCategoryRepository;
import team4.emotionmap.place.PlaceRepository;

@Service
@RequiredArgsConstructor
public class MemoryService {
    private final MemoryRepository memoryRepository;
    private final MemoryCategoryRepository memoryCategoryRepository;
    private final MemoryAccessService memoryAccessService;
    private final UserRepository userRepository;
    private final PlaceRepository placeRepository;
    private final PlaceCategoryRepository placeCategoryRepository;
    private final ImageUploadService imageUploadService;
    private final ImageStorageService imageStorageService;

    @Transactional
    public MemoryResponse create(UUID ownerId, MemoryCreateRequest request) {
        requireActiveOwner(ownerId);
        if (request.analysisToken() != null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Analysis tokens are not supported");
        }
        if (request.content() == null || request.content().isBlank() || request.type() == null
                || request.atmospheres() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content, type and atmospheres are required");
        }
        List<String> categoryCodes = request.categoryCodes();
        if (categoryCodes == null || categoryCodes.size() > 3
                || categoryCodes.stream().anyMatch(code -> code == null || code.isBlank())
                || new HashSet<>(categoryCodes).size() != categoryCodes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose up to three distinct category codes");
        }
        List<PlaceCategory> categories = categoryCodes.stream().map(code -> placeCategoryRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category"))).toList();
        Place place = resolvePlace(ownerId, request);
        ImageUpload image = request.imageId() == null ? null
                : imageUploadService.requireAttachable(ownerId, request.imageId());
        Memory memory = memoryRepository.save(Memory.builder()
                .ownerId(ownerId).placeId(place.getId()).content(request.content())
                .distributionType(request.type()).originKind(OriginKind.DIRECT)
                .dataOrigin(DataOrigin.PARTICIPANT)
                .placeLabelSnapshot(place.getLabel()).placeLat(place.getLat()).placeLng(place.getLng())
                .crowdLevel(request.atmospheres().crowdLevel()).spatialFeel(request.atmospheres().spatialFeel())
                .companyFit(request.atmospheres().companyFit()).stayStyle(request.atmospheres().stayStyle())
                .crowdSource(ValueSource.USER).spatialSource(ValueSource.USER)
                .companySource(ValueSource.USER).staySource(ValueSource.USER)
                .atmosphereAnalysisStatus(AtmosphereAnalysisStatus.NOT_RUN)
                .categoryAnalysisStatus(CategoryAnalysisStatus.NOT_RUN)
                .imagePath(image == null ? null : image.getStoragePath())
                .imageMediaType(image == null ? null : image.getMediaType())
                .imageSizeBytes(image == null ? null : image.getSizeBytes())
                .build());
        if (image != null) {
            image.attach(memory.getId());
        }
        for (int index = 0; index < categories.size(); index++) {
            PlaceCategory category = categories.get(index);
            memoryCategoryRepository.save(MemoryCategory.builder()
                    .id(new MemoryCategoryId(memory.getId(), category.getId()))
                    .slotNo((short) (index + 1)).assignmentSource(ValueSource.USER)
                    .labelSnapshot(category.getLabel()).taxonomyVersion(category.getTaxonomyVersion()).build());
        }
        return MemoryResponse.from(memory);
    }

    @Transactional(readOnly = true)
    public MemoryResponse get(UUID userId, UUID id) {
        return MemoryResponse.from(memoryAccessService.requireReadable(userId, id));
    }

    @Transactional(readOnly = true)
    public List<MemoryResponse> findByUser(UUID userId) {
        return memoryRepository.findByOwnerIdAndContentStatusOrderByCreatedAtDesc(userId, ContentStatus.ACTIVE)
                .stream().map(MemoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MemoryResponse> findByPlace(UUID userId, UUID placeId) {
        List<MemoryResponse> visible = memoryRepository
                .findByPlaceIdAndContentStatusOrderByCreatedAtDesc(placeId, ContentStatus.ACTIVE).stream()
                .filter(memory -> memoryAccessService.isReadable(userId, memory))
                .map(MemoryResponse::from).toList();
        if (visible.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found");
        }
        return visible;
    }

    @Transactional(readOnly = true)
    public StoredImage image(UUID userId, UUID memoryId) {
        Memory memory = memoryAccessService.requireReadable(userId, memoryId);
        if (memory.getImagePath() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory has no image");
        }
        return imageStorageService.load(memory.getImagePath());
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        requireActiveOwner(ownerId);
        Memory memory = memoryRepository.findByIdForUpdate(id)
                .filter(candidate -> candidate.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found"));
        if (memory.getContentStatus() != ContentStatus.DELETED) {
            memory.softDelete(Instant.now());
        }
    }

    private void requireActiveOwner(UUID ownerId) {
        User owner = userRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
        AccountAccessService.requireActive(owner);
    }

    private Place resolvePlace(UUID ownerId, MemoryCreateRequest request) {
        Double lat = request.lat();
        Double lng = request.lng();
        if (lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid place coordinates");
        }
        if (request.placeId() == null) {
            String label = request.placeLabel();
            return placeRepository.save(Place.builder().lat(lat).lng(lng)
                    .label(label == null || label.isBlank() ? null : label.strip()).build());
        }
        boolean visible = memoryRepository
                .findByPlaceIdAndContentStatusOrderByCreatedAtDesc(request.placeId(), ContentStatus.ACTIVE).stream()
                .anyMatch(memory -> memoryAccessService.isReadable(ownerId, memory));
        if (!visible) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found");
        }
        Place place = placeRepository.findById(request.placeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Place not found"));
        if (place.getLat().doubleValue() != lat.doubleValue()
                || place.getLng().doubleValue() != lng.doubleValue()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Place coordinates do not match");
        }
        return place;
    }
}
