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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_INVITE_HOURS = 72;
    private static final int INVITE_CODE_LENGTH = 8;
    private static final String INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

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

    //모임 생성 (+ 초대코드 자동 발급)
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

        issueInviteOn(saved, DEFAULT_INVITE_HOURS);

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
                .inviteCode(group.getInviteCode())
                .inviteExpiresAt(group.getInviteExpiresAt())
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

        groupParticipantRepository
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
                .inviteCode(group.getInviteCode())
                .inviteExpiresAt(group.getInviteExpiresAt())
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

    /** 초대코드 재발급 (OWNER). 기존 코드는 덮어써서 무효화된다. */
    @Transactional
    public IssueInviteResponse reissueInvite(Long groupId, Long userId, IssueInviteRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));

        if (!group.getOwner().getId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED, "모임 개설자만 초대코드를 재발급할 수 있습니다.");
        }

        int hours = request.getExpiresInHours() != null
                ? request.getExpiresInHours()
                : DEFAULT_INVITE_HOURS;
        issueInviteOn(group, hours);

        return IssueInviteResponse.builder()
                .inviteCode(group.getInviteCode())
                .inviteUrl(null)
                .expiresAt(group.getInviteExpiresAt())
                .build();
    }

    /** 초대코드로 모임 참여. 탈퇴 이력이 있으면 재활성화. */
    @Transactional
    public JoinGroupResponse joinByInviteCode(Long userId, JoinGroupRequest request) {
        String code = request.getInviteCode().trim();
        Group group = groupRepository.findByInviteCode(code)
                .filter(Group::isInviteValid)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INVITE_CODE));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        var existing = groupParticipantRepository.findByGroupIdAndUserId(group.getId(), userId);
        GroupParticipant participant;
        if (existing.isPresent()) {
            participant = existing.get();
            if (participant.isActive()) {
                throw new BusinessException(ErrorCode.ALREADY_JOINED);
            }
            participant.reactivate(Role.MEMBER, null);
        } else {
            participant = groupParticipantRepository.save(GroupParticipant.builder()
                    .group(group)
                    .user(user)
                    .role(Role.MEMBER)
                    .displayName(null)
                    .build());
        }

        return JoinGroupResponse.builder()
                .groupId(group.getId())
                .participantId(participant.getId())
                .name(group.getName())
                .role(participant.getRole().name())
                .joinedAt(participant.getJoinedAt())
                .build();
    }

    private void issueInviteOn(Group group, int hours) {
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(hours);
        for (int attempt = 0; attempt < 20; attempt++) {
            group.issueInvite(randomInviteCode(), expiresAt);
            try {
                groupRepository.flush();
                return;
            } catch (DataIntegrityViolationException e) {
                if (!isInviteCodeConflict(e)) {
                    throw e;
                }
            }
        }
        throw new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR, "초대코드 생성에 실패했습니다. 다시 시도해주세요.");
    }

    private boolean isInviteCodeConflict(DataIntegrityViolationException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage()).toLowerCase();
        return message.contains("uk_groups_invite_code") || message.contains("invite_code");
    }

    private String randomInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_ALPHABET.charAt(RANDOM.nextInt(INVITE_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
