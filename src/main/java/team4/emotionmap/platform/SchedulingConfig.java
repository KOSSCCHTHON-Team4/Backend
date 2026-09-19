package team4.emotionmap.platform;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 안전 검사 재시도 등 DB 상태 기반 주기 작업을 켠다(별도 외부 큐 없음). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
