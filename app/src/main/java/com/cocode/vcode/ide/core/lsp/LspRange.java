package com.cocode.vcode.ide.core.lsp;

/**
 * A range within a text document, defined by a start and end {@link LspPosition}.
 * Matches the LSP specification's {@code Range} type.
 */
public final class LspRange {

    public final LspPosition start;
    public final LspPosition end;

    public LspRange(LspPosition start, LspPosition end) {
        this.start = start;
        this.end = end;
    }

    public LspRange(int startLine, int startChar, int endLine, int endChar) {
        this.start = new LspPosition(startLine, startChar);
        this.end = new LspPosition(endLine, endChar);
    }
}
