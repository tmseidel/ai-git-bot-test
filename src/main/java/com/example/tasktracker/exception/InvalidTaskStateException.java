package com.example.tasktracker.exception;

/**
 * Thrown when a state-machine transition is not allowed
 * (e.g. starting a task that is already running, or stopping
 * a task that is not running).
 * The message is an i18n message-code resolved by the
 * {@code GlobalExceptionHandler}.
 */
public class InvalidTaskStateException extends RuntimeException {

    public InvalidTaskStateException(String messageCode) {
        super(messageCode);
    }
}
