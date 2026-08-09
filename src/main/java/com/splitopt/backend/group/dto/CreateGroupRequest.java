package com.splitopt.backend.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateGroupRequest {

    @NotBlank(message = "모임 이름을 입력해주세요.")
    @Size(max = 100, message = "모임 이름은 100자 이하여야 합니다.")
    private String name;

    @Size(max = 500, message = "설명은 500자 이하여야 합니다.")
    private String description;

}