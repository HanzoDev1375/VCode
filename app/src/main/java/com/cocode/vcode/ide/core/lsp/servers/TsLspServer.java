package com.cocode.vcode.ide.core.lsp.servers;

import android.content.Context;

import com.cocode.vcode.ide.core.autocomplete.CompletionItem;
import com.cocode.vcode.ide.core.autocomplete.TsAutoCompleteEngine;
import com.cocode.vcode.ide.core.diagnostic.linters.TsLinter;
import com.cocode.vcode.ide.core.lsp.LspCompletionItem;
import com.cocode.vcode.ide.core.lsp.LspDiagnostic;
import com.cocode.vcode.ide.core.lsp.LspDocument;
import com.cocode.vcode.ide.core.lsp.LspLocation;
import com.cocode.vcode.ide.core.lsp.LspPosition;
import com.cocode.vcode.ide.core.lsp.LspRange;
import com.cocode.vcode.ide.core.lsp.LspServer;
import com.cocode.vcode.ide.core.lsp.LspSignatureHelp;
import com.cocode.vcode.ide.core.lsp.ProjectIndex;
import com.cocode.vcode.ide.data.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-process Language Server for TypeScript files.
 *
 * Extends the JavaScript server's capability set with TS-specific linting
 * (via {@link TsLinter}) and TypeScript-aware completions (via {@link TsAutoCompleteEngine}).
 * Module resolution also tries {@code .ts} and {@code .tsx} extensions.
 */
public final class TsLspServer implements LspServer {

    private static final Pattern IMPORT_FROM =
            Pattern.compile("import\\s+.*?from\\s+['\"]([^'\"]+)['\"]");

    private volatile boolean ready = false;
    private ProjectIndex projectIndex;

    private final TsAutoCompleteEngine autoCompleteEngine;

    public TsLspServer(Context context) {
        this.autoCompleteEngine = new TsAutoCompleteEngine(context);
    }

    public TsLspServer() {
        this(null);
    }

    // -------------------------------------------------------------------------
    // LspServer contract
    // -------------------------------------------------------------------------

    @Override
    public void initialize(ProjectIndex index) {
        this.projectIndex = index;
        ready = true;
    }

