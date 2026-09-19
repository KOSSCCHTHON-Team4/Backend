package team4.emotionmap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan   // @ConfigurationProperties 빈 스캔 (StorageProperties 등)
public class EmotionMapApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmotionMapApplication.class, args);
    }
}
