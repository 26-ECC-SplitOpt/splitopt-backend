package com.splitopt.backend.group.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AddParticipantRequest {

    @NotNull(message = "추가할 사용자 ID를 입력해주세요.")
    private Long userId;
}
