package com.hotplay.automation.core;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a uiautomator XML dump into a UiNode tree.
 *
 * Empty or whitespace-only input returns a synthetic empty root instead of
 * throwing. This happens when uiautomator returns nothing (screen mid-render,
 * device hiccup, adb hiccup) — the caller can then retry via waitFor polling
 * without being interrupted by exceptions.
 */
public final class XmlParser {
    private XmlParser() {}

    private static final Pattern BOUNDS =
            Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]");

    /** Parse a uiautomator XML dump and return the root UiNode (the first <node>). */
    public static UiNode parse(String xml) {
        if (xml == null || xml.trim().isEmpty()) {
            return emptyRoot();
        }
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",
                    false);
            DocumentBuilder b = f.newDocumentBuilder();
            Document doc = b.parse(new ByteArrayInputStream(
                    xml.getBytes(StandardCharsets.UTF_8)));

            NodeList nodes = doc.getElementsByTagName("node");
            if (nodes.getLength() == 0) return emptyRoot();

            Element rootEl = (Element) nodes.item(0);
            UiNode root = toUiNode(rootEl);
            attachChildren(rootEl, root);
            return root;
        } catch (Exception e) {
            // Single warning line instead of a stack trace per poll.
            System.err.println("  [warn] XML parse failed: " + e.getMessage());
            return emptyRoot();
        }
    }

    private static void attachChildren(Element el, UiNode parent) {
        NodeList kids = el.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++) {
            Node n = kids.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && "node".equals(n.getNodeName())) {
                Element ce = (Element) n;
                UiNode child = toUiNode(ce);
                parent.addChild(child);
                attachChildren(ce, child);
            }
        }
    }

    private static UiNode toUiNode(Element e) {
        int[] b = parseBounds(e.getAttribute("bounds"));
        return new UiNode(
                attr(e, "resource-id"),
                attr(e, "text"),
                attr(e, "content-desc"),
                attr(e, "class"),
                attr(e, "package"),
                bool(e, "enabled"),
                bool(e, "clickable"),
                bool(e, "focusable"),
                bool(e, "focused"),
                bool(e, "checked"),
                bool(e, "checkable"),
                bool(e, "selected"),
                bool(e, "scrollable"),
                b[0], b[1], b[2], b[3]);
    }

    /** Synthetic empty root — all findById / findByText calls return null. */
    private static UiNode emptyRoot() {
        return new UiNode("", "", "", "", "",
                false, false, false, false,
                false, false, false, false,
                0, 0, 0, 0);
    }

    private static String attr(Element e, String name) {
        String v = e.getAttribute(name);
        return v == null ? "" : v;
    }

    private static boolean bool(Element e, String name) {
        return "true".equals(e.getAttribute(name));
    }

    private static int[] parseBounds(String s) {
        Matcher m = BOUNDS.matcher(s == null ? "" : s);
        if (m.find()) return new int[]{
                Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))};
        return new int[]{0, 0, 0, 0};
    }
}