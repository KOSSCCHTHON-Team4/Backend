package team4.emotionmap.memory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team4.emotionmap.contracts.memory.MemoryImageReferences;

@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
public class MemoryImageReferenceReader implements MemoryImageReferences {

    private final MemoryRepository memoryRepository;

    @Override
    public boolean existsReference(String storageKey) {
        return memoryRepository.existsByImagePath(storageKey);
    }

    @Override
    public boolean existsAnyReference() {
        return memoryRepository.existsByImagePathIsNotNull();
    }
}
