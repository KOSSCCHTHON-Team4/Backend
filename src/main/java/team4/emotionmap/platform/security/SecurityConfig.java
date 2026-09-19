package team4.emotionmap.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import team4.emotionmap.platform.web.ApiErrorWriter;

/**
 * Spring Security 설정(C02 일부).
 *
 * <ul>
 *   <li>공개: {@code POST /auth/login}(확정). {@code POST /auth/signup} 은 baseline 잔재이며 A01 에서 제거 예정
 *       (공개 회원가입 없음 — API_SPEC 3.1). Swagger·health 는 확인 편의상 공개.</li>
 *   <li>그 외 전부 Bearer 인증 필요. 401/403 본문은 {@code ApiError} JSON.</li>
 *   <li>온보딩 전 허용 경로(/config, /atmosphere-axes, /place-categories, /users/me, /users/me/onboarding)와
 *       "현재 계정 상태 재확인"은 인증 뒤 서비스 계층이 {@code AccountAccessReader} 로 판정한다(A01/A02 단계).</li>
 *   <li>모든 응답 {@code Cache-Control: private, no-store}(API_SPEC 2.1). 개인 JSON·이미지가 공유 캐시에 남지 않게 한다.</li>
 *   <li>CORS 허용 origin·노출 헤더는 D03/FE 합의 후 설정값으로 추가한다(현재 미설정).</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final ApiErrorWriter apiErrorWriter;

    public SecurityConfig(JwtTokenProvider tokenProvider, ApiErrorWriter apiErrorWriter) {
        this.tokenProvider = tokenProvider;
        this.apiErrorWriter = apiErrorWriter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .cacheControl(cache -> cache.disable())
                        .addHeaderWriter(new StaticHeadersWriter("Cache-Control", ApiErrorWriter.CACHE_CONTROL_VALUE)))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(new ApiErrorAuthenticationEntryPoint(apiErrorWriter))
                        .accessDeniedHandler(new ApiErrorAccessDeniedHandler(apiErrorWriter)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth/signup", "/auth/login").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
