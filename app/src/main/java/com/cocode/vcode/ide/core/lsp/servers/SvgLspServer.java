package com.cocode.vcode.ide.core.lsp.servers;

import com.cocode.vcode.ide.core.autocomplete.CompletionItem;
import com.cocode.vcode.ide.core.autocomplete.HtmlAutoCompleteEngine;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDiagnostic;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-process Language Server for SVG files.
 */
public final class SvgLspServer implements LspServer {

    private volatile boolean ready = false;
    private ProjectIndex projectIndex;

    private final HtmlAutoCompleteEngine completeEngine = new HtmlAutoCompleteEngine(null);

    @Override
    public void initialize(ProjectIndex index) {
        this.projectIndex = index;
        this.ready = true;
    }

    @Override
    public void shutdown() {
        this.ready = false;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLanguageId() {
        return "svg";
    }

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) {
            return Collections.emptyList();
        }

        int flatOffset = doc.toOffset(pos);
        if (flatOffset < 0) {
            flatOffset = doc.text.length();
        }

        if (doc.uri != null) {
            File file = new File(doc.uri);
            completeEngine.setCurrentFile(file);
        }

        List<CompletionItem> legacy = completeEngine.getSuggestions(doc.text, flatOffset);
        return convertCompletions(legacy);
    }

    @Override
    public List<LspDiagnostic> diagnostics(LspDocument doc) {
        return Collections.emptyList();
    }

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        return null;
    }

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        return Collections.emptyList();
    }

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return null;
    }

    private static List<LspCompletionItem> convertCompletions(List<CompletionItem> legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return Collections.emptyList();
        }
        List<LspCompletionItem> result = new ArrayList<>(legacy.size());
        for (CompletionItem ci : legacy) {
            if (ci == null) {
                continue;
            }
            int kind = mapKind(ci.getType());
            result.add(new LspCompletionItem(
                    ci.getLabel(),
                    ci.getEffectiveInsertText(),
                    kind,
                    ci.getDetail(),
                    null
            ));
        }
        return result;
    }

    private static int mapKind(CompletionItem.Type type) {
        if (type == null) {
            return LspCompletionItem.KIND_TEXT;
        }
        switch (type) {
            case TAG:
                return LspCompletionItem.KIND_CLASS;
            case ATTRIBUTE:
                return LspCompletionItem.KIND_PROPERTY;
            case VALUE:
                return LspCompletionItem.KIND_VALUE;
            case SNIPPET:
                return LspCompletionItem.KIND_SNIPPET;
            case KEYWORD:
                return LspCompletionItem.KIND_KEYWORD;
            case FILE:
                return LspCompletionItem.KIND_FILE;
            case FOLDER:
                return LspCompletionItem.KIND_FOLDER;
            default:
                return LspCompletionItem.KIND_TEXT;
        }
    }
}
