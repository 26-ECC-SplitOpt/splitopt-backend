package com.splitopt.backend.group.repository;

import com.splitopt.backend.group.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, Long> {
}