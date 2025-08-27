package com.lmc.ide;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LMCSyntaxHighlighter {

    private static final String[] LMC_KEYWORDS = new String[] {
            "INP", "OUT", "LDA", "STA", "ADD", "SUB", "BRA", "BRZ", "BRP", "HLT", "DAT"
    };

    private static final String KEYWORD_REGEX = "\\b(" + String.join("|", LMC_KEYWORDS) + ")\\b";
    private static final String LABEL_REGEX = "\\b[A-Za-z_][A-Za-z0-9_]*\\s*:";
    private static final String NUMBER_REGEX = "\\b\\d+\\b";
    private static final String COMMENT_REGEX = "(//|#).*";
    // Operand regex is broad, but the order in the main pattern ensures keywords
    // are matched first.
    private static final String OPERAND_REGEX = "\\b[A-Za-z_][A-Za-z0-9_]*\\b";

    private static final Pattern PATTERN = Pattern.compile(
            String.format("(?<KEYWORD>%s)|(?<LABEL>%s)|(?<NUMBER>%s)|(?<COMMENT>%s)|(?<OPERAND>%s)",
                    KEYWORD_REGEX, LABEL_REGEX, NUMBER_REGEX, COMMENT_REGEX, OPERAND_REGEX));

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while (matcher.find()) {
            String styleClass = null;
            if (matcher.group("KEYWORD") != null) {
                styleClass = "keyword";
            } else if (matcher.group("LABEL") != null) {
                styleClass = "label";
            } else if (matcher.group("NUMBER") != null) {
                styleClass = "number";
            } else if (matcher.group("COMMENT") != null) {
                styleClass = "comment";
            } else if (matcher.group("OPERAND") != null) {
                styleClass = "operand";
            }

            // Add an unstyled span for the text between matches (e.g., whitespace).
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            // Add the styled span for the matched token.
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }

        // FIX: This is the crucial line. It handles any remaining text after the last
        // match.
        // It also handles the case where the text is empty or contains no matches,
        // which prevents the "No spans have been added" crash.
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);

        return spansBuilder.create();
    }
}