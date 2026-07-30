package com.splitopt.backend.user.repository;

import com.splitopt.backend.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email); //회원가입 중복 체크용
}