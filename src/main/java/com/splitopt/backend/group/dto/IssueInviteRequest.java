package com.splitopt.backend.group.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class IssueInviteRequest {

    /** 미입력 시 서비스에서 기본 72시간 적용. 상한 168시간(7일)으로 노출 기간을 제한한다. */
    @Min(value = 1, message = "만료 시간은 1시간 이상이어야 합니다.")
    @Max(value = 168, message = "만료 시간은 168시간(7일) 이하여야 합니다.")
    private Integer expiresInHours;
}
