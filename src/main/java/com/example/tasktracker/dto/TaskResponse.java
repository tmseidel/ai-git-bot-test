package com.example.tasktracker.dto;

import com.example.tasktracker.domain.Task;

import java.time.Instant;

/**
 * API-boundary projection of {@link Task}.
 */
public record TaskResponse(
        Long id,
        String title,
        String status,
        long trackedSeconds,
        Instant startedAt,
        boolean running
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getStatus() != null ? task.getStatus().name() : null,
                task.getTrackedSeconds(),
                task.getStartedAt(),
                task.getStartedAt() != null
        );
    }
}
