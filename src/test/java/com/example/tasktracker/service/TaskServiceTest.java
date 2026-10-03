package com.example.tasktracker.service;

import com.example.tasktracker.domain.Task;
import com.example.tasktracker.exception.InvalidTaskStateException;
import com.example.tasktracker.exception.TaskNotFoundException;
import com.example.tasktracker.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    private Clock clock;
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        taskService = new TaskService(taskRepository, clock);
    }

    // ── create ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create: persists a TODO task with zero tracked time")
    void create_persistsTodoTask() {
        when(taskRepository.save(any(Task.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Task saved = taskService.create("Write tests");

        assertThat(saved.getTitle()).isEqualTo("Write tests");
        assertThat(saved.getStatus()).isEqualTo(Task.Status.TODO);
        assertThat(saved.getTrackedSeconds()).isZero();
        assertThat(saved.getStartedAt()).isNull();
    }

    // ── list ordering ───────────────────────────────────────────────

    @Test
    @DisplayName("list: running task comes first, others by newest creation")
    void list_ordersRunningFirst() {
        Task running = task("Running", 1L, Instant.parse("2026-01-01T11:00:00Z"),
                Instant.parse("2026-01-01T10:00:00Z"));
        Task older = task("Older", 2L, null,
                Instant.parse("2025-12-30T10:00:00Z"));
        Task newer = task("Newer", 3L, null,
                Instant.parse("2025-12-31T10:00:00Z"));
        when(taskRepository.findAll()).thenReturn(List.of(older, newer, running));

        List<Task> result = taskService.list();

        assertThat(result).extracting(Task::getId)
                .containsExactly(1L, 3L, 2L);
    }

    // ── startTracking ───────────────────────────────────────────────

    @Test
    @DisplayName("startTracking: stamps start time and sets DOING")
    void startTracking_stampsStart() {
        Task task = task("A", 1L, null, Instant.parse("2026-01-01T09:00:00Z"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.existsByStartedAtIsNotNull()).thenReturn(false);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task result = taskService.startTracking(1L);

        assertThat(result.getStartedAt()).isEqualTo(clock.instant());
        assertThat(result.getStatus()).isEqualTo(Task.Status.DOING);
    }

    @Test
    @DisplayName("startTracking: rejects an already-running task")
    void startTracking_rejectsRunning() {
        Task task = task("A", 1L, Instant.parse("2026-01-01T11:00:00Z"),
                Instant.parse("2026-01-01T09:00:00Z"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.startTracking(1L))
                .isInstanceOf(InvalidTaskStateException.class)
                .hasMessage("error.task.already_running");

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("startTracking: rejects when another task is running")
    void startTracking_rejectsWhenAnotherRunning() {
        Task task = task("A", 1L, null, Instant.parse("2026-01-01T09:00:00Z"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.existsByStartedAtIsNotNull()).thenReturn(true);

        assertThatThrownBy(() -> taskService.startTracking(1L))
                .isInstanceOf(InvalidTaskStateException.class)
                .hasMessage("error.task.another_running");

        verify(taskRepository, never()).save(any());
    }

    // ── stopTracking ────────────────────────────────────────────────

    @Test
    @DisplayName("stopTracking: folds elapsed seconds into trackedSeconds")
    void stopTracking_foldsElapsed() {
        Task task = task("A", 1L, Instant.parse("2026-01-01T11:30:00Z"),
                Instant.parse("2026-01-01T09:00:00Z"));
        task.setTrackedSeconds(60);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task result = taskService.stopTracking(1L);

        // 11:30:00 → 12:00:00 = 1800s, plus previous 60s.
        assertThat(result.getTrackedSeconds()).isEqualTo(1860L);
        assertThat(result.getStartedAt()).isNull();
    }

    @Test
    @DisplayName("stopTracking: rejects a task that is not running")
    void stopTracking_rejectsNotRunning() {
        Task task = task("A", 1L, null, Instant.parse("2026-01-01T09:00:00Z"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskService.stopTracking(1L))
                .isInstanceOf(InvalidTaskStateException.class)
                .hasMessage("error.task.not_running");

        verify(taskRepository, never()).save(any());
    }

    // ── not found ───────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(longs = {1L, 42L, 999999L})
    @DisplayName("unknown id: throws TaskNotFoundException carrying the id")
    void unknownId_throwsNotFound(long id) {
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.startTracking(id))
                .isInstanceOf(TaskNotFoundException.class)
                .satisfies(ex ->
                        assertThat(((TaskNotFoundException) ex).getTaskId()).isEqualTo(id));
    }

    @Test
    @DisplayName("unknown id on stop: throws TaskNotFoundException")
    void stopTracking_unknownId_throwsNotFound() {
        when(taskRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.stopTracking(5L))
                .isInstanceOf(TaskNotFoundException.class)
                .satisfies(ex ->
                        assertThat(((TaskNotFoundException) ex).getTaskId()).isEqualTo(5L));
    }

    // ── update / delete ─────────────────────────────────────────────

    @Test
    @DisplayName("update: changes title and status, persists the task")
    void update_changesFields() {
        Task task = task("Old title", 1L, null, Instant.parse("2026-01-01T09:00:00Z"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        when(taskRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        taskService.update(1L, "New title", Task.Status.DONE);

        assertThat(captor.getValue().getTitle()).isEqualTo("New title");
        assertThat(captor.getValue().getStatus()).isEqualTo(Task.Status.DONE);
    }

    @Test
    @DisplayName("update: blank title is ignored, null status keeps current")
    void update_ignoresBlankTitleAndNullStatus() {
        Task task = task("Keep me", 1L, null, Instant.parse("2026-01-01T09:00:00Z"));
        task.setStatus(Task.Status.DOING);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task result = taskService.update(1L, "   ", null);

        assertThat(result.getTitle()).isEqualTo("Keep me");
        assertThat(result.getStatus()).isEqualTo(Task.Status.DOING);
    }

    @Test
    @DisplayName("delete: removes the task")
    void delete_removesTask() {
        Task task = task("A", 1L, null, Instant.parse("2026-01-01T09:00:00Z"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        taskService.delete(1L);

        verify(taskRepository).delete(task);
    }

    // ── helpers ─────────────────────────────────────────────────────

    private Task task(String title, long id, Instant startedAt, Instant createdAt) {
        return Task.builder()
                .id(id)
                .title(title)
                .status(Task.Status.TODO)
                .trackedSeconds(0)
                .startedAt(startedAt)
                .createdAt(createdAt)
                .build();
    }
}
