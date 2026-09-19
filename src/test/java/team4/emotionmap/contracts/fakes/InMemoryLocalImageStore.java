package team4.emotionmap.contracts.fakes;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.media.LocalImageStore;
import team4.emotionmap.contracts.media.SanitizedImage;
import team4.emotionmap.contracts.media.StoredImageMeta;

/** 파일시스템 없는 가짜 이미지 저장소. duplicateIndependent 는 항상 새 무작위 키를 만든다. */
public final class InMemoryLocalImageStore implements LocalImageStore {

    private record Entry(byte[] bytes, StoredImageMeta meta) {
    }

    private final Map<String, Entry> files = new ConcurrentHashMap<>();

    @Override
    public StoredImageMeta store(SanitizedImage image) {
        String key = UUID.randomUUID() + "." + image.mediaType().extension();
        StoredImageMeta meta = new StoredImageMeta(key, image.mediaType(), image.sizeBytes(), image.width(), image.height());
        files.put(key, new Entry(image.bytes().clone(), meta));
        return meta;
    }

    @Override
    public Optional<StoredImageMeta> describe(String storageKey) {
        if (storageKey == null || storageKey.contains("..") || storageKey.contains("/") || storageKey.contains("\\")) {
            return Optional.empty();
        }
        Entry e = files.get(storageKey);
        return e == null ? Optional.empty() : Optional.of(e.meta());
    }

    @Override
    public InputStream open(String storageKey) {
        Entry e = files.get(storageKey);
        if (e == null) {
            throw ContractError.of(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return new ByteArrayInputStream(e.bytes());
    }

    @Override
    public StoredImageMeta duplicateIndependent(String sourceStorageKey) {
        Entry e = files.get(sourceStorageKey);
        if (e == null) {
            throw ContractError.of(ErrorCode.COPY_FAILED);
        }
        String key = UUID.randomUUID() + "." + e.meta().mediaType().extension();
        StoredImageMeta meta = new StoredImageMeta(key, e.meta().mediaType(), e.meta().sizeBytes(),
                e.meta().width(), e.meta().height());
        files.put(key, new Entry(e.bytes().clone(), meta));
        return meta;
    }

    @Override
    public boolean deleteUnreferenced(String storageKey) {
        return files.remove(storageKey) != null;
    }

    public int size() {
        return files.size();
    }
}
