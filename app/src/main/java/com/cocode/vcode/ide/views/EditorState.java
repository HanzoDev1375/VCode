package com.cocode.vcode.ide.views;

public class EditorState {
    public final String text;
    public final int cursor;

    public EditorState(String text, int cursor) {
        this.text = text;
        this.cursor = cursor;
    }
}
