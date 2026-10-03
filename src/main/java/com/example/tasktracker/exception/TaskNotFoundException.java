package com.example.tasktracker.exception;

/**
 * Thrown when a task with the given id cannot be found.
 * The message is an i18n message-code; the id is carried separately
 * so the {@code GlobalExceptionHandler} can resolve it with the
 * user's locale.
 */
public class TaskNotFoundException extends RuntimeException {

    private final Long taskId;

    public TaskNotFoundException(Long taskId) {
        super("error.task.not_found");
        this.taskId = taskId;
    }

    public Long getTaskId() {
        return taskId;
    }
}
