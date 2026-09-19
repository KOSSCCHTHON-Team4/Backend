package team4.emotionmap.platform.security;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuntimeClockConfig {

    @Bean
    public Clock runtimeClock() {
        return Clock.systemUTC();
    }
}
