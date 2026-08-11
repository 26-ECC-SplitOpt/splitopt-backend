package com.splitopt.backend.group.service;

import com.splitopt.backend.budget.repository.BudgetRepository;
import com.splitopt.backend.expense.repository.ExpenseRepository;
import com.splitopt.backend.expense.repository.ExpenseShareRepository;
import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.group.domain.GroupParticipant.Role;
import com.splitopt.backend.group.dto.*;
import com.splitopt.backend.group.repository.GroupParticipantRepository;
import com.splitopt.backend.group.repository.GroupRepository;
import com.splitopt.backend.schedule.repository.ScheduleRepository;
import com.splitopt.backend.settlement.dto.ParticipantBalanceResponse;
import com.splitopt.backend.settlement.repository.SettlementRepository;
import com.splitopt.backend.settlement.service.BalanceService;
import com.splitopt.backend.settlement.service.SettlementService;
import com.splitopt.backend.user.domain.User;
import com.splitopt.backend.user.dto.MessageResponse;
import com.splitopt.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupParticipantRepository groupParticipantRepository;
    private final UserRepository userRepository;
    private final SettlementService settlementService;
    private final BalanceService balanceService;
    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final SettlementRepository settlementRepository;
    private final BudgetRepository budgetRepository;
    private final ScheduleRepository scheduleRepository;
    private static final int MAX_PAGE_SIZE = 100;

    //모임 생성
    @Transactional
    public GroupResponse create(Long userId, CreateGroupRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        Group group = Group.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .owner(owner)
                .build();

        Group saved = groupRepository.save(group);

        groupParticipantRepository.save(GroupParticipant.builder()
                .group(saved)
                .user(owner)
                .role(Role.OWNER)
                .displayName(null)
                .build());

        return toResponse(saved, 1);
    }

    private GroupResponse toResponse(Group group, int memberCount) {
        return GroupResponse.builder()
                .groupId(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .currency(group.getCurrency())
                .ownerId(group.getOwner().getId())
                .memberCount(memberCount)
                .createdAt(group.getCreatedAt())
                .build();
    }

    //내 모임 목록
    @Transactional(readOnly = true)
    public GroupListResponse getMyGroups(Long userId, int page, int size) {
        if (page < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "size는 1 이상 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
        PageRequest pageable = PageRequest.of(
                page, size, Sort.by(Sort.Direction.DESC, "group.createdAt"));
        Page<GroupParticipant> membershipPage =
                groupParticipantRepository.findAllByUserIdAndIsActiveTrue(userId, pageable);
        List<GroupListItemResponse> content = membershipPage.getContent().stream()
                .map(m -> toListItem(m.getGroup(), userId))
                .toList();
        return GroupListResponse.builder()
                .groups(content)
                .page(page)
                .size(size)
                .totalElements(membershipPage.getTotalElements())
                .totalPages(membershipPage.getTotalPages())
                .build();
    }


    private GroupListItemResponse toListItem(Group group, Long userId) {
        Long groupId = group.getId();
        int memberCount = (int) groupParticipantRepository.countByGroupIdAndIsActiveTrue(groupId);

        BigDecimal totalExpense = expenseRepository.findAllByGroupId(groupId).stream()
                .map(e -> e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal myBalance = BigDecimal.ZERO;
        var myParticipant = groupParticipantRepository.findByGroupIdAndUserId(groupId, userId);
        if (myParticipant.isPresent()) {
            Long participantId = myParticipant.get().getId();
            myBalance = balanceService.getBalances(groupId).stream()
                    .filter(b -> b.participantId().equals(participantId))
                    .map(ParticipantBalanceResponse::balance)
                    .findFirst()
                    .orElse(BigDecimal.ZERO);
        }

        String settledStatus = settlementService.getSummary(groupId).status().name();

        return GroupListItemResponse.builder()
                .groupId(groupId)
                .name(group.getName())
                .memberCount(memberCount)
                .totalExpense(totalExpense)
                .myBalance(myBalance)
                .settledStatus(settledStatus)
                .createdAt(group.getCreatedAt())
                .build();
    }

    //모임 상세
    @Transactional(readOnly = true)
    public GroupDetailResponse getDetail(Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));

        GroupParticipant me = groupParticipantRepository
                .findByGroupIdAndUserId(groupId, userId)
                .filter(GroupParticipant::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ACCESS_DENIED, "이 모임의 참여자가 아닙니다."));

        List<GroupParticipantItemResponse> participants =
                groupParticipantRepository.findAllByGroupIdAndIsActiveTrue(groupId).stream()
                        .map(p -> GroupParticipantItemResponse.builder()
                                .participantId(p.getId())
                                .userId(p.getUser().getId())
                                .name(p.getEffectiveDisplayName())
                                .role(p.getRole().name())
                                .build())
                        .toList();

        BigDecimal totalExpense = expenseRepository.findAllByGroupId(groupId).stream()
                .map(e -> e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return GroupDetailResponse.builder()
                .groupId(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .currency(group.getCurrency())
                .ownerId(group.getOwner().getId())
                .participants(participants)
                .totalExpense(totalExpense)
                .createdAt(group.getCreatedAt())
                .build();
    }

    //모임 정보 수정(OWNER)
    @Transactional
    public GroupResponse update(Long groupId, Long userId, CreateGroupRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));

        if (!group.getOwner().getId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED, "모임 개설자만 수정할 수 있습니다.");
        }

        group.updateGroupInfo(
                request.getName().trim(),
                request.getDescription());

        int memberCount = (int) groupParticipantRepository
                .countByGroupIdAndIsActiveTrue(groupId);

        return GroupResponse.builder()
                .groupId(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .currency(group.getCurrency())
                .ownerId(group.getOwner().getId())
                .memberCount(memberCount)
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    /**
     * 모임 삭제 (OWNER).
     * H2·MySQL 모두에서 동작하도록 하위 데이터를 앱에서 순서대로 지운다.
     * (participants → expenses RESTRICT 때문에 참여자보다 지출·정산을 먼저 삭제)
     */
    @Transactional
    public MessageResponse delete(Long groupId, Long userId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));

        if (!group.getOwner().getId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED, "모임 개설자만 삭제할 수 있습니다.");
        }

        settlementRepository.deleteAll(settlementRepository.findByGroup_Id(groupId));

        var expenses = expenseRepository.findAllByGroupId(groupId);
        for (var expense : expenses) {
            expenseShareRepository.deleteAllByExpenseId(expense.getId());
        }
        expenseRepository.deleteAll(expenses);

        budgetRepository.findByGroup_Id(groupId).ifPresent(budgetRepository::delete);
        scheduleRepository.deleteAll(scheduleRepository.findAllByGroupId(groupId));

        groupParticipantRepository.deleteAll(
                groupParticipantRepository.findAllByGroupId(groupId));
        groupRepository.delete(group);

        return new MessageResponse("모임이 삭제되었습니다.");
    }
}