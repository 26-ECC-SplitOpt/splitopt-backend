package com.splitopt.backend.group.dto;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IssueInviteRequest {

    /** 미입력 시 서비스에서 기본 72시간 적용 */
    @Min(value = 1, message = "만료 시간은 1시간 이상이어야 합니다.")
    private Integer expiresInHours;
}