    @Override
    public void shutdown() {
        ready = false;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public String getLanguageId() {
        return "typescript";
    }

    // -------------------------------------------------------------------------
    // Completions
    // -------------------------------------------------------------------------

    @Override
    public List<LspCompletionItem> completion(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null) return Collections.emptyList();
        int offset = doc.toOffset(pos);
        if (offset < 0) offset = doc.text.length();

        autoCompleteEngine.setCurrentFile(new File(doc.uri));
        List<CompletionItem> suggestions = autoCompleteEngine.getSuggestions(doc.text, offset);
        if (suggestions == null) return Collections.emptyList();

        List<LspCompletionItem> result = new ArrayList<>(suggestions.size());
        for (CompletionItem item : suggestions) {
            String insert = item.getEffectiveInsertText();
            int curOffset = item.getCursorOffset();
            if (curOffset < 0) {
                int pipeIdx = insert.length() + curOffset;
                if (pipeIdx >= 0 && pipeIdx <= insert.length()) {
                    insert = insert.substring(0, pipeIdx) + "|" + insert.substring(pipeIdx);
                }
            }
            result.add(new LspCompletionItem(
                    item.getLabel(),
                    insert,
                    mapKind(item.getType()),
                    item.getDetail(),
                    null
            ));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    @Override
    public List<LspDiagnostic> diagnostics(LspDocument doc) {
        if (doc == null || doc.text == null || doc.text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        File file = new File(doc.uri);
        List<Problem> problems = TsLinter.analyze(file, doc.text);
        if (problems == null) return Collections.emptyList();

        List<LspDiagnostic> diagnostics = new ArrayList<>(problems.size());
        for (Problem p : problems) {
            int startLine = Math.max(0, p.getLine() - 1);
            int startChar = Math.max(0, p.getColumn());
            int endChar   = startChar + Math.max(1, p.getLength());
            diagnostics.add(new LspDiagnostic(
                    new LspRange(startLine, startChar, startLine, endChar),
                    mapSeverity(p.getSeverity()),
                    p.getMessage(),
                    null,
                    "typescript"
            ));
        }
        return diagnostics;
    }

    // -------------------------------------------------------------------------
    // Go to Definition
    // -------------------------------------------------------------------------

    @Override
    public LspLocation definition(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return null;

        String lineText = doc.getLine(pos.line);
        if (lineText != null) {
            Matcher m = IMPORT_FROM.matcher(lineText);
            while (m.find()) {
                if (pos.character >= m.start() && pos.character <= m.end()) {
                    String importPath = m.group(1);
                    LspLocation resolved = resolveModulePath(doc.uri, importPath);
                    if (resolved != null) return resolved;
                }
            }
        }

        int offset = doc.toOffset(pos);
        String word = extractWord(doc.text, offset >= 0 ? offset : 0);
        if (word.isEmpty()) return null;

        List<LspLocation> defs = ProjectIndex.getInstance().findDefinitions(word);
        return (defs != null && !defs.isEmpty()) ? defs.get(0) : null;
    }

    // -------------------------------------------------------------------------
    // Find References
    // -------------------------------------------------------------------------

    @Override
    public List<LspLocation> references(LspDocument doc, LspPosition pos) {
        if (doc == null || doc.text == null || pos == null) return Collections.emptyList();
        int offset = doc.toOffset(pos);
        String word = extractWord(doc.text, offset >= 0 ? offset : 0);
        if (word.isEmpty()) return Collections.emptyList();

        List<LspLocation> locs = ProjectIndex.getInstance().findDefinitions(word);
        return locs != null ? locs : Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // Signature Help
    // -------------------------------------------------------------------------

    @Override
    public LspSignatureHelp signatureHelp(LspDocument doc, LspPosition pos) {
        return null;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static LspLocation resolveModulePath(String docUri, String importPath) {
        File base = new File(docUri).getParentFile();
        if (base == null) return null;
        File target = new File(base, importPath);
        if (target.exists() && target.isFile()) {
            return new LspLocation(target.getAbsolutePath(), new LspRange(0, 0, 0, 0));
        }
        for (String ext : new String[]{".ts", ".tsx", ".js", ".mjs", ".cjs"}) {
            File withExt = new File(base, importPath + ext);
            if (withExt.exists()) {
                return new LspLocation(withExt.getAbsolutePath(), new LspRange(0, 0, 0, 0));
            }
        }
        return null;
    }

    private static String extractWord(String text, int offset) {
        if (text == null || offset < 0 || offset > text.length()) return "";
        int start = Math.min(offset, text.length() - 1);
        while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
        int end = offset;
        while (end < text.length() && isWordChar(text.charAt(end))) end++;
        return start < end ? text.substring(start, end) : "";
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    private static int mapKind(CompletionItem.Type type) {
        if (type == null) return LspCompletionItem.KIND_TEXT;
        switch (type) {
            case FUNCTION:
            case BUILTIN:  return LspCompletionItem.KIND_FUNCTION;
            case KEYWORD:  return LspCompletionItem.KIND_KEYWORD;
            case SNIPPET:  return LspCompletionItem.KIND_SNIPPET;
            case VALUE:    return LspCompletionItem.KIND_VALUE;
            case FILE:     return LspCompletionItem.KIND_FILE;
            case FOLDER:   return LspCompletionItem.KIND_FOLDER;
            default:       return LspCompletionItem.KIND_TEXT;
        }
    }

    private static int mapSeverity(Problem.Severity severity) {
        if (severity == null) return LspDiagnostic.SEVERITY_INFORMATION;
        switch (severity) {
            case ERROR:   return LspDiagnostic.SEVERITY_ERROR;
            case WARNING: return LspDiagnostic.SEVERITY_WARNING;
            default:      return LspDiagnostic.SEVERITY_INFORMATION;
        }
    }
}
