package com.splitopt.backend.group.service;

import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.group.domain.GroupParticipant;
import com.splitopt.backend.group.repository.GroupParticipantRepository;
import com.splitopt.backend.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모임 범위 API의 공통 인가 가드.
 *
 * <p>경로에 {@code groupId}만 받는 API(정산·잔액·예산 등)는 그 자체로는 호출자가 해당 모임
 * 사람인지 알 수 없다. 인증만 통과하면 groupId를 아는 누구나 남의 모임 데이터를 조회·변경할 수
 * 있으므로(IDOR), 각 서비스 진입점에서 이 가드로 로그인 사용자의 참여 여부를 먼저 확인한다.
 *
 * <p>없는 모임은 404, 모임은 있지만 참여자가 아니면 403 — {@link GroupService}의 모임 상세(7)와
 * 같은 규칙이다.
 */
@Component
@RequiredArgsConstructor
public class GroupAccessGuard {

    private final GroupRepository groupRepository;
    private final GroupParticipantRepository groupParticipantRepository;

    /**
     * 로그인 사용자가 이 모임의 <b>활성</b> 참여자인지 확인하고 참여자 엔티티를 돌려준다.
     *
     * <p>탈퇴자(is_active=false)는 거부한다. 모임 상세(7)와 같은 기준이며, 탈퇴한 사람이 모임
     * 데이터를 계속 들여다볼 수 있으면 안 되기 때문이다.
     *
     * @return 요청자의 참여자 엔티티 — 참여자 id가 필요한 권한 검증(27 SEND/CONFIRM)에 쓴다
     * @throws BusinessException 모임이 없으면 404, 활성 참여자가 아니면 403
     */
    @Transactional(readOnly = true)
    public GroupParticipant requireActiveParticipant(Long groupId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (!groupRepository.existsById(groupId)) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "모임을 찾을 수 없습니다.");
        }
        return groupParticipantRepository.findByGroupIdAndUserId(groupId, userId)
                .filter(GroupParticipant::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED, "이 모임의 참여자가 아닙니다."));
    }

    /**
     * 참여 여부만 확인한다(조회 계열). 반환값이 필요 없을 때 의도를 드러내려고 둔 이름이며
     * 검증 규칙은 {@link #requireActiveParticipant}와 같다.
     */
    @Transactional(readOnly = true)
    public void requireMember(Long groupId, Long userId) {
        requireActiveParticipant(groupId, userId);
    }
}
