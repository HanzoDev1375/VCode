package com.cocode.vcode.ide.core.editor.highlight;

import java.util.List;

/**
 * The complete output of one incremental syntax analysis pass covering lines
 * {@code [startLine, endLine]} (both inclusive).
 *
 * <p>The renderer replaces existing tokens for that range with these new ones and calls
 * {@code invalidate()} on the visible viewport. Results whose {@code contentVersion} does not
 * match the current {@link com.cocode.vcode.ide.core.editor.text.Content#getVersion()} are
 * discarded — they are stale and would paint the wrong colours.
 */
public final class HighlightResult {

    /** First line covered by this result (zero-indexed, inclusive). */
    public final int startLine;

    /** Last line covered by this result (zero-indexed, inclusive). */
    public final int endLine;

    /** All tokens produced for lines [startLine, endLine]. */
    public final List<HighlightToken> tokens;

    /**
     * The version of the {@link com.cocode.vcode.ide.core.editor.text.Content} model that was
     * analysed. The renderer discards this result if the current version has advanced.
     */
    public final long contentVersion;

    public HighlightResult(int startLine, int endLine,
                           List<HighlightToken> tokens,
                           long contentVersion) {
        this.startLine      = startLine;
        this.endLine        = endLine;
        this.tokens         = tokens;
        this.contentVersion = contentVersion;
    }
}
