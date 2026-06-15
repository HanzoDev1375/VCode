package com.cocode.vcode.ide.core.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FoldingEngine {

    public static class FoldRegion {
        public int startLine;
        public int endLine;
        public int startOffset;
        public int endOffset;
        public boolean isFolded;

        public FoldRegion(int startLine, int endLine, int startOffset, int endOffset) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.isFolded = false;
        }
    }

    public static List<FoldRegion> detectFoldRegions(String code, android.text.Layout layout) {
        List<FoldRegion> regions = new ArrayList<>();
        if (code == null || code.isEmpty() || layout == null) return regions;

        // Basic brace block detection
        Pattern p = Pattern.compile("\\{([^}]*)\\}");
        Matcher m = p.matcher(code);
        while (m.find()) {
            int startLine = layout.getLineForOffset(m.start());
            int endLine = layout.getLineForOffset(m.end());
            if (endLine > startLine) {
                regions.add(new FoldRegion(startLine, endLine, m.start(), m.end()));
            }
        }
        return regions;
    }
}
