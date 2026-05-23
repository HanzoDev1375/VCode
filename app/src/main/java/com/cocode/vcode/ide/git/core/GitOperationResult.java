package com.cocode.vcode.ide.git.core;

import androidx.annotation.NonNull;

/**
 * Encapsulation data wrapper mapping out outcomes generated across version control workflows.
 * Captures programmatic execution metrics status rules and carries corresponding diagnostic context feedback strings.
 */
public class GitOperationResult {

    private final Status status;
    private final String message;

    private GitOperationResult(Status status, String message) {
        this.status = status;
        this.message = message != null ? message : "";
    }

    /**
     * Static factory initializer indicating clean, problem-free task execution completions states.
     */
    public static GitOperationResult success(String message) {
        return new GitOperationResult(Status.SUCCESS, message);
    }

    /**
     * Static factory initializer capturing breakdowns, structural blocks, or missing dependencies text indicators.
     */
    public static GitOperationResult error(String message) {
        return new GitOperationResult(Status.ERROR, message);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    public boolean isError() {
        return status == Status.ERROR;
    }

    public Status getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Packages active parameters configurations into strings descriptive formats for logging layers.
     */
    @NonNull
    @Override
    public String toString() {
        return "GitOperationResult{" + status + ", " + message + "}";
    }

    /**
     * Category status metrics classifications defining version control thread outcomes signals.
     */
    public enum Status {SUCCESS, ERROR, CONFLICT}
}