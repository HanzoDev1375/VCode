package com.cocode.vcode.ide.views;

public class DirtyRangeTracker {
    public int start = -1;
    public int end = -1;

    public void addEdit(int editStart, int beforeLength, int afterLength) {
        if (start == -1) {
            start = editStart;
            end = editStart + afterLength;
        } else {
            int diff = afterLength - beforeLength;
            if (editStart <= end) {
                end += diff;
            }
            if (editStart < start) {
                start = editStart;
            }
            if (editStart + afterLength > end) {
                end = editStart + afterLength;
            }
        }
    }

    public void reset() {
        start = -1;
        end = -1;
    }

    public boolean isDirty() {
        return start != -1;
    }
}
