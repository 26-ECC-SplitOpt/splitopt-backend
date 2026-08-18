package com.splitopt.backend;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class BackendApplication {

	/**
	 * 서버 기준 시각을 한국 시간으로 고정한다.
	 *
	 * <p>배포 이미지({@code eclipse-temurin})는 UTC로 뜬다. 그런데 일정 시작 시각처럼 우리가 주고받는
	 * 값은 시간대가 없는 벽시계 시각이고, 사용자는 그것을 한국 시각으로 입력한다. 서버만 UTC로
	 * 돌면 그 둘을 비교하는 순간 9시간이 어긋난다.
	 *
	 * <p>실제로 드러난 곳:
	 * <ul>
	 *   <li>예산 초과 예측(40) — "일정이 시작됐는가" 판정이 서버 날짜로 이뤄져, 날짜가 한국 기준
	 *       오전 9시에 넘어갔다. 여행 첫날 아침에는 예측이 나오지 않고, 여행 중에도 매일 0~9시
	 *       사이에는 경과 일수가 하루 적게 잡혀 예상 총액이 부풀려졌다.</li>
	 *   <li>기록 시각 전반 — 정산 완료·참여·초대 만료 등이 모두 9시간 이르게 내려갔다.</li>
	 * </ul>
	 *
	 * <p>배포 설정(Dockerfile의 {@code TZ})에만 두지 않고 여기서 고정하는 이유는, 그러면 로컬·CI·
	 * 배포가 서로 다른 시각으로 돌아 같은 코드가 환경마다 다르게 동작하기 때문이다. 시간대를
	 * 애플리케이션의 성질로 두면 어디서 띄우든 같다.
	 */
	@PostConstruct
	void setDefaultTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
