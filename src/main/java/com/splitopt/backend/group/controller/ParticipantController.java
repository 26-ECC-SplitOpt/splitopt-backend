package com.splitopt.backend.group.controller;

import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.dto.AddParticipantRequest;
import com.splitopt.backend.group.dto.AddParticipantResponse;
import com.splitopt.backend.group.dto.GroupParticipantItemResponse;
import com.splitopt.backend.group.dto.ParticipantStatusResponse;
import com.splitopt.backend.group.service.ParticipantService;
import com.splitopt.backend.user.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{groupId}/participants")
@RequiredArgsConstructor
public class ParticipantController {

    private final ParticipantService participantService;

    // 참여자 추가
    @PostMapping
    public ResponseEntity<ApiResponse<AddParticipantResponse>> add(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AddParticipantRequest request
    ) {
        AddParticipantResponse data =
                participantService.add(groupId, principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data));
    }

    // 참여자 목록
    @GetMapping
    public ApiResponse<List<GroupParticipantItemResponse>> list(
            @PathVariable Long groupId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                participantService.list(groupId, principal.getUserId()));
    }

    // 참여자 삭제 (soft-delete, OWNER만)
    @DeleteMapping("/{userId}")
    public ApiResponse<MessageResponse> remove(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                participantService.remove(groupId, principal.getUserId(), userId));
    }

    // 참여자별 정산 현황
    @GetMapping("/{userId}/status")
    public ApiResponse<ParticipantStatusResponse> status(
            @PathVariable Long groupId,
            @PathVariable Long userId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ApiResponse.success(
                participantService.status(groupId, principal.getUserId(), userId));
    }
}
