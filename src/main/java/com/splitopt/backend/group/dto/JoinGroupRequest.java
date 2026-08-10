package com.splitopt.backend.group.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JoinGroupRequest {

    @NotBlank(message = "초대코드를 입력해주세요.")
    private String inviteCode;
}
