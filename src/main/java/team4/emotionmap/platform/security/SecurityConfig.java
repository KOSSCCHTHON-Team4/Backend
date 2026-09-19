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

/** Stateless UUID JWT authentication; login, documentation and health are public. */
@Configuration
public class SecurityConfig {

    private final JwtTokenProvider tokenProvider;
    private final AccountAccessGuard accountAccessGuard;

    public SecurityConfig(JwtTokenProvider tokenProvider, AccountAccessGuard accountAccessGuard) {
        this.tokenProvider = tokenProvider;
        this.accountAccessGuard = accountAccessGuard;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Preserve the original 4xx/5xx during the container's internal error dispatch.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/auth/login").permitAll()
                        // 공개: API 문서
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 공개: 헬스체크
                        .requestMatchers("/actuator/health").permitAll()
                        // 그 외 전부 인증 필요
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                response.sendError(401, "AUTH_REQUIRED")))
                .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, accountAccessGuard),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
