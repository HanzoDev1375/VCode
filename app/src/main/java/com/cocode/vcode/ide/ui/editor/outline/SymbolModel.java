package com.cocode.vcode.ide.ui.editor.outline;

public class SymbolModel {
    private final String name;
    private final String details;
    private final int lineNumber;
    private final int iconResId;

    public SymbolModel(String name, String details, int lineNumber, int iconResId) {
        this.name = name;
        this.details = details;
        this.lineNumber = lineNumber;
        this.iconResId = iconResId;
    }

    public String getName() {
        return name;
    }

    public String getDetails() {
        return details;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getIconResId() {
        return iconResId;
    }
}
