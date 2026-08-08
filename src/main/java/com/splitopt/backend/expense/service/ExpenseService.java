package com.splitopt.backend.expense.service;

import com.splitopt.backend.expense.domain.Expense;
import com.splitopt.backend.expense.domain.ExpenseShare;
import com.splitopt.backend.expense.dto.ExpenseCreateRequest;
import com.splitopt.backend.expense.dto.ExpenseResponse;
import com.splitopt.backend.expense.repository.ExpenseRepository;
import com.splitopt.backend.expense.repository.ExpenseShareRepository;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.group.repository.GroupParticipantRepository;
import com.splitopt.backend.group.repository.GroupRepository;
import com.splitopt.backend.schedule.repository.ScheduleRepository;
import com.splitopt.backend.schedule.domain.Schedule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 지출 등록/조회 (API 17~19).
 * TODO: 인증 완성되면 payerId를 파라미터로 받는 대신 @AuthenticationPrincipal에서 꺼내도록 교체.
 */
@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final GroupRepository groupRepository;
    private final GroupParticipantRepository groupParticipantRepository;
    private final ScheduleRepository scheduleRepository;

    @Transactional
    public ExpenseResponse createExpense(Long groupId, Long payerId, ExpenseCreateRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));

        // 결제자가 진짜 이 모임 소속인지 확인 (남의 모임에 등록 못 하게)
        GroupParticipant payer = groupParticipantRepository.findByIdAndGroupIdAndIsActiveTrue(payerId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED, "이 모임의 활성 참여자가 아닙니다."));

        // scheduleId가 있으면 그 일정을 찾아서 연결, 없으면 null(연결 안 함)
        Schedule schedule = null;
        if (request.scheduleId() != null) {
            schedule = scheduleRepository.findByIdAndGroupId(request.scheduleId(), groupId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "일정을 찾을 수 없습니다."));
        }

        // 1. 지출(Expense) 저장
        Expense expense = expenseRepository.save(Expense.builder()
                .group(group)
                .payer(payer)
                .schedule(schedule)
                .title(request.title())
                .amount(request.amount())
                .category(request.category())
                .memo(request.memo())
                .spentAt(request.spentAt())
                .build());

        // 2. 부담 내역(ExpenseShare) 계산 — 균등분담 vs 직접입력 분기
        List<ExpenseShare> shares = (request.splitMethod() == ExpenseCreateRequest.SplitMethod.EQUAL)
                ? buildEqualShares(expense, groupId, request, payer)
                : buildDirectShares(expense, groupId, request);

        expenseShareRepository.saveAll(shares);

        return ExpenseResponse.from(expense, shares);
    }

    /** 균등 분담: 내림 계산 후 나머지는 결제자에게 우선 배분 (3주차 회의 F 조항). */
    private List<ExpenseShare> buildEqualShares(Expense expense, Long groupId,
                                                ExpenseCreateRequest request, GroupParticipant payer) {
        List<Long> participantIds = request.shares().stream()
                .map(ExpenseCreateRequest.ShareInput::participantId)
                .toList();

        Map<Long, GroupParticipant> participantMap = loadParticipants(groupId, participantIds);
        List<GroupParticipant> participants = participantIds.stream()
                .map(participantMap::get)
                .toList();

        int n = participants.size();
        if (n == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "부담자가 1명 이상 필요합니다.");
        }

        // 내림 계산 (예: 41000 / 3 = 13666, 나머지는 버림)
        BigDecimal base = expense.getAmount().divideToIntegralValue(BigDecimal.valueOf(n));
        BigDecimal remainder = expense.getAmount().subtract(base.multiply(BigDecimal.valueOf(n)));

        // 나머지를 받을 대상: 결제자가 부담자 목록에 있으면 결제자, 없으면 목록 맨 앞 사람
        boolean payerIncluded = participants.stream().anyMatch(p -> p.getId().equals(payer.getId()));
        Long remainderTargetId = payerIncluded ? payer.getId() : participants.get(0).getId();

        List<ExpenseShare> shares = new ArrayList<>();
        for (GroupParticipant p : participants) {
            BigDecimal amount = p.getId().equals(remainderTargetId) ? base.add(remainder) : base;
            shares.add(ExpenseShare.builder().expense(expense).participant(p).shareAmount(amount).build());
        }
        return shares;
    }

    /** 직접 입력: Σ(shares) == amount 검증 (3주차 회의 필수 검증). */
    private List<ExpenseShare> buildDirectShares(Expense expense, Long groupId, ExpenseCreateRequest request) {
        List<Long> participantIds = request.shares().stream()
                .map(ExpenseCreateRequest.ShareInput::participantId)
                .toList();
        Map<Long, GroupParticipant> participantMap = loadParticipants(groupId, participantIds);

        List<ExpenseShare> shares = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (ExpenseCreateRequest.ShareInput input : request.shares()) {
            if (input.amount() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "직접 입력 방식은 각 참여자의 금액이 필요합니다.");
            }
            GroupParticipant participant = participantMap.get(input.participantId());
            shares.add(ExpenseShare.builder().expense(expense).participant(participant).shareAmount(input.amount()).build());
            sum = sum.add(input.amount());
        }

        if (sum.compareTo(expense.getAmount()) != 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "부담 금액의 합이 총 금액과 일치하지 않습니다.");
        }
        return shares;
    }

    /** 요청에 담긴 participantId들이 실제로 이 모임 소속(활성)인지 검증하고, ID→엔티티 맵으로 변환. */
    private Map<Long, GroupParticipant> loadParticipants(Long groupId, List<Long> participantIds) {
        if (participantIds.size() != new HashSet<>(participantIds).size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "부담자 목록에 중복된 참여자가 있습니다.");
        }
        List<GroupParticipant> active = groupParticipantRepository.findAllByGroupIdAndIsActiveTrue(groupId);
        Map<Long, GroupParticipant> map = active.stream()
                .collect(Collectors.toMap(GroupParticipant::getId, Function.identity()));

        for (Long id : participantIds) {
            if (!map.containsKey(id)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "모임에 속하지 않은 참여자입니다: " + id);
            }
        }
        return map;
    }

    /** 지출 목록 조회(18). */
    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(Long groupId) {
        return expenseRepository.findAllByGroupId(groupId).stream()
                .map(expense -> ExpenseResponse.from(expense, expenseShareRepository.findAllByExpenseId(expense.getId())))
                .toList();
    }

    /** 지출 상세 조회(19). */
    @Transactional(readOnly = true)
    public ExpenseResponse getExpense(Long groupId, Long expenseId) {
        Expense expense = findExpenseOrThrow(groupId, expenseId);
        return ExpenseResponse.from(expense, expenseShareRepository.findAllByExpenseId(expenseId));
    }

    /** 지출 수정(20). 결제자 본인만 가능 (3주차 회의 D조항). */
    @Transactional
    public ExpenseResponse updateExpense(Long groupId, Long expenseId, Long requesterId, ExpenseCreateRequest request) {
        Expense expense = findExpenseOrThrow(groupId, expenseId);

        if (!expense.isPayer(requesterId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "결제자 본인만 수정할 수 있습니다.");
        }

        expense.update(request.title(), request.amount(), request.category(), request.memo(), request.spentAt());

        // scheduleId가 있으면 그 일정으로 연결/변경, 없으면 연결 해제
        if (request.scheduleId() != null) {
            Schedule schedule = scheduleRepository.findByIdAndGroupId(request.scheduleId(), groupId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "일정을 찾을 수 없습니다."));
            expense.assignSchedule(schedule);
        } else {
            expense.clearSchedule();
        }

        // 기존 부담 내역은 지우고 새로 계산해서 다시 저장 (금액이 바뀌었을 수 있으니 재계산 필수)
        expenseShareRepository.deleteAllByExpenseId(expenseId);
        GroupParticipant payer = expense.getPayer();
        List<ExpenseShare> shares = (request.splitMethod() == ExpenseCreateRequest.SplitMethod.EQUAL)
                ? buildEqualShares(expense, groupId, request, payer)
                : buildDirectShares(expense, groupId, request);
        expenseShareRepository.saveAll(shares);

        return ExpenseResponse.from(expense, shares);
    }

    /** 지출 삭제(21). 결제자 본인 또는 모임 개설자(owner)만 가능 (4주차 회의 채택). */
    @Transactional
    public void deleteExpense(Long groupId, Long expenseId, Long requesterId) {
        Expense expense = findExpenseOrThrow(groupId, expenseId);

        GroupParticipant requester = groupParticipantRepository.findByIdAndGroupIdAndIsActiveTrue(requesterId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED, "이 모임의 활성 참여자가 아닙니다."));

        boolean isPayer = expense.isPayer(requesterId);
        boolean isOwner = requester.getRole() == GroupParticipant.Role.OWNER;

        if (!isPayer && !isOwner) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "결제자 본인 또는 모임 개설자만 삭제할 수 있습니다.");
        }

        expenseShareRepository.deleteAllByExpenseId(expenseId);
        expenseRepository.delete(expense);
    }

    private Expense findExpenseOrThrow(Long groupId, Long expenseId) {
        return expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "지출을 찾을 수 없습니다."));
    }
}