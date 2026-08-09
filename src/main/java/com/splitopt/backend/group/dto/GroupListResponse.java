package com.splitopt.backend.group.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GroupListResponse {
    private List<GroupListItemResponse> groups;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}