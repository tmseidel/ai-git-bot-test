package com.example.tasktracker.web;

import com.example.tasktracker.domain.Task;
import com.example.tasktracker.dto.TaskResponse;
import com.example.tasktracker.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;


/**
 * Server-rendered pages (Thymeleaf) plus small JSON endpoints for the
 * start/stop timer buttons (called via fetch from the page).
 */
@Controller
@RequiredArgsConstructor
@RequestMapping
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/")
    public String index(Model model) {
        var tasks = taskService.list();
        model.addAttribute("tasks", tasks);
        model.addAttribute("totalTrackedSeconds",
                tasks.stream().mapToLong(Task::getTrackedSeconds).sum());
        var fmt = java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                .withZone(java.time.ZoneOffset.UTC);
        // Map task id -> ISO-8601 start time (UTC) for the client-side live timer.
        var startedIsos = new java.util.HashMap<Long, String>();
        for (Task t : tasks) {
            if (t.getStartedAt() != null) {
                startedIsos.put(t.getId(), fmt.format(t.getStartedAt()));
            }
        }
        model.addAttribute("startedIsos", startedIsos);
        // The list is ordered running-first, so the (single) running task is tasks[0].
        model.addAttribute("running",
                !tasks.isEmpty() && tasks.get(0).getStartedAt() != null);
        return "tasks/list";
    }

    /** New-task form bound as a record (validation messages are i18n keys). */
    public record NewTaskForm(
            @NotBlank(message = "validation.task.title.notBlank") String title) {}

    @PostMapping("/tasks")
    public String create(@Valid NewTaskForm form, jakarta.servlet.http.HttpServletResponse response)
            throws java.io.IOException {
        taskService.create(form.title().trim());
        response.sendRedirect("/");
        return null;
    }

    /** JSON endpoint for the start button (called via fetch). */
    @PostMapping(value = "/api/tasks/{id}/start", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public TaskResponse start(@PathVariable Long id) {
        return TaskResponse.from(taskService.startTracking(id));
    }

    /** JSON endpoint for the stop button (called via fetch). */
    @PostMapping(value = "/api/tasks/{id}/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public TaskResponse stop(@PathVariable Long id) {
        return TaskResponse.from(taskService.stopTracking(id));
    }

    @PatchMapping("/tasks/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam(value = "title", required = false) String title,
                         @RequestParam(value = "status", required = false) Task.Status status,
                         jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        taskService.update(id, title, status);
        response.sendRedirect("/");
        return null;
    }

    @PostMapping("/tasks/{id}/delete")
    public String delete(@PathVariable Long id,
                         jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        taskService.delete(id);
        response.sendRedirect("/");
        return null;
    }
}
