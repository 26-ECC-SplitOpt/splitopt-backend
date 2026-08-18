package com.splitopt.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 서버 기준 시각이 한국 시간인지 확인한다.
 *
 * <p>배포 이미지는 UTC로 뜬다. 서버가 UTC로 돌면 시간대 없이 주고받는 값(일정 시작 시각 등)과
 * 9시간 어긋나, 예산 초과 예측(40)의 "일정이 시작됐는가" 판정이 한국 기준 오전 9시에 넘어가고
 * 기록 시각 전반이 9시간 이르게 내려간다.
 *
 * <p>개발자 노트북은 대개 이미 한국 시각이라 이 테스트가 그냥 통과한다. 실제 방어는
 * <b>CI와 배포 환경(UTC)</b>에서 이뤄진다.
 */
@SpringBootTest
class ServerTimeZoneTest {

    @Test
    @DisplayName("서버 기본 시간대는 Asia/Seoul이다")
    void defaultTimeZoneIsSeoul() {
        assertEquals(ZoneId.of("Asia/Seoul"), TimeZone.getDefault().toZoneId());
    }

    @Test
    @DisplayName("시간대 없이 찍는 현재 시각이 한국 시각과 같다")
    void nowFollowsKoreanWallClock() {
        // LocalDateTime.now()는 기본 시간대를 따른다 — 예측(40)이 '오늘'을 판단하는 방식 그대로다.
        LocalDateTime serverNow = LocalDateTime.now();
        LocalDateTime koreaNow = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        long gapSeconds = Math.abs(ChronoUnit.SECONDS.between(serverNow, koreaNow));
        assertTrue(gapSeconds < Duration.ofMinutes(1).toSeconds(),
                "서버 시각이 한국 시각과 " + gapSeconds + "초 어긋났다 (UTC로 돌면 32400초 차이가 난다)");
    }
}
