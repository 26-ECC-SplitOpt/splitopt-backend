package com.splitopt.backend.group.service;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.Group;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.group.domain.GroupParticipant.Role;
import com.splitopt.backend.group.dto.AddParticipantRequest;
import com.splitopt.backend.group.dto.AddParticipantResponse;
import com.splitopt.backend.group.dto.GroupParticipantItemResponse;
import com.splitopt.backend.group.dto.ParticipantStatusResponse;
import com.splitopt.backend.group.repository.GroupParticipantRepository;
import com.splitopt.backend.group.repository.GroupRepository;
import com.splitopt.backend.settlement.domain.Settlement;
import com.splitopt.backend.settlement.domain.SettlementStatus;
import com.splitopt.backend.settlement.dto.ParticipantBalanceResponse;
import com.splitopt.backend.settlement.dto.SettlementSummaryResponse;
import com.splitopt.backend.settlement.repository.SettlementRepository;
import com.splitopt.backend.settlement.service.BalanceService;
import com.splitopt.backend.user.domain.User;
import com.splitopt.backend.user.dto.MessageResponse;
import com.splitopt.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParticipantService {

    private final GroupRepository groupRepository;
    private final GroupParticipantRepository groupParticipantRepository;
    private final UserRepository userRepository;
    private final BalanceService balanceService;
    private final SettlementRepository settlementRepository;

    /**
     * 참여자 추가. 가입된 유저를 userId로 모임에 MEMBER로 등록한다.
     * OWNER만 가능. 탈퇴(soft-delete) 이력이 있으면 재활성화한다.
     */
    @Transactional
    public AddParticipantResponse add(Long groupId, Long actorUserId, AddParticipantRequest request) {
        Group group = requireGroup(groupId);
        requireOwner(group, actorUserId, "모임 개설자만 참여자를 추가할 수 있습니다.");

        User target = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ENTITY_NOT_FOUND, "추가할 사용자를 찾을 수 없습니다."));

        var existing = groupParticipantRepository.findByGroupIdAndUserId(groupId, target.getId());
        if (existing.isPresent()) {
            GroupParticipant participant = existing.get();
            if (participant.isActive()) {
                throw new BusinessException(
                        ErrorCode.INVALID_STATE, "이미 이 모임의 참여자입니다.");
            }
            participant.reactivate(Role.MEMBER, null);
            return toAddResponse(participant);
        }

        GroupParticipant saved = groupParticipantRepository.save(GroupParticipant.builder()
                .group(group)
                .user(target)
                .role(Role.MEMBER)
                .displayName(null)
                .build());

        return toAddResponse(saved);
    }

    /** 참여자 목록. 활성 참여자만, 모임 멤버만 조회 가능. */
    @Transactional(readOnly = true)
    public List<GroupParticipantItemResponse> list(Long groupId, Long actorUserId) {
        requireGroup(groupId);
        requireActiveMember(groupId, actorUserId);

        return groupParticipantRepository.findAllByGroupIdAndIsActiveTrue(groupId).stream()
                .map(this::toItemResponse)
                .toList();
    }

    /**
     * 참여자 삭제(soft-delete). OWNER만 MEMBER를 강퇴할 수 있다.
     * OWNER 본인은 삭제 불가 — 모임 삭제(API 9)를 사용한다.
     * 미정산 채무(netBalance ≠ 0)가 남아 있으면 409.
     */
    @Transactional
    public MessageResponse remove(Long groupId, Long actorUserId, Long targetUserId) {
        Group group = requireGroup(groupId);
        requireOwner(group, actorUserId, "모임 개설자만 참여자를 삭제할 수 있습니다.");

        GroupParticipant target = groupParticipantRepository
                .findByGroupIdAndUserId(groupId, targetUserId)
                .filter(GroupParticipant::isActive)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ENTITY_NOT_FOUND, "참여자를 찾을 수 없습니다."));

        if (target.getRole() == Role.OWNER) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE, "모임 개설자는 삭제할 수 없습니다.");
        }

        requireNoUnsettledBalance(groupId, target.getId());

        target.deactivate();
        return new MessageResponse("참여자가 모임에서 제외되었습니다.");
    }

    //참여자별 정산 현황
    @Transactional(readOnly = true)
    public ParticipantStatusResponse status(Long groupId, Long actorUserId, Long targetUserId) {
        requireGroup(groupId);
        requireActiveMember(groupId, actorUserId);

        GroupParticipant target = groupParticipantRepository
                .findByGroupIdAndUserId(groupId, targetUserId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ENTITY_NOT_FOUND, "참여자를 찾을 수 없습니다."));

        ParticipantBalanceResponse balance = balanceService.getBalances(groupId).stream()
                .filter(b -> b.participantId().equals(target.getId()))
                .findFirst()
                .orElse(new ParticipantBalanceResponse(
                        target.getId(),
                        target.getEffectiveDisplayName(),
                        target.isActive(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO));

        List<Settlement> mine = settlementRepository.findMine(groupId, targetUserId);
        List<ParticipantStatusResponse.SettlementLeg> toSend = new ArrayList<>();
        List<ParticipantStatusResponse.SettlementLeg> toReceive = new ArrayList<>();
        long completed = 0;

        for (Settlement s : mine) {
            if (s.getStatus() == SettlementStatus.COMPLETED) {
                completed++;
                continue;
            }
            boolean iAmSender = s.getFromParticipant().getUser().getId().equals(targetUserId);
            if (iAmSender) {
                GroupParticipant counter = s.getToParticipant();
                toSend.add(ParticipantStatusResponse.SettlementLeg.builder()
                        .settlementId(s.getId())
                        .toUserId(counter.getUser().getId())
                        .toName(counter.getEffectiveDisplayName())
                        .amount(s.getAmount())
                        .status(s.getStatus().name())
                        .build());
            } else {
                GroupParticipant counter = s.getFromParticipant();
                toReceive.add(ParticipantStatusResponse.SettlementLeg.builder()
                        .settlementId(s.getId())
                        .fromUserId(counter.getUser().getId())
                        .fromName(counter.getEffectiveDisplayName())
                        .amount(s.getAmount())
                        .status(s.getStatus().name())
                        .build());
            }
        }

        String settledStatus = SettlementSummaryResponse.of(mine.size(), completed).status().name();

        return ParticipantStatusResponse.builder()
                .userId(target.getUser().getId())
                .name(target.getEffectiveDisplayName())
                .paidAmount(balance.paid())
                .burdenAmount(balance.owed())
                .balance(balance.balance())
                .toSend(toSend)
                .toReceive(toReceive)
                .settledStatus(settledStatus)
                .build();
    }

    /** 순잔액(netBalance)이 0이 아니면 미정산 채무가 남은 것으로 본다. */
    private void requireNoUnsettledBalance(Long groupId, Long participantId) {
        boolean hasDebt = balanceService.getBalances(groupId).stream()
                .filter(b -> b.participantId().equals(participantId))
                .map(ParticipantBalanceResponse::netBalance)
                .anyMatch(net -> net.compareTo(BigDecimal.ZERO) != 0);
        if (hasDebt) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATE, "미정산 채무가 남아 있어 삭제할 수 없습니다.");
        }
    }

    private Group requireGroup(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다."));
    }

    private void requireActiveMember(Long groupId, Long userId) {
        boolean isMember = groupParticipantRepository
                .findByGroupIdAndUserId(groupId, userId)
                .filter(GroupParticipant::isActive)
                .isPresent();
        if (!isMember) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED, "이 모임의 참여자가 아닙니다.");
        }
    }

    private void requireOwner(Group group, Long userId, String message) {
        if (!group.getOwner().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, message);
        }
    }

    private AddParticipantResponse toAddResponse(GroupParticipant participant) {
        return AddParticipantResponse.builder()
                .participantId(participant.getId())
                .userId(participant.getUser().getId())
                .name(participant.getEffectiveDisplayName())
                .role(participant.getRole().name())
                .joinedAt(participant.getJoinedAt())
                .build();
    }

    private GroupParticipantItemResponse toItemResponse(GroupParticipant participant) {
        return GroupParticipantItemResponse.builder()
                .participantId(participant.getId())
                .userId(participant.getUser().getId())
                .name(participant.getEffectiveDisplayName())
                .role(participant.getRole().name())
                .build();
    }
}
