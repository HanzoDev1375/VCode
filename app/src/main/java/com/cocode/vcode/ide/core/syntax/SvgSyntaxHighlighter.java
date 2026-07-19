package com.cocode.vcode.ide.core.syntax;

import android.content.Context;
import com.cocode.vcode.ide.core.editor.highlight.HighlightToken;
import com.cocode.vcode.ide.core.editor.text.ContentLine;

import java.util.List;

/**
 * SVG files are XML-based, so we reuse HtmlSyntaxHighlighter's tokenizer
 * which correctly handles tags, attributes, values and comments.
 */
public class SvgSyntaxHighlighter extends HtmlSyntaxHighlighter {
    public SvgSyntaxHighlighter(Context context) {
        super(context);
    }
}
