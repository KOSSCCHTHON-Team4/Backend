package team4.emotionmap;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import team4.emotionmap.media.ImageFileFence;
import team4.emotionmap.media.ImageRootBinding;
import team4.emotionmap.media.ImageUpload;
import team4.emotionmap.media.ImageUploadRepository;
import team4.emotionmap.media.StorageProperties;
import team4.emotionmap.memory.Memory;
import team4.emotionmap.memory.MemoryImageReferenceReader;
import team4.emotionmap.memory.MemoryRepository;

/** Explicit composition root, deliberately not a component-scanned configuration. */
@EnableAutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
@EntityScan(basePackageClasses = {ImageUpload.class, Memory.class})
@EnableJpaRepositories(basePackageClasses = {ImageUploadRepository.class, MemoryRepository.class})
@Import({ImageRootBinding.class, ImageFileFence.class, MemoryImageReferenceReader.class})
public class ImageStorageActivationBootstrap {
}
