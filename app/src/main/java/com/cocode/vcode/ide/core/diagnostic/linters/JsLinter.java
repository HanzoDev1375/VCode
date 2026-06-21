package com.cocode.vcode.ide.core.diagnostic.linters;

import com.cocode.vcode.ide.core.diagnostic.util.KnownElements;
import com.cocode.vcode.ide.core.diagnostic.util.LinterUtils;
import com.cocode.vcode.ide.core.diagnostic.util.TokenMask;
import com.cocode.vcode.ide.data.model.Problem;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsLinter {

    // ── Patterns compiled once ──────────────────────────────────────────────
    private static final Pattern PAT_VAR = Pattern.compile("\\bvar\\s+([a-zA-Z_$][\\w$]*)");
    private static final Pattern PAT_CONSOLE = Pattern.compile("\\bconsole\\.(\\w+)");
    private static final Pattern PAT_DEBUGGER = Pattern.compile("\\bdebugger\\s*;?");
    private static final Pattern PAT_LOOSE_EQ = Pattern.compile("(?<![=!<>])==(?!=)");
    private static final Pattern PAT_LOOSE_NEQ = Pattern.compile("!=(?!=)");
    private static final Pattern PAT_EVAL = Pattern.compile("\\beval\\s*\\(");
    private static final Pattern PAT_WITH = Pattern.compile("\\bwith\\s*\\(");
    private static final Pattern PAT_NAN_CMP = Pattern.compile("===\\s*NaN|NaN\\s*===|==\\s*NaN|NaN\\s*==");
    private static final Pattern PAT_EMPTY_CATCH = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*\\}");
    private static final Pattern PAT_INF_LOOP = Pattern.compile("\\bwhile\\s*\\(\\s*true\\s*\\)|\\bfor\\s*\\(\\s*;\\s*;\\s*\\)");
    private static final Pattern PAT_SWITCH = Pattern.compile("\\bswitch\\s*\\(");
    private static final Pattern PAT_THEN = Pattern.compile("\\.then\\s*\\(");
    private static final Pattern PAT_CATCH_CHAIN = Pattern.compile("\\.catch\\s*\\(");
    private static final Pattern PAT_AWAIT_CALL = Pattern.compile("\\b(fetch|(?:\\w+\\.)?(?:json|text|arrayBuffer|blob|formData))\\s*\\(");
    private static final Pattern PAT_ASYNC_FN = Pattern.compile("\\basync\\s+function\\s*(\\w*)|\\basync\\s*\\(|\\basync\\s+([a-zA-Z_$][\\w$]*)\\s*=>");
    private static final Pattern PAT_TYPEOF_CMP = Pattern.compile("\\btypeof\\b[^=]*===?\\s*(undefined|null|NaN|true|false|\\d+)");
    private static final Pattern PAT_TODO = Pattern.compile("(?://|/\\*).*?(TODO|FIXME)([^\n]*)");
    private static final Pattern PAT_ARROW_SIMP = Pattern.compile("function\\s*(\\w*)\\s*\\([^)]*\\)\\s*\\{\\s*return\\s+[^;{]+;\\s*\\}");
    private static final Pattern PAT_STR_CONCAT = Pattern.compile("\"[^\"]*\"\\s*\\+\\s*\\w+|\\w+\\s*\\+\\s*\"[^\"]*\"|'[^']*'\\s*\\+\\s*\\w+|\\w+\\s*\\+\\s*'[^']*'");
    private static final Pattern PAT_OPT_CHAIN = Pattern.compile("(\\w+)\\s*&&\\s*\\1\\.(\\w+)");
    private static final Pattern PAT_NULLISH = Pattern.compile("\\|\\|\\s*(null|undefined|''|\"\"|0)\\b");
    private static final Pattern PAT_DIV_ZERO = Pattern.compile("([^/])\\s*/\\s*0\\b");
    private static final Pattern PAT_RETURN = Pattern.compile("\\breturn\\b");
    private static final Pattern PAT_THROW = Pattern.compile("\\bthrow\\b");
    private static final Pattern PAT_BREAK = Pattern.compile("\\bbreak\\b");
    private static final Pattern PAT_CONTINUE = Pattern.compile("\\bcontinue\\b");
    private static final Pattern PAT_FUNC_DECL = Pattern.compile("\\bfunction\\s+(\\w+)\\s*\\(([^)]*)\\)");
    private static final Pattern PAT_ARROW_DECL = Pattern.compile("(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?(?:\\(([^)]*)\\)|\\w+)\\s*=>");
    private static final Pattern PAT_LET_CONST = Pattern.compile("\\b(let|const)\\s+([a-zA-Z_$][\\w$]*)");
    private static final Pattern PAT_CONST_INIT = Pattern.compile("\\bconst\\s+([a-zA-Z_$][\\w$]*)\\s*(?!\\s*=)(?=[;,\\n])");
    private static final Pattern PAT_STR_CONCAT_LOOP = Pattern.compile("\\w+\\s*\\+=\\s*['\"`]|['\"`][^'\"`]*['\"`]\\s*\\+");
    private static final Pattern PAT_UNREACHABLE = Pattern.compile("\\b(return|throw|break|continue)\\b\\s*[^;{/\n]*;?");
    private static final Pattern PAT_AWAIT_MISSING = Pattern.compile("(?<!await\\s)\\bfetch\\s*\\(|(?<!\\.)(json|text|arrayBuffer|blob|formData)\\s*\\(");

    // ── Entry point ─────────────────────────────────────────────────────────
    public static List<Problem> analyze(File file, String text) {
        if (text == null || text.trim().isEmpty()) return java.util.Collections.emptyList();

        List<Problem> problems = new ArrayList<>();
        TokenMask mask = TokenMask.build(text, "js");
        String[] lines = LinterUtils.splitLines(text);

        checkVarUsage(file, text, lines, mask, problems);
        checkConsole(file, text, lines, mask, problems);
        checkDebugger(file, text, lines, mask, problems);
        checkLooseEquality(file, text, lines, mask, problems);
        checkEval(file, text, lines, mask, problems);
        checkWith(file, text, lines, mask, problems);
        checkNaNComparison(file, text, lines, mask, problems);
        checkEmptyCatch(file, text, lines, mask, problems);
        checkInfiniteLoop(file, text, lines, mask, problems);
        checkSwitchDefault(file, text, lines, mask, problems);
        checkPromiseChain(file, text, lines, mask, problems);
        checkMissingAwait(file, text, lines, mask, problems);
        checkAsyncNoAwait(file, text, lines, mask, problems);
        checkTypeofComparison(file, text, lines, mask, problems);
        checkTodoFixme(file, text, lines, mask, problems);
        checkFunctionParams(file, text, lines, mask, problems);
        checkDivisionByZero(file, text, lines, mask, problems);
        checkUnreachableCode(file, text, lines, mask, problems);
        checkConstReassign(file, text, lines, mask, problems);
        checkUnclosedString(file, text, lines, mask, problems);
        checkReturnOutsideFunction(file, text, lines, mask, problems);
        checkBreakContinue(file, text, lines, mask, problems);
        checkUnusedVars(file, text, lines, mask, problems);
        checkArrowSimplification(file, text, lines, mask, problems);
        checkStringConcat(file, text, lines, mask, problems);
        checkOptionalChaining(file, text, lines, mask, problems);
        checkNullishCoalescing(file, text, lines, mask, problems);
        checkStringConcatInLoop(file, text, lines, mask, problems);

        return problems;
    }


    // ── Rule implementations ────────────────────────────────────────────────

    private static void checkVarUsage(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_VAR.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 3,
                    "'var' is function-scoped: prefer 'const' for values that don't change, or 'let'",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkConsole(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_CONSOLE.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "Debug statement 'console." + m.group(1) + "(...)' left in code: remove before production",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkDebugger(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_DEBUGGER.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 8,
                    "'debugger' statement must be removed before production",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkLooseEquality(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_LOOSE_EQ.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 2,
                    "Loose equality '==': use '===' to avoid type coercion bugs",
                    Problem.Severity.WARNING));
        }
        Matcher m2 = PAT_LOOSE_NEQ.matcher(text);
        while (m2.find()) {
            if (mask.isMasked(m2.start())) continue;
            // ensure it's not !==
            if (m2.start() + 2 < text.length() && text.charAt(m2.start() + 2) == '=') continue;
            int line = LinterUtils.getLine(text, m2.start());
            int col = LinterUtils.getColumn(text, m2.start());
            out.add(new Problem(file, line, col, 2,
                    "Loose inequality '!=': use '!==' to avoid type coercion bugs",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkEval(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_EVAL.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 4,
                    "'eval()' is a security risk and performance bottleneck: avoid in production",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkWith(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_WITH.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 4,
                    "'with' statement is deprecated and disallowed in strict mode",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkNaNComparison(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_NAN_CMP.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "Direct comparison to 'NaN' is always false: use 'Number.isNaN()' or 'isNaN()'",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkEmptyCatch(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_EMPTY_CATCH.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 5,
                    "Empty 'catch' block: errors are silently swallowed — add handling or logging",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkInfiniteLoop(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_INF_LOOP.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            // find closing brace of this loop body and check for break/return
            int braceStart = text.indexOf('{', m.end());
            if (braceStart < 0) continue;
            int depth = 0, end = braceStart;
            for (int i = braceStart; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            String body = text.substring(braceStart, end);
            if (!body.contains("break") && !body.contains("return")) {
                int line = LinterUtils.getLine(text, m.start());
                int col = LinterUtils.getColumn(text, m.start());
                out.add(new Problem(file, line, col, m.group().length(),
                        "Potential infinite loop: 'while(true)' has no visible 'break' or 'return'",
                        Problem.Severity.WARNING));
            }
        }
    }

    private static void checkSwitchDefault(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_SWITCH.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int braceStart = text.indexOf('{', m.end());
            if (braceStart < 0) continue;
            int depth = 0, end = braceStart;
            for (int i = braceStart; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            String body = text.substring(braceStart, end);
            if (!body.contains("default:") && !body.contains("default :")) {
                int line = LinterUtils.getLine(text, m.start());
                int col = LinterUtils.getColumn(text, m.start());
                out.add(new Problem(file, line, col, 6,
                        "'switch' is missing a 'default' case: unhandled values will silently pass through",
                        Problem.Severity.WARNING));
            }
        }
    }


    private static void checkPromiseChain(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_THEN.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            // look for .catch( within 200 chars after this .then(
            int searchEnd = Math.min(text.length(), m.start() + 300);
            String region = text.substring(m.start(), searchEnd);
            if (!region.contains(".catch(") && !region.contains(".catch (")) {
                // also check for try block before
                int searchStart = Math.max(0, m.start() - 200);
                String before = text.substring(searchStart, m.start());
                if (!before.contains("try")) {
                    int line = LinterUtils.getLine(text, m.start());
                    int col = LinterUtils.getColumn(text, m.start());
                    out.add(new Problem(file, line, col, 5,
                            "Promise chain missing '.catch()': unhandled rejections can cause silent failures",
                            Problem.Severity.WARNING));
                }
            }
        }
    }

    private static void checkMissingAwait(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        // Check fetch( without await on same line
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineOff = LinterUtils.lineStartOffset(text, i + 1);
            if (line.contains("fetch(") && !line.contains("await") && !line.contains(".then(")) {
                int idx = line.indexOf("fetch(");
                int absOff = lineOff + idx;
                if (!mask.isMasked(absOff)) {
                    out.add(new Problem(file, i + 1, idx + 1, 5,
                            "Missing 'await' before 'fetch(': result will be an unresolved Promise",
                            Problem.Severity.WARNING));
                }
            }
        }
    }

    private static void checkAsyncNoAwait(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_ASYNC_FN.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            String name = m.group(1) != null ? m.group(1) : (m.group(2) != null ? m.group(2) : "<anonymous>");
            int braceStart = text.indexOf('{', m.end());
            if (braceStart < 0) continue;
            int depth = 0, end = braceStart;
            for (int i = braceStart; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            String body = text.substring(braceStart, end);
            if (!body.contains("await ")) {
                int line = LinterUtils.getLine(text, m.start());
                int col = LinterUtils.getColumn(text, m.start());
                out.add(new Problem(file, line, col, m.group().length(),
                        "'async' function '" + name + "' has no 'await': remove 'async' or add awaited calls",
                        Problem.Severity.WARNING));
            }
        }
    }

    private static void checkTypeofComparison(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_TYPEOF_CMP.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "'typeof' always returns a string: compare to '\"undefined\"' not 'undefined'",
                    Problem.Severity.WARNING));
        }
    }

    private static void checkTodoFixme(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_TODO.matcher(text);
        while (m.find()) {
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            String txt = (m.group(2) != null ? m.group(2).trim() : "");
            out.add(new Problem(file, line, col, m.group(1).length(),
                    "TODO/FIXME found: '" + m.group(1) + (txt.isEmpty() ? "" : " " + txt) + "'",
                    Problem.Severity.INFO));
        }
    }

    private static void checkFunctionParams(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_FUNC_DECL.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            String params = m.group(2).trim();
            if (params.isEmpty()) continue;
            int count = params.split(",").length;
            if (count > 4) {
                int line = LinterUtils.getLine(text, m.start());
                int col = LinterUtils.getColumn(text, m.start());
                out.add(new Problem(file, line, col, m.group(1).length(),
                        "Function '" + m.group(1) + "' has " + count + " parameters: consider a config object for readability",
                        Problem.Severity.WARNING));
            }
        }
        Matcher m2 = PAT_ARROW_DECL.matcher(text);
        while (m2.find()) {
            if (mask.isMasked(m2.start())) continue;
            String params = m2.group(2) != null ? m2.group(2).trim() : "";
            if (params.isEmpty()) continue;
            int count = params.split(",").length;
            if (count > 4) {
                int line = LinterUtils.getLine(text, m2.start());
                int col = LinterUtils.getColumn(text, m2.start());
                out.add(new Problem(file, line, col, m2.group(1).length(),
                        "Function '" + m2.group(1) + "' has " + count + " parameters: consider a config object for readability",
                        Problem.Severity.WARNING));
            }
        }
    }

    private static void checkDivisionByZero(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_DIV_ZERO.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().trim().length(),
                    "Division by zero: '" + m.group().trim() + "'",
                    Problem.Severity.ERROR));
        }
    }

    private static void checkUnreachableCode(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Pattern terminators = Pattern.compile("\\b(return|throw|break|continue)\\b[^;{\\n]*(;|$)");
        Matcher m = terminators.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int termLine = LinterUtils.getLine(text, m.start());
            // check if next non-empty line is code (not closing brace or comment)
            if (termLine < lines.length) {
                for (int next = termLine; next < lines.length && next < termLine + 3; next++) {
                    String nextLine = lines[next].trim();
                    if (nextLine.isEmpty() || nextLine.startsWith("//") || nextLine.startsWith("*"))
                        continue;
                    if (nextLine.startsWith("}") || nextLine.startsWith(")") || nextLine.startsWith("]"))
                        break;
                    // there's code after a terminator
                    out.add(new Problem(file, next + 1, 1, nextLine.length(),
                            "Unreachable code after '" + m.group(1) + "' on line " + termLine,
                            Problem.Severity.WARNING));
                    break;
                }
            }
        }
    }

    private static void checkConstReassign(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        // JS-E004: const with no initializer
        Matcher m = PAT_CONST_INIT.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group(1).length(),
                    "'const " + m.group(1) + "' must be initialized at declaration",
                    Problem.Severity.ERROR));
        }
        // JS-E005: const reassignment — collect all const names with their declaration lines
        Map<String, Integer> constDecls = new LinkedHashMap<>();
        Matcher decl = Pattern.compile("\\bconst\\s+([a-zA-Z_$][\\w$]*)\\s*=").matcher(text);
        while (decl.find()) {
            if (!mask.isMasked(decl.start()))
                constDecls.put(decl.group(1), LinterUtils.getLine(text, decl.start()));
        }
        for (Map.Entry<String, Integer> e : constDecls.entrySet()) {
            String name = e.getKey();
            int declLine = e.getValue();
            // find reassignment: name = (not ==, !=, <=, >=, =>, +=, -=, *=, /=)
            Pattern reassign = Pattern.compile("\\b" + Pattern.quote(name) + "\\s*(?<![=!<>+\\-*/])=(?![=>])");
            Matcher rm = reassign.matcher(text);
            while (rm.find()) {
                if (mask.isMasked(rm.start())) continue;
                int rLine = LinterUtils.getLine(text, rm.start());
                if (rLine == declLine) continue; // skip declaration itself
                int col = LinterUtils.getColumn(text, rm.start());
                out.add(new Problem(file, rLine, col, name.length(),
                        "Cannot reassign 'const' variable '" + name + "' declared on line " + declLine,
                        Problem.Severity.ERROR));
            }
        }
    }


    private static void checkUnclosedString(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineOff = LinterUtils.lineStartOffset(text, i + 1);
            char openQuote = 0;
            int openPos = -1;
            for (int j = 0; j < line.length(); j++) {
                int absOff = lineOff + j;
                char c = line.charAt(j);
                if (openQuote == 0) {
                    if ((c == '\'' || c == '"') && !mask.inComment[absOff]) {
                        openQuote = c;
                        openPos = j;
                    }
                } else {
                    if (c == '\\') {
                        j++;
                        continue;
                    } // skip escaped char
                    if (c == openQuote) {
                        openQuote = 0;
                        openPos = -1;
                    }
                }
            }
            if (openQuote != 0 && !mask.inComment[lineOff]) {
                out.add(new Problem(file, i + 1, openPos + 1, 1,
                        "Unclosed string literal: string opened with '" + openQuote + "' is not closed on this line",
                        Problem.Severity.ERROR));
            }
        }
    }

    private static void checkReturnOutsideFunction(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        // Track function depth via { }; return at depth 0 is outside any function
        int fnDepth = 0;
        int i = 0;
        while (i < text.length()) {
            if (mask.isMasked(i)) {
                i++;
                continue;
            }
            char c = text.charAt(i);
            if (c == '{') {
                fnDepth++;
                i++;
                continue;
            }
            if (c == '}') {
                if (fnDepth > 0) fnDepth--;
                i++;
                continue;
            }
            // check for 'function' keyword or arrow
            if (c == 'f' && text.startsWith("function", i)) {
                i += 8;
                continue;
            }
            if (c == 'r' && text.startsWith("return", i)) {
                if (fnDepth == 0) {
                    // ensure it's a word boundary
                    boolean before = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
                    boolean after = i + 6 >= text.length() || !Character.isLetterOrDigit(text.charAt(i + 6));
                    if (before && after) {
                        int line = LinterUtils.getLine(text, i);
                        int col = LinterUtils.getColumn(text, i);
                        out.add(new Problem(file, line, col, 6,
                                "'return' outside of a function body", Problem.Severity.ERROR));
                    }
                }
                i += 6;
                continue;
            }
            i++;
        }
    }

    private static void checkBreakContinue(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        int loopDepth = 0;
        int i = 0;
        while (i < text.length()) {
            if (mask.isMasked(i)) {
                i++;
                continue;
            }
            char c = text.charAt(i);
            // detect loop/switch start keywords
            if (c == 'f' && text.startsWith("for", i) && (i + 3 >= text.length() || !Character.isLetterOrDigit(text.charAt(i + 3)))) {
                loopDepth++;
                i += 3;
                continue;
            }
            if (c == 'w' && text.startsWith("while", i) && (i + 5 >= text.length() || !Character.isLetterOrDigit(text.charAt(i + 5)))) {
                loopDepth++;
                i += 5;
                continue;
            }
            if (c == 'd' && text.startsWith("do", i) && (i + 2 >= text.length() || !Character.isLetterOrDigit(text.charAt(i + 2)))) {
                loopDepth++;
                i += 2;
                continue;
            }
            if (c == 's' && text.startsWith("switch", i) && (i + 6 >= text.length() || !Character.isLetterOrDigit(text.charAt(i + 6)))) {
                loopDepth++;
                i += 6;
                continue;
            }
            if (c == '{') {
                i++;
                continue;
            }
            if (c == '}') {
                if (loopDepth > 0) loopDepth--;
                i++;
                continue;
            }
            if ((c == 'b' && text.startsWith("break", i)) || (c == 'c' && text.startsWith("continue", i))) {
                int kwLen = c == 'b' ? 5 : 8;
                boolean before = i == 0 || !Character.isLetterOrDigit(text.charAt(i - 1));
                boolean after = i + kwLen >= text.length() || !Character.isLetterOrDigit(text.charAt(i + kwLen));
                if (before && after && loopDepth == 0) {
                    String kw = c == 'b' ? "break" : "continue";
                    int line = LinterUtils.getLine(text, i);
                    int col = LinterUtils.getColumn(text, i);
                    out.add(new Problem(file, line, col, kwLen,
                            "'" + kw + "' used outside of a loop or switch statement",
                            Problem.Severity.ERROR));
                }
                i += kwLen;
                continue;
            }
            i++;
        }
    }

    private static void checkUnusedVars(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_LET_CONST.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            String name = m.group(2);
            if (KnownElements.JS_GLOBALS.contains(name)) continue;
            int declEnd = m.end();
            // count usages after declaration
            Pattern use = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
            Matcher um = use.matcher(text);
            int usages = 0;
            while (um.find()) {
                if (mask.isMasked(um.start())) continue;
                if (um.start() >= declEnd) usages++;
            }
            if (usages == 0) {
                int line = LinterUtils.getLine(text, m.start());
                int col = LinterUtils.getColumn(text, m.start());
                out.add(new Problem(file, line, col, name.length(),
                        "Variable '" + name + "' is declared but never used",
                        Problem.Severity.WARNING));
            }
        }
    }

    private static void checkArrowSimplification(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_ARROW_SIMP.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, 8,
                    "Arrow function simplification: '() => expr' instead of 'function() { return expr; }'",
                    Problem.Severity.INFO));
        }
    }

    private static void checkStringConcat(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_STR_CONCAT.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "Prefer template literal over string concatenation: use backticks",
                    Problem.Severity.INFO));
        }
    }

    private static void checkOptionalChaining(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_OPT_CHAIN.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "Consider optional chaining: '" + m.group(1) + " && " + m.group(1) + "." + m.group(2) + "' → '" + m.group(1) + "?." + m.group(2) + "'",
                    Problem.Severity.INFO));
        }
    }

    private static void checkNullishCoalescing(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        Matcher m = PAT_NULLISH.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int line = LinterUtils.getLine(text, m.start());
            int col = LinterUtils.getColumn(text, m.start());
            out.add(new Problem(file, line, col, m.group().length(),
                    "Consider nullish coalescing '??' if the left side being 0 or '' should not use the default",
                    Problem.Severity.INFO));
        }
    }

    private static void checkStringConcatInLoop(File file, String text, String[] lines, TokenMask mask, List<Problem> out) {
        // Find for/while loops and check body for string concatenation
        Pattern loopPat = Pattern.compile("\\b(for|while)\\s*\\(");
        Matcher m = loopPat.matcher(text);
        while (m.find()) {
            if (mask.isMasked(m.start())) continue;
            int braceStart = text.indexOf('{', m.end());
            if (braceStart < 0) continue;
            int depth = 0, end = braceStart;
            for (int i = braceStart; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            String body = text.substring(braceStart, end);
            int bodyStart = braceStart;
            Matcher cm = PAT_STR_CONCAT_LOOP.matcher(body);
            while (cm.find()) {
                int absOff = bodyStart + cm.start();
                if (mask.isMasked(absOff)) continue;
                int line = LinterUtils.getLine(text, absOff);
                int col = LinterUtils.getColumn(text, absOff);
                out.add(new Problem(file, line, col, cm.group().length(),
                        "String concatenation in loop: use array.push() + join() or template literals",
                        Problem.Severity.WARNING));
            }
        }
    }
}
