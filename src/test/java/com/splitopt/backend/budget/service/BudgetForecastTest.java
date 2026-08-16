package com.splitopt.backend.budget.service;

import com.splitopt.backend.budget.domain.BudgetType;
import com.splitopt.backend.budget.dto.BudgetForecastResponse;
import com.splitopt.backend.budget.dto.BudgetForecastResponse.Basis;
import com.splitopt.backend.expense.domain.Expense;
import com.splitopt.backend.expense.domain.ExpenseCategory;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.schedule.domain.Schedule;
import com.splitopt.backend.user.domain.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 예산 초과 예측 테스트 (API 40).
 *
 * <p>예측은 "현재 사용액 ÷ 기간 경과 비율"이라 <b>기간을 어떻게 잡느냐가 전부</b>다.
 * 그래서 기간이 없는 경우·아직 시작 안 한 경우·이미 끝난 경우·길이가 0인 경우를 각각 고정한다.
 *
 * <p>지금 시각에 의존하는 케이스는 정확한 소수점 대신 범위로 단언한다 — 테스트 실행에 걸리는
 * 시간만큼 경과 비율이 움직이기 때문이다. 결정적으로 확인할 수 있는 경계(기간 종료 후 등)는
 * 정확한 값으로 단언한다.
 */
@SpringBootTest
@Transactional
class BudgetForecastTest {

    @PersistenceContext
    private EntityManager em;
    @Autowired
    private BudgetService budgetService;

    private Group group;
    private GroupParticipant payer;

    @BeforeEach
    void setUp() {
        User owner = User.builder().email("f@x.com").password("p").name("F").build();
        em.persist(owner);
        group = Group.builder().name("워크샵").owner(owner).build();
        em.persist(group);
        payer = GroupParticipant.builder().group(group).user(owner)
                .role(GroupParticipant.Role.OWNER).build();
        em.persist(payer);
        em.flush();
    }

    private void budget(String amount) {
        budgetService.upsert(group.getId(), BudgetType.TOTAL, new BigDecimal(amount));
    }

    private void expense(String amount) {
        em.persist(Expense.builder()
                .group(group).payer(payer)
                .title("지출").amount(new BigDecimal(amount))
                .category(ExpenseCategory.ETC)
                .spentAt(LocalDateTime.now())
                .build());
        em.flush();
    }

    private void schedule(LocalDateTime startAt, LocalDateTime endAt) {
        em.persist(Schedule.builder()
                .group(group).title("일정")
                .startAt(startAt).endAt(endAt)
                .build());
        em.flush();
    }

    private BudgetForecastResponse forecast() {
        return budgetService.getForecast(group.getId());
    }

    @Nested
    @DisplayName("예측할 근거가 없으면 예측하지 않는다")
    class NotForecastable {

        @Test
        @DisplayName("일정이 하나도 없으면 basis=NONE이고 예측 필드는 비어 있다")
        void noSchedule() {
            budget("200000");
            expense("50000");

            BudgetForecastResponse res = forecast();

            assertAll(
                    () -> assertEquals(Basis.NONE, res.basis()),
                    () -> assertNull(res.projectedTotal()),
                    () -> assertNull(res.willExceed()),
                    () -> assertNull(res.elapsedDays()),
                    () -> assertNull(res.dailyAverage()),
                    () -> assertNull(res.periodStart()),
                    // 예측은 못 해도 예산·사용액은 그대로 알려준다
                    () -> assertEquals(0, res.totalBudget().compareTo(new BigDecimal("200000"))),
                    () -> assertEquals(0, res.spent().compareTo(new BigDecimal("50000")))
            );
        }

        @Test
        @DisplayName("기간이 아직 시작 전이면 경과 비율이 0이라 예측하지 않는다")
        void beforePeriodStart() {
            budget("200000");
            expense("50000");
            schedule(LocalDateTime.now().plusDays(3), LocalDateTime.now().plusDays(5));

            assertEquals(Basis.NONE, forecast().basis(), "하루도 안 지나 평균을 낼 수 없다");
        }
    }

    @Nested
    @DisplayName("일정 기간이 있으면 경과 비율로 예상 총액을 낸다")
    class ScheduleBased {

        @Test
        @DisplayName("절반쯤 지났는데 예산의 절반을 넘게 썼으면 초과가 예상된다")
        void halfwayOverPace() {
            budget("150000");
            expense("100000");
            // 날짜 단위로 세므로 어제~모레는 4일이고 오늘이 2일째다 — 절반이 지난 상태.
            schedule(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(2));

            BudgetForecastResponse res = forecast();

            assertEquals(Basis.SCHEDULE, res.basis());
            assertEquals(4, res.totalDays());
            assertEquals(2, res.elapsedDays());
            assertEquals(0, res.projectedTotal().compareTo(new BigDecimal("200000")),
                    () -> "이틀에 10만원 썼으니 나흘이면 20만원: " + res.projectedTotal());
            assertTrue(res.willExceed());
            assertTrue(res.projectedOverage().signum() > 0);
        }

