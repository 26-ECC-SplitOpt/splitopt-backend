package com.splitopt.backend.group.controller;

import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.global.security.UserPrincipal;
import com.splitopt.backend.group.dto.*;
import com.splitopt.backend.group.service.GroupService;
import com.splitopt.backend.user.dto.MessageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    //모임 생성
    @PostMapping
    public ResponseEntity<ApiResponse<GroupResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateGroupRequest request
    ) {
        GroupResponse data = groupService.create(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data));
    }

    //내 모임 목록
    @GetMapping
    public ApiResponse<GroupListResponse> getMyGroups(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(
                groupService.getMyGroups(principal.getUserId(), page, size));
    }

    // 초대코드로 참여 (/{groupId} 보다 구체적 path를 앞에 둠)
    @PostMapping("/join")
    public ApiResponse<JoinGroupResponse> join(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody JoinGroupRequest request
    ) {
        return ApiResponse.success(
                groupService.joinByInviteCode(principal.getUserId(), request));
    }

    //모임 상세
    @GetMapping("/{groupId}")
    public ApiResponse<GroupDetailResponse> getDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long groupId
    ) {
        return ApiResponse.success(
                groupService.getDetail(groupId, principal.getUserId()));
    }

    //모임 정보 수정
    @PutMapping("/{groupId}")
    public ApiResponse<GroupResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long groupId,
            @Valid @RequestBody CreateGroupRequest request
    ) {
        return ApiResponse.success(
                groupService.update(groupId, principal.getUserId(), request));
    }

    //모임 삭제
    @DeleteMapping("/{groupId}")
    public ApiResponse<MessageResponse> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long groupId
    ) {
        return ApiResponse.success(
                groupService.delete(groupId, principal.getUserId()));
    }

    // 초대코드 재발급 (OWNER)
    @PostMapping("/{groupId}/invite")
    public ResponseEntity<ApiResponse<IssueInviteResponse>> reissueInvite(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long groupId,
            @Valid @RequestBody(required = false) IssueInviteRequest request
    ) {
        IssueInviteRequest body = request != null ? request : new IssueInviteRequest();
        IssueInviteResponse data =
                groupService.reissueInvite(groupId, principal.getUserId(), body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data));
    }
}
