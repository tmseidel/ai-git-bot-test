package com.example.tasktracker.repository;

import com.example.tasktracker.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /** Tasks with an active timer, ordered by the most recently started first. */
    List<Task> findAllByStartedAtIsNotNull();

    boolean existsByStartedAtIsNotNull();
}
