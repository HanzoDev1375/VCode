package com.cocode.vcode.ide.data.model;

import java.io.File;

public class Problem {
    private final File file;
    private final int line;
    private final int column;
    private final String message;
    private final Severity severity;

    public enum Severity {
        ERROR, WARNING, INFO
    }

    public Problem(File file, int line, int column, String message, Severity severity) {
        this.file = file;
        this.line = line;
        this.column = column;
        this.message = message;
        this.severity = severity;
    }

    public File getFile() {
        return file;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getMessage() {
        return message;
    }

    public Severity getSeverity() {
        return severity;
    }
}
