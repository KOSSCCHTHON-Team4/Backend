package team4.emotionmap;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan   // @ConfigurationProperties 빈 스캔 (StorageProperties 등)
public class EmotionMapApplication {

    public static void main(String[] args) {
        if (args.length > 0 && "storage-init-empty".equals(args[0])) {
            System.exit(ImageStorageActivationCli.run(Arrays.copyOfRange(args, 1, args.length)));
            return;
        }
        SpringApplication.run(EmotionMapApplication.class, args);
    }
}
