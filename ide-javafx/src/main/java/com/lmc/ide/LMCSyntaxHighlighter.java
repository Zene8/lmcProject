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
    private static final String OPERAND_REGEX = "\\b[A-Za-z_][A-Za-z0-9_]*\\b";

    private static final Pattern PATTERN = Pattern.compile(
            String.format("(?<KEYWORD>%s)|(?<LABEL>%s)|(?<NUMBER>%s)|(?<COMMENT>%s)|(?<OPERAND>%s)",
                    KEYWORD_REGEX, LABEL_REGEX, NUMBER_REGEX, COMMENT_REGEX, OPERAND_REGEX),
            Pattern.CASE_INSENSITIVE);

    public static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass = matcher.group("KEYWORD") != null ? "keyword"
                    : matcher.group("LABEL") != null ? "label"
                            : matcher.group("NUMBER") != null ? "number"
                                    : matcher.group("COMMENT") != null ? "comment"
                                            : matcher.group("OPERAND") != null ? "operand" : null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
}
