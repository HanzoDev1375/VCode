package com.cocode.vcode.ide.data.model;

import androidx.annotation.NonNull;

/**
 * Functional data wrapper type mapping background task outcomes.
 * Streamlines architecture flows by clearly grouping payload data structures
 * along with descriptive exception status strings across application pipelines.
 * @param <T> The expected entity type parameter wrapped by successful process resolutions.
 */
public class Result<T> {

    private final T data;
    private final String errorMessage;
    private final boolean success;

    private Result(T data, String errorMessage, boolean success) {
        this.data = data;
        this.errorMessage = errorMessage;
        this.success = success;
    }

    /**
     * Static factory initializer indicating task completion, wrapping the resulting object data.
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(data, null, true);
    }

    /**
     * Static factory initializer capturing operational breakdowns complete with localized diagnosis text.
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(null, message != null ? message : "Unknown error", false);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getError() {
        return errorMessage;
    }

    /**
     * String conversion helper returning diagnostic data states for application tracking logs.
     */
    @NonNull
    @Override
    public String toString() {
        return success ? "Result.success(" + data + ")" : "Result.error(" + errorMessage + ")";
    }
}