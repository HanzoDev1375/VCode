package com.cocode.vcode.ide.core.autocomplete;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EmmetParser {

    private static final Pattern PAT_ABBR = Pattern.compile("^[a-zA-Z0-9_\\-#.*+>]+$");
    private static final Pattern PAT_CSS_ABBR = Pattern.compile("^[a-z]+-?[a-z]*[0-9]+[a-z%]*$");
    private static final Pattern PAT_CSS_PROP = Pattern.compile("^([a-z]+)(-?[a-z]*)([0-9]+)([a-z%]*)$");
    private static final Pattern PAT_EMMET_NODE = Pattern.compile("([>+])?([a-zA-Z0-9_\\-#.*]+)");
    private static final Pattern PAT_EMMET_PARSE = Pattern.compile("^([a-zA-Z0-9_-]*)(#[a-zA-Z0-9_-]+)?((?:\\.[a-zA-Z0-9_-]+)*)(?:\\*([0-9]+))?$");


    public static String expandHtml(String abbr, String boilerplate) {
        if (abbr == null || abbr.trim().isEmpty()) return null;
        if (abbr.equals("!")) {
            if (boilerplate != null && !boilerplate.isEmpty()) {
                return boilerplate;
            }
            return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n    <title>Document</title>\n</head>\n<body>\n    |\n</body>\n</html>";
        }

        if (!PAT_ABBR.matcher(abbr).matches()) {
            return null;
        }

        try {
            return parseEmmet(abbr);
        } catch (Exception e) {
            return null;
        }
    }

    public static String expandCss(String abbr) {
        if (abbr == null || abbr.isEmpty()) return null;

        switch (abbr) {
            case "df":
                return "display: flex;|";
            case "db":
                return "display: block;|";
            case "dib":
                return "display: inline-block;|";
            case "dn":
                return "display: none;|";
            case "jcc":
                return "justify-content: center;|";
            case "jcsb":
                return "justify-content: space-between;|";
            case "jcsa":
                return "justify-content: space-around;|";
            case "aic":
                return "align-items: center;|";
            case "ais":
                return "align-items: stretch;|";
            case "bgc":
                return "background-color: #|;";
        }

        if (PAT_CSS_ABBR.matcher(abbr).matches()) {
            Matcher m = PAT_CSS_PROP.matcher(abbr);
            if (m.matches()) {
                String propMap = getCssProp(Objects.requireNonNull(m.group(1)));
                if (propMap != null) {
                    String unit = m.group(4);
                    assert unit != null;
                    if (unit.isEmpty()) unit = "px";
                    return propMap + ": " + m.group(3) + unit + ";|";
                }
            }
        }

        return null;
    }

    private static String getCssProp(String p) {
        switch (p) {
            case "m":
                return "margin";
            case "p":
                return "padding";
            case "mt":
                return "margin-top";
            case "mb":
                return "margin-bottom";
            case "ml":
                return "margin-left";
            case "mr":
                return "margin-right";
            case "pt":
                return "padding-top";
            case "pb":
                return "padding-bottom";
            case "pl":
                return "padding-left";
            case "pr":
                return "padding-right";
            case "w":
                return "width";
            case "h":
                return "height";
            case "fs":
                return "font-size";
            case "br":
                return "border-radius";
            default:
                return null;
        }
    }

    private static String parseEmmet(String abbr) {
        Matcher m = PAT_EMMET_NODE.matcher(abbr);

        Node root = new Node("root");
        Node current = root;

        int lastEnd = 0;
        while (m.find()) {
            if (m.start() != lastEnd) return null;
            lastEnd = m.end();

            String op = m.group(1);
            String nodeStr = m.group(2);

            Node[] parsed = parseNode(nodeStr);
            if (parsed == null) return null;

            if (">".equals(op)) {
                for (Node n : parsed) current.addChild(n);
                current = parsed[parsed.length - 1];
            } else if ("+".equals(op)) {
                if (current.parent != null) {
                    for (Node n : parsed) current.parent.addChild(n);
                    current = parsed[parsed.length - 1];
                }
            } else {
                for (Node n : parsed) current.addChild(n);
                current = parsed[parsed.length - 1];
            }
        }
        if (lastEnd != abbr.length()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < root.children.size(); i++) {
            renderNode(root.children.get(i), sb, 0, i == root.children.size() - 1);
        }

        String result = sb.toString();
        if (result.contains("|")) return result;
        int firstClose = result.indexOf("></");
        if (firstClose != -1) {
            return result.substring(0, firstClose + 1) + "|" + result.substring(firstClose + 1);
        }
        return result + "|";
    }

    private static void renderNode(Node node, StringBuilder sb, int indent, boolean isLast) {
        String ind = getIndent(indent);
        sb.append(ind).append("<").append(node.tag);
        if (node.id != null) sb.append(" id=\"").append(node.id).append("\"");
        if (node.classes != null && !node.classes.isEmpty()) {
            sb.append(" class=\"");
            for (int i = 0; i < node.classes.size(); i++) {
                sb.append(node.classes.get(i));
                if (i < node.classes.size() - 1) sb.append(" ");
            }
            sb.append("\"");
        }

        boolean isSelfClosing = node.tag.equals("img") || node.tag.equals("input") || node.tag.equals("br") || node.tag.equals("hr") || node.tag.equals("meta") || node.tag.equals("link");

        sb.append(">");
        if (!isSelfClosing) {
            if (node.children.isEmpty()) {
                sb.append("</").append(node.tag).append(">");
            } else {
                sb.append("\n");
                for (int i = 0; i < node.children.size(); i++) {
                    renderNode(node.children.get(i), sb, indent + 1, i == node.children.size() - 1);
                }
                sb.append(ind).append("</").append(node.tag).append(">");
            }
        }
        if (!isLast) sb.append("\n");
    }

    private static String getIndent(int levels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < levels; i++) sb.append("  ");
        return sb.toString();
    }

    private static Node[] parseNode(String str) {
        Matcher m = PAT_EMMET_PARSE.matcher(str);
        if (!m.matches()) return null;

        String tag = m.group(1);
        if (tag == null || tag.isEmpty()) tag = "div";

        String idStr = m.group(2);
        String id = (idStr != null && idStr.length() > 1) ? idStr.substring(1) : null;

        String classesStr = m.group(3);
        List<String> classes = new ArrayList<>();
        if (classesStr != null && !classesStr.isEmpty()) {
            String[] cls = classesStr.substring(1).split("\\.");
            for (String c : cls) if (!c.isEmpty()) classes.add(c);
        }

        String multStr = m.group(4);
        int mult = 1;
        if (multStr != null && !multStr.isEmpty()) {
            mult = Integer.parseInt(multStr);
        }

        Node[] nodes = new Node[mult];
        for (int i = 0; i < mult; i++) {
            Node n = new Node(tag);
            n.id = id;
            if (id != null && mult > 1) n.id = id + (i + 1);
            n.classes = classes;
            nodes[i] = n;
        }
        return nodes;
    }

    static class Node {
        String tag;
        String id;
        List<String> classes;
        List<Node> children = new ArrayList<>();
        Node parent;

        Node(String tag) {
            this.tag = tag;
        }

        void addChild(Node c) {
            c.parent = this;
            children.add(c);
        }
    }
}
