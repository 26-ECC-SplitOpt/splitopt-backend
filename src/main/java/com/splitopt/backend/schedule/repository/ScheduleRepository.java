package com.splitopt.backend.schedule.repository;

import com.splitopt.backend.schedule.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findAllByGroupId(Long groupId);
}