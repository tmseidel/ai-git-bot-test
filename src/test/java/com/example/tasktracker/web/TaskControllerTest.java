package com.example.tasktracker.web;

import com.example.tasktracker.domain.Task;
import com.example.tasktracker.exception.InvalidTaskStateException;
import com.example.tasktracker.exception.TaskNotFoundException;
import com.example.tasktracker.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer test: verifies routing, HTTP status codes, JSON payloads and
 * PRG redirects without starting the full Spring context.
 * (Full-context behavior — i18n, templates — is covered by the
 * {@code @SpringBootTest} smoke test.)
 */
@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    private MockMvc mockMvc;

    @Mock
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new TaskController(taskService))
                .setControllerAdvice(new com.example.tasktracker.exception.GlobalExceptionHandler(
                        messageSourceStub()))
                .build();
    }

    private org.springframework.context.MessageSource messageSourceStub() {
        var source = new org.springframework.context.support.StaticMessageSource();
        source.addMessage("error.task.not_found",
                java.util.Locale.ENGLISH, "Task with id {0} was not found.");
        source.addMessage("error.task.already_running",
                java.util.Locale.ENGLISH, "This task is already being tracked.");
        source.addMessage("error.task.another_running",
                java.util.Locale.ENGLISH, "Another task is already being tracked.");
        source.addMessage("error.task.not_running",
                java.util.Locale.ENGLISH, "This task is not being tracked.");
        source.addMessage("validation.task.title.notBlank",
                java.util.Locale.ENGLISH, "Title is required");
        return source;
    }

    @Test
    @DisplayName("GET / renders the task list template")
    void index_rendersList() throws Exception {
        when(taskService.list()).thenReturn(List.of(
                task("A", 1L, null, 3600),
                task("B", 2L, Instant.parse("2026-01-01T11:00:00Z"), 60)));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .view().name("tasks/list"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .model().attribute("totalTrackedSeconds", 3660L));
    }

    @Test
    @DisplayName("POST /tasks: creates and redirects to / (PRG)")
    void create_redirects() throws Exception {
        when(taskService.create("My task")).thenReturn(task("My task", 1L, null, 0));

        mockMvc.perform(post("/tasks").param("title", "My task"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(taskService).create("My task");
    }

    @Test
    @DisplayName("POST /tasks with blank title: 400 with ProblemDetail")
    void create_blankTitle_400() throws Exception {
        mockMvc.perform(post("/tasks").param("title", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0]", org.hamcrest.Matchers.containsString("title")));
    }

    @Test
    @DisplayName("POST /api/tasks/{id}/start: 200 with TaskResponse JSON")
    void start_returnsJson() throws Exception {
        when(taskService.startTracking(1L))
                .thenReturn(task("A", 1L, Instant.parse("2026-01-01T12:00:00Z"), 0));

        mockMvc.perform(post("/api/tasks/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("A"))
                .andExpect(jsonPath("$.status").value("DOING"))
                .andExpect(jsonPath("$.running").value(true));
    }

    @Test
    @DisplayName("POST /api/tasks/{id}/start: 404 when task missing")
    void start_notFound_404() throws Exception {
        when(taskService.startTracking(99L))
                .thenThrow(new TaskNotFoundException(99L));

        mockMvc.perform(post("/api/tasks/99/start"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail",
                        org.hamcrest.Matchers.containsString("99")));
    }

    @Test
    @DisplayName("POST /api/tasks/{id}/start: 409 when already running")
    void start_alreadyRunning_409() throws Exception {
        when(taskService.startTracking(1L))
                .thenThrow(new InvalidTaskStateException("error.task.already_running"));

        mockMvc.perform(post("/api/tasks/1/start"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").isString());
    }

    @Test
    @DisplayName("POST /api/tasks/{id}/stop: 200 with updated TaskResponse")
    void stop_returnsJson() throws Exception {
        when(taskService.stopTracking(1L))
                .thenReturn(task("A", 1L, null, 125));

        mockMvc.perform(post("/api/tasks/1/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackedSeconds").value(125))
                .andExpect(jsonPath("$.running").value(false));
    }

    @Test
    @DisplayName("POST /api/tasks/{id}/stop: 409 when not running")
    void stop_notRunning_409() throws Exception {
        when(taskService.stopTracking(1L))
                .thenThrow(new InvalidTaskStateException("error.task.not_running"));

        mockMvc.perform(post("/api/tasks/1/stop"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PATCH /tasks/{id}: updates and redirects")
    void update_redirects() throws Exception {
        when(taskService.update(anyLong(), any(), any())).thenAnswer(inv ->
                task("A", inv.getArgument(0, Long.class), null, 0));

        mockMvc.perform(patch("/tasks/1").param("status", "DONE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(taskService).update(eq(1L), isNull(), eq(Task.Status.DONE));
    }

    @Test
    @DisplayName("POST /tasks/{id}/delete: deletes and redirects")
    void delete_redirects() throws Exception {
        mockMvc.perform(post("/tasks/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(taskService).delete(1L);
    }

    private Task task(String title, long id, Instant startedAt, long trackedSeconds) {
        return Task.builder()
                .id(id)
                .title(title)
                .status(Task.Status.DOING)
                .trackedSeconds(trackedSeconds)
                .startedAt(startedAt)
                .createdAt(Instant.parse("2026-01-01T09:00:00Z"))
                .build();
    }
}
