package com.splitopt.backend.settlement.controller;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * status 파라미터 검증 단위 테스트.
 * 지원하지 않는 값은 {@link BusinessException}(INVALID_INPUT)로 거부되며,
 * 이는 GlobalExceptionHandler가 400으로 변환한다. (조용한 전체 반환 방지)
 */
class SettlementControllerTest {

    // 잘못된 status는 service 호출 전에 거부되므로 service는 필요 없음
    private final SettlementController controller = new SettlementController(null);

    @Test
    @DisplayName("지원하지 않는 status 값(오타)은 INVALID_INPUT으로 거부된다")
    void rejectsUnknownStatus() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.getSettlements(1L, "pendng"));
        assertEquals(ErrorCode.INVALID_INPUT, ex.getErrorCode());
    }

    @Test
    @DisplayName("정의되지 않은 임의 status도 거부된다")
    void rejectsArbitraryStatus() {
        assertThrows(BusinessException.class,
                () -> controller.getSettlements(1L, "done"));
    }
}
