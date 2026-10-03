package com.example.tasktracker.service;

import com.example.tasktracker.domain.Task;
import com.example.tasktracker.exception.InvalidTaskStateException;
import com.example.tasktracker.exception.TaskNotFoundException;
import com.example.tasktracker.repository.TaskRepository;
import lombok.RequiredArgsConstructor;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Business logic for task and time-tracking operations.
 *
 * <p>Time is measured with epoch-based {@link Instant} deltas so the tracked
 * duration is stable across server restarts and timezone changes.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<Task> list() {
        // Running tasks first (most recent start on top), then by creation date.
        return taskRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((Task t) -> t.getStartedAt() == null, Boolean::compareTo)
                        .thenComparing(Task::getCreatedAt,
                                Comparator.comparing(Instant::toEpochMilli,
                                        Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public Task create(String title) {
        var task = Task.builder()
                .title(title)
                .status(Task.Status.TODO)
                .trackedSeconds(0)
                .build();
        return taskRepository.save(task);
    }

    @Transactional
    public Task update(Long id, String title, Task.Status status) {
        var task = findOrThrow(id);
        if (title != null && !title.isBlank()) {
            task.setTitle(title.trim());
        }
        if (status != null) {
            task.setStatus(status);
        }
        return taskRepository.save(task);
    }

    @Transactional
    public void delete(Long id) {
        var task = findOrThrow(id);
        taskRepository.delete(task);
    }

    /**
     * Starts tracking on a task. Fails if a different task is already running
     * (one active timer at a time).
     */
    @Transactional
    public Task startTracking(Long id) {
        var task = findOrThrow(id);
        if (task.getStartedAt() != null) {
            throw new InvalidTaskStateException("error.task.already_running");
        }
        if (taskRepository.existsByStartedAtIsNotNull()) {
            throw new InvalidTaskStateException("error.task.another_running");
        }
        task.setStartedAt(clock.instant());
        task.setStatus(Task.Status.DOING);
        return taskRepository.save(task);
    }

    /**
     * Stops tracking on a task and folds the elapsed time into
     * {@code trackedSeconds}.
     */
    @Transactional
    public Task stopTracking(Long id) {
        var task = findOrThrow(id);
        if (task.getStartedAt() == null) {
            throw new InvalidTaskStateException("error.task.not_running");
        }
        long elapsed = ChronoUnit.SECONDS.between(task.getStartedAt(), clock.instant());
        task.setTrackedSeconds(task.getTrackedSeconds() + Math.max(0, elapsed));
        task.setStartedAt(null);
        return taskRepository.save(task);
    }

    private Task findOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }
}
