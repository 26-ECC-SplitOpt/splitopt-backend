package com.splitopt.backend.user.service;


import com.splitopt.backend.global.exception.BusinessException;
import com.splitopt.backend.global.exception.ErrorCode;
import com.splitopt.backend.user.domain.User;
import com.splitopt.backend.user.dto.SignupRequest;
import com.splitopt.backend.user.dto.SignupResponse;
import com.splitopt.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // 해시 저장
                .name(request.getName())
                .build();

        User saved = userRepository.save(user);

        return SignupResponse.builder()
                .userId(saved.getId())
                .email(saved.getEmail())
                .name(saved.getName())
                .createdAt(saved.getCreatedAt())
                .build();
    }
}