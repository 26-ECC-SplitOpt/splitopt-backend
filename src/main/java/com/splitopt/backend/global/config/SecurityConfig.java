package com.splitopt.backend.global.config;

import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.global.response.ErrorField;
import com.splitopt.backend.global.security.JwtAuthenticationFilter;
import com.splitopt.backend.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security 설정.
 * <p>JWT 기반 API를 전제로 세션·기본 로그인 폼을 쓰지 않으며,
 * 회원가입·로그인처럼 토큰 없이 호출되어야 하는 경로만 열어 둔다.
 * JWT 필터로 Authorization 헤더의 access 토큰을 읽어 인증 정보를 채운다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        return new JwtAuthenticationFilter(jwtTokenProvider);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false); // 서블릿 컨테이너 자동 등록 비활성 → SecurityFilterChain만 사용
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * HTTP 보안 규칙.
     * <p>signup·login·logout만 인증 없이 허용하고,
     * 그 외 /api/** 는 access 토큰이 필요하다.
     * CSRF는 브라우저 폼 로그인이 아닌 JSON API라 비활성화한다.
     *
     * <p>CORS는 {@link CorsConfig}의 {@code CorsConfigurationSource} 빈을 시큐리티 체인에 연결해
     * 적용한다. CORS 필터는 인증·인가보다 먼저 실행되므로, 프리플라이트(OPTIONS)는 토큰 없이도
     * 통과하고 인증 실패(401) 응답에도 CORS 헤더가 붙는다 — 헤더가 없으면 브라우저가 실제 상태
     * 코드 대신 CORS 오류만 보여줘 원인을 찾기 어렵다.
     *
     * <p>확인해 보니 이 버전에서는 {@code CorsConfigurationSource} 빈만 있어도 자동으로 적용돼
     * 아래 {@code cors()} 호출 없이도 동작한다. 그래도 명시해 두는 이유는, 그 동작이 스프링
     * 버전에 딸린 암묵적 규칙이라 눈에 보이지 않기 때문이다. CORS가 어디서 켜지는지 이 자리에
     * 드러나 있어야 나중에 필터 순서를 손볼 때 놓치지 않는다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                write(res, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, e) ->
                                write(res, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/signup",
                                "/api/auth/login",
                                "/api/auth/logout"
                        ).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void write(HttpServletResponse res, HttpStatus status, ErrorCode code) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getOutputStream(),
                ApiResponse.fail(code.getMessage(),
                        List.of(new ErrorField(null, code.getCode(), code.getMessage()))));
    }
}