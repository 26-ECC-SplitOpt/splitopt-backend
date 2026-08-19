package com.splitopt.backend.expense.controller;

import com.splitopt.backend.expense.dto.ExpenseCreateRequest;
import com.splitopt.backend.expense.dto.ExpenseResponse;
import com.splitopt.backend.expense.dto.ExpenseScheduleLinkRequest;
import com.splitopt.backend.expense.service.ExpenseService;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.service.GroupAccessGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 지출 관리 API (17~21).
 *
 * <p><b>요청자는 인증 principal에서만 얻는다.</b> 예전에는 {@code ?payerId=}·{@code ?requesterId=}
 * 쿼리 파라미터로 받았는데, 개정안 C-2가 정한 "결제자는 로그인 사용자로 서버가 고정"과 어긋났고
 * 클라이언트가 다른 참여자로 위장할 수 있어 권한 규칙(D)이 사실상 성립하지 않았다.
 *
 * <p>또한 프론트가 명세대로 그 파라미터를 보내지 않으면 <b>본문 검증 전에</b> 바인딩이 실패해
 * 500이 났다. 지출 등록이 막히던 직접 원인이다.
 */
@RestController
@RequestMapping("/api/groups/{groupId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final GroupAccessGuard groupAccessGuard;

    /** 지출 등록(17). 결제자는 로그인 사용자로 고정한다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpenseResponse> createExpense(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ExpenseCreateRequest request) {
        Long payerId = participantId(groupId, principal);
        return ApiResponse.success(expenseService.createExpense(groupId, payerId, request),
                "지출이 등록되었습니다.");
    }

    /** 지출 목록 조회(18). */
    @GetMapping
    public ApiResponse<List<ExpenseResponse>> getExpenses(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupAccessGuard.requireMember(groupId, userId(principal));
        return ApiResponse.success(expenseService.getExpenses(groupId));
    }

    /** 지출 상세 조회(19). */
    @GetMapping("/{expenseId}")
    public ApiResponse<ExpenseResponse> getExpense(
            @PathVariable Long groupId,
            @PathVariable Long expenseId,
            @AuthenticationPrincipal UserPrincipal principal) {
        groupAccessGuard.requireMember(groupId, userId(principal));
        return ApiResponse.success(expenseService.getExpense(groupId, expenseId));
    }

    /** 지출 수정(20). 결제자 본인만 — 판정은 서비스가 한다. */
    @PutMapping("/{expenseId}")
    public ApiResponse<ExpenseResponse> updateExpense(
            @PathVariable Long groupId,
            @PathVariable Long expenseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ExpenseCreateRequest request) {
        Long requesterId = participantId(groupId, principal);
        return ApiResponse.success(expenseService.updateExpense(groupId, expenseId, requesterId, request),
                "지출이 수정되었습니다.");
    }

    /**
     * 지출에 연결된 일정만 변경/해제한다. <b>모임의 활성 참여자면 누구나</b> 할 수 있다.
     *
     * <p>일정 상세 화면에서 지출을 연결하기 위한 경로다. 수정(20)으로는 할 수 없다 —
     * 그쪽은 제목·금액·부담 내역을 모두 요구해서, 일정만 바꾸려다 나머지를 지우게 된다.
     *
     * <p>결제자로 제한하지 않는 이유는 {@code ExpenseService#linkSchedule} 참고. 참여자 확인은
     * 여기서 끝내므로 서비스는 요청자를 받지 않는다 — 이 호출을 지우면 인가가 통째로 사라진다.
     */
    @PatchMapping("/{expenseId}/schedule")
    public ApiResponse<ExpenseResponse> linkSchedule(
            @PathVariable Long groupId,
            @PathVariable Long expenseId,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) ExpenseScheduleLinkRequest request) {
        groupAccessGuard.requireActiveParticipant(groupId, userId(principal));
        Long scheduleId = request != null ? request.scheduleId() : null;
        return ApiResponse.success(
                expenseService.linkSchedule(groupId, expenseId, scheduleId),
                scheduleId != null ? "일정이 연결되었습니다." : "일정 연결이 해제되었습니다.");
    }

    /** 지출 삭제(21). 결제자 본인 또는 모임 개설자 — 판정은 서비스가 한다. */
    @DeleteMapping("/{expenseId}")
    public ApiResponse<Void> deleteExpense(
            @PathVariable Long groupId,
            @PathVariable Long expenseId,
            @AuthenticationPrincipal UserPrincipal principal) {
        expenseService.deleteExpense(groupId, expenseId, participantId(groupId, principal));
        return ApiResponse.success();
    }

    /**
     * 로그인 사용자를 이 모임의 참여자로 해석한다. 모임이 없으면 404, 참여자가 아니면 403.
     *
     * <p>반환값은 참여자 id다. 서비스가 결제자·요청자를 참여자 id로 다루기 때문이며,
     * 이렇게 서버가 해석하므로 클라이언트가 다른 참여자를 지정할 수 없다.
     */
    private Long participantId(Long groupId, UserPrincipal principal) {
        return groupAccessGuard.requireActiveParticipant(groupId, userId(principal)).getId();
    }

    /** principal이 비어 있으면(필터가 인증을 못 채운 경우) 401. */
    private Long userId(UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.getUserId();
    }
}
