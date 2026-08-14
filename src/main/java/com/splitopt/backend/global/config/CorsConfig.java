package com.splitopt.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS 설정.
 *
 * <p>브라우저는 다른 출처(origin)로 가는 요청을 서버가 허용한다고 응답했을 때만 결과를 넘겨준다.
 * 프론트엔드는 배포 사이트와 개발 서버(localhost)에서 이 API를 호출하므로 두 출처를 모두 허용해야
 * 한다. 설정이 없으면 curl·Postman은 되는데 브라우저에서만 전부 실패한다 — CORS는 브라우저가
 * 강제하는 규칙이라서다.
 *
 * <p>허용 출처를 상수로 박지 않고 {@code cors.allowed-origins}로 뺀 이유는, 배포 도메인이
 * 바뀌거나 오타가 있었을 때 <b>코드 배포 없이 환경변수만 고쳐서</b> 대응하기 위해서다.
 * 운영에서는 {@code CORS_ALLOWED_ORIGINS}로 덮어쓸 수 있다.
 *
 * <p>실제 적용은 {@link SecurityConfig}에서 이 빈을 시큐리티 체인에 연결해 이뤄진다.
 */
@Configuration
public class CorsConfig {

    /** 허용할 출처 목록. 쉼표로 구분된 프로퍼티가 리스트로 바인딩된다. */
    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);

        // PATCH가 빠지면 정산 상태 변경(27)이 브라우저에서만 막힌다. 실제로 쓰는 메서드를 모두 적는다.
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Authorization이 빠지면 로그인 이후의 모든 요청이 실패한다.
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        // 프론트가 axios withCredentials를 켜도 동작하도록 허용한다. 지금 인증은 쿠키가 아니라
        // Authorization 헤더라 필요하지는 않지만, 허용 출처를 와일드카드 없이 명시했으므로
        // 켜 두는 편이 안전하고 연동 문제도 줄인다.
        config.setAllowCredentials(true);

        // 프리플라이트(OPTIONS) 결과를 1시간 캐시해 요청마다 왕복하지 않게 한다.
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