        @Test
        @DisplayName("같은 속도라도 예산이 넉넉하면 초과가 아니다")
        void halfwayWithinBudget() {
            budget("500000");
            expense("100000");
            schedule(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

            BudgetForecastResponse res = forecast();

            assertAll(
                    () -> assertFalse(res.willExceed()),
                    () -> assertEquals(0, res.projectedOverage().signum(),
                            "넘지 않을 때는 음수가 아니라 0이다")
            );
        }

        @Test
        @DisplayName("기간이 끝났으면 경과 100%·예상 총액은 현재 사용액과 같다")
        void afterPeriodEnd() {
            budget("100000");
            expense("80000");
            schedule(LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(3));

            BudgetForecastResponse res = forecast();

            assertAll(
                    () -> assertEquals(Basis.SCHEDULE, res.basis()),
                    () -> assertEquals(res.totalDays(), res.elapsedDays(), "기간이 끝나면 전부 지난 것"),
                    () -> assertEquals(0, res.projectedTotal().compareTo(new BigDecimal("80000")),
                            "더 쓸 기간이 없으므로 현재 사용액이 곧 예상 총액"),
                    () -> assertFalse(res.willExceed())
            );
        }

        @Test
        @DisplayName("끝난 기간에 이미 예산을 넘겼으면 초과로 잡히고 초과액이 나온다")
        void afterPeriodEndAlreadyOver() {
            budget("100000");
            expense("130000");
            schedule(LocalDateTime.now().minusDays(5), LocalDateTime.now().minusDays(3));

            BudgetForecastResponse res = forecast();

            assertAll(
                    () -> assertTrue(res.willExceed()),
                    () -> assertEquals(0, res.projectedOverage().compareTo(new BigDecimal("30000")))
            );
        }

        @Test
        @DisplayName("종료 시각 없는 일정은 그 시점을 지났으면 기간이 끝난 것으로 본다")
        void singlePointSchedule() {
            budget("100000");
            expense("60000");
            schedule(LocalDateTime.now().minusHours(2), null);

            BudgetForecastResponse res = forecast();

            assertAll(
                    () -> assertEquals(Basis.SCHEDULE, res.basis()),
                    () -> assertEquals(res.totalDays(), res.elapsedDays(),
                            "길이가 0인 기간은 나눌 수 없다"),
                    () -> assertEquals(0, res.projectedTotal().compareTo(new BigDecimal("60000")))
            );
        }

        @Test
        @DisplayName("일정이 여러 개면 첫 시작 ~ 마지막 종료를 기간으로 본다")
        void periodSpansAllSchedules() {
            budget("300000");
            expense("50000");
            schedule(LocalDateTime.now().minusDays(4), LocalDateTime.now().minusDays(3));
            schedule(LocalDateTime.now().minusDays(2), LocalDateTime.now().minusDays(1));

            BudgetForecastResponse res = forecast();

            assertAll(
                    () -> assertTrue(res.periodStart().isBefore(LocalDateTime.now().minusDays(3)),
                            "가장 이른 시작"),
                    () -> assertTrue(res.periodEnd().isAfter(LocalDateTime.now().minusDays(2)),
                            "가장 늦은 종료"),
                    () -> assertEquals(res.totalDays(), res.elapsedDays())
            );
        }

        @Test
        @DisplayName("지출이 없으면 예상 총액도 0이고 초과가 아니다")
        void noSpending() {
            budget("100000");
            schedule(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

            BudgetForecastResponse res = forecast();

            assertAll(
                    () -> assertEquals(0, res.projectedTotal().signum()),
                    () -> assertFalse(res.willExceed()),
                    () -> assertEquals(0, res.projectedOverage().signum())
            );
        }

        @Test
        @DisplayName("예산 0원에 지출이 있으면 초과로 예상된다")
        void zeroBudget() {
            budget("0");
            expense("10000");
            schedule(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

            assertTrue(forecast().willExceed());
        }
    }

    @Test
    @DisplayName("다른 모임의 일정은 기간에 섞이지 않는다")
    void otherGroupScheduleIsIgnored() {
        budget("100000");
        expense("10000");

        User otherOwner = User.builder().email("f2@x.com").password("p").name("F2").build();
        em.persist(otherOwner);
        Group otherGroup = Group.builder().name("동아리").owner(otherOwner).build();
        em.persist(otherGroup);
        em.persist(Schedule.builder().group(otherGroup).title("남의 일정")
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .build());
        em.flush();

        assertEquals(Basis.NONE, forecast().basis(), "내 모임에는 일정이 없다");
    }

    @Test
    @DisplayName("예산을 설정한 적이 없으면 404")
    void withoutBudgetIsNotFound() {
        schedule(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        BusinessException ex = assertThrows(BusinessException.class, this::forecast);
        assertEquals(ErrorCode.ENTITY_NOT_FOUND, ex.getErrorCode());
    }
}
