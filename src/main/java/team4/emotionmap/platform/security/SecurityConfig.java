package team4.emotionmap.platform.security;

import jakarta.servlet.DispatcherType;
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
 * Stateless UUID JWT authentication with current account/onboarding checks.
 * Only POST /v1/auth/login, documentation and health are public.
 * Failures use ApiError JSON; all responses prevent shared caching.
 */
@Configuration
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final ApiErrorWriter apiErrorWriter;
    private final AccountAccessGuard accountAccessGuard;

    public SecurityConfig(JwtTokenProvider tokenProvider, ApiErrorWriter apiErrorWriter,
                          AccountAccessGuard accountAccessGuard) {
        this.tokenProvider = tokenProvider;
        this.apiErrorWriter = apiErrorWriter;
        this.accountAccessGuard = accountAccessGuard;
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
                        // Preserve the original 4xx/5xx during the container's internal error dispatch.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/auth/login").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, accountAccessGuard, apiErrorWriter),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
