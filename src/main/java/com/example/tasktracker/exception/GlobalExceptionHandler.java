package com.example.tasktracker.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain and data exceptions to RFC 7807 ProblemDetail responses.
 * Exception messages are i18n message-codes (#{...} keys) that are resolved
 * to the user's language here before being sent in the response body.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleNotFound(TaskNotFoundException ex) {
        log.info("Task not found: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                resolve(ex.getMessage(), ex.getTaskId(), LocaleContextHolder.getLocale()));
    }

    @ExceptionHandler(InvalidTaskStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleInvalidState(InvalidTaskStateException ex) {
        log.warn("Invalid task state: {}", ex.getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                resolve(ex.getMessage(), null, LocaleContextHolder.getLocale()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        // e.getDefaultMessage() may be an unresolved #{...} code (Bean Validation
        // does not auto-resolve MessageSource keys) — resolve it ourselves.
        var locale = LocaleContextHolder.getLocale();
        detail.setProperty("errors", ex.getBindingResult().getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + resolve(e.getDefaultMessage(), null, locale))
                .toList());
        return detail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleDuplicate(DataIntegrityViolationException ex) {
        // Duplicate title is the only expected integrity violation in this app.
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                resolve("error.task.duplicate", null, LocaleContextHolder.getLocale()));
    }

    /**
     * Resolves a user-facing message. The message is either a plain string or
     * a MessageSource key wrapped in {@code #{...}} (as produced by Bean
     * Validation's {@code @NotBlank(message = "#{...}")}).
     */
    private String resolve(String message, Long taskId, java.util.Locale locale) {
        if (message == null) {
            return null;
        }
        // The message may be a MessageSource key either wrapped in #{...}
        // (Bean Validation) or bare (domain exception message codes).
        var key = message.startsWith("#{") && message.endsWith("}")
                ? message.substring(2, message.length() - 1)
                : message;
        try {
            // Pass the id as a String so MessageFormat does not apply locale
            // number-grouping (e.g. 9999 -> "9.999" in German).
            return messageSource.getMessage(key,
                    taskId != null ? new Object[]{String.valueOf(taskId)} : null, locale);
        } catch (Exception e) {
            // Not a known key (e.g. a literal text message) — use as-is.
            return message;
        }
    }
}
