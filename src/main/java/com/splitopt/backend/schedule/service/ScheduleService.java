package com.splitopt.backend.schedule.service;

import com.splitopt.backend.expense.domain.Expense;
import com.splitopt.backend.expense.dto.ExpenseResponse;
import com.splitopt.backend.expense.repository.ExpenseRepository;
import com.splitopt.backend.expense.repository.ExpenseShareRepository;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.repository.GroupRepository;
import com.splitopt.backend.schedule.domain.Schedule;
import com.splitopt.backend.schedule.dto.ScheduleCreateRequest;
import com.splitopt.backend.schedule.dto.ScheduleExpensesResponse;
import com.splitopt.backend.schedule.dto.ScheduleResponse;
import com.splitopt.backend.schedule.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final GroupRepository groupRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;

    /** 일정 등록(33). */
    @Transactional
    public ScheduleResponse createSchedule(Long groupId, ScheduleCreateRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));

        Schedule schedule = scheduleRepository.save(Schedule.builder()
                .group(group)
                .title(request.title())
                .location(request.location())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .memo(request.memo())
                .build());

        return ScheduleResponse.from(schedule);
    }

    /** 일정 목록 조회(34). 시작일시 순 정렬. */
    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedules(Long groupId) {
        return scheduleRepository.findAllByGroupId(groupId).stream()
                .sorted((a, b) -> a.getStartAt().compareTo(b.getStartAt()))
                .map(ScheduleResponse::from)
                .toList();
    }

    /** 일정 수정(35). */
    @Transactional
    public ScheduleResponse updateSchedule(Long groupId, Long scheduleId, ScheduleCreateRequest request) {
        Schedule schedule = findScheduleOrThrow(groupId, scheduleId);
        schedule.update(request.title(), request.location(), request.startAt(), request.endAt(), request.memo());
        return ScheduleResponse.from(schedule);
    }

    /** 일정 삭제(36). 연결된 지출은 삭제하지 않고 schedule_id만 NULL로 해제. */
    @Transactional
    public void deleteSchedule(Long groupId, Long scheduleId) {
        Schedule schedule = findScheduleOrThrow(groupId, scheduleId);

        List<Expense> linkedExpenses = expenseRepository.findAllByScheduleId(scheduleId);
        linkedExpenses.forEach(Expense::clearSchedule); // 연결만 해제, 지출 자체는 안 지움

        scheduleRepository.delete(schedule);
    }

    /** 일정별 지출 조회(37). 그 일정에 연결된 지출 리스트 + 합계. */
    @Transactional(readOnly = true)
    public ScheduleExpensesResponse getScheduleExpenses(Long groupId, Long scheduleId) {
        Schedule schedule = findScheduleOrThrow(groupId, scheduleId);

        List<Expense> expenses = expenseRepository.findAllByScheduleId(scheduleId);
        List<ExpenseResponse> expenseResponses = expenses.stream()
                .map(e -> ExpenseResponse.from(e, expenseShareRepository.findAllByExpenseId(e.getId())))
                .toList();

        BigDecimal total = expenses.stream()
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ScheduleExpensesResponse(ScheduleResponse.from(schedule), expenseResponses, total);
    }

    private Schedule findScheduleOrThrow(Long groupId, Long scheduleId) {
        return scheduleRepository.findByIdAndGroupId(scheduleId, groupId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "일정을 찾을 수 없습니다."));
    }
}