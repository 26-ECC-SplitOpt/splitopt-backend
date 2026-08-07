package com.splitopt.backend.global.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * SecurityContext에 넣는 로그인 사용자 정보.
 */
@Getter
@RequiredArgsConstructor
public class UserPrincipal {

    private final Long userId;
}