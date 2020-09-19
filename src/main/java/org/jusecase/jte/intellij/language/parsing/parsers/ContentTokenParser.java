package org.jusecase.jte.intellij.language.parsing.parsers;

import org.jusecase.jte.intellij.language.parsing.JteLexer;
import org.jusecase.jte.intellij.language.parsing.JteTokenTypes;

public class ContentTokenParser extends AbstractTokenParser {
    private static final String[] KEYWORDS = {
            "${",
            "$unsafe{",
            "!{",
            "<%--",
            "@if",
            "@else",
            "@elseif",
            "@endif",
            "@for",
            "@endfor",
            "@import",
            "@param",
            "@tag",
            "@layout"
    };

    private final JteLexer lexer;
    private boolean insideString;

    public ContentTokenParser(JteLexer lexer) {
        this.lexer = lexer;
    }

    @Override
    public boolean hasToken(int position) {
        insideString = false;

        if (shouldSkipWhitespaces(position) && isWhitespace(position)) {
            return skipWhitespaces(position);
        }

        if (lexer.isInJavaEndState()) {
            lexer.setCurrentState(JteLexer.CONTENT_STATE_HTML);
        }

        if (isBeginOfJteKeyword(position)) {
            return false;
        }

        int currentState = lexer.getCurrentState();
        int start = position;
        position++;

        while (position < myEndOffset && !isBeginOfJteKeyword(position)) {
            if (currentState == JteLexer.CONTENT_STATE_HTML && !lexer.isImportOrParamIgnored() && !isWhitespace(position)) {
                lexer.setImportOrParamIgnored(true);
            }
            position++;
        }

        if (currentState == JteLexer.CONTENT_STATE_TAG_NAME_BEGIN) {
            myTokenInfo.updateData(start, position, JteTokenTypes.TAG_NAME);
        } else if (currentState == JteLexer.CONTENT_STATE_HTML) {
            myTokenInfo.updateData(start, position, JteTokenTypes.HTML_CONTENT);
        } else if (currentState == JteLexer.CONTENT_STATE_PARAM_DEFAULT_VALUE) {
            myTokenInfo.updateData(start, position, JteTokenTypes.EXTRA_JAVA_INJECTION);
        } else if (currentState == JteLexer.CONTENT_STATE_PARAM_NAME) {
            myTokenInfo.updateData(start, position, JteTokenTypes.PARAM_NAME);
        } else {
            myTokenInfo.updateData(start, position, JteTokenTypes.JAVA_INJECTION);
        }
        return true;
    }

    private boolean shouldSkipWhitespaces(int position) {
        int state = lexer.getCurrentState();
        if (state == JteLexer.CONTENT_STATE_PARAM_DEFAULT_VALUE && isBeginOf(position, '\n')) {
            return false;
        }

        return
                state == JteLexer.CONTENT_STATE_IMPORT_BEGIN ||
                        state == JteLexer.CONTENT_STATE_PARAM_BEGIN ||
                        state == JteLexer.CONTENT_STATE_FOR_BEGIN ||
                        state == JteLexer.CONTENT_STATE_ELSEIF_BEGIN ||
                        state == JteLexer.CONTENT_STATE_IF_BEGIN ||
                        state == JteLexer.CONTENT_STATE_PARAM_DEFAULT_VALUE ||
                        state == JteLexer.CONTENT_STATE_PARAM_NAME ||
                        state == JteLexer.CONTENT_STATE_TAG_PARAMS;
    }

    private boolean isBeginOfJteKeyword(int position) {
        if (insideString) {
            if (isBeginOf(position, '\"') && myBuffer.charAt(position - 1) != '\\') {
                insideString = false;
            }
            return false;
        }

        if (lexer.getCurrentState() == JteLexer.CONTENT_STATE_HTML || lexer.isInJavaEndState()) {
            for (String keyword : KEYWORDS) {
                if (("@import".equals(keyword) || "@param".equals(keyword)) && lexer.isImportOrParamIgnored()) {
                    continue;
                }

                if (isBeginOf(position, keyword)) {
                    return true;
                }
            }
        }

        if (isBeginOf(position, '\n')) {
            switch (lexer.getCurrentState()) {
                case JteLexer.CONTENT_STATE_IMPORT_BEGIN:
                    lexer.setCurrentState(JteLexer.CONTENT_STATE_IMPORT_END);
                    return true;
                case JteLexer.CONTENT_STATE_PARAM_BEGIN:
                case JteLexer.CONTENT_STATE_PARAM_DEFAULT_VALUE:
                    lexer.setCurrentState(JteLexer.CONTENT_STATE_PARAM_END);
                    return true;
            }
        }

        if (lexer.getCurrentState() == JteLexer.CONTENT_STATE_PARAM_BEGIN) {
            for (int index = position; index < myEndOffset; ++index) {
                if (myBuffer.charAt(index) == '=') {
                    lexer.setCurrentState(JteLexer.CONTENT_STATE_PARAM_DEFAULT_VALUE);
                    return true;
                }

                if (!isWhitespace(index)) {
                    return false;
                }
            }
        }

        if (lexer.getCurrentState() == JteLexer.CONTENT_STATE_PARAM_DEFAULT_VALUE && isBeginOf(position, '=')) {
            return true;
        }

        if (isBeginOf(position, "@`") && lexer.getCurrentState() != JteLexer.CONTENT_STATE_HTML) {
            return true;
        }

        if (isBeginOf(position, "`") && lexer.getCurrentState() == JteLexer.CONTENT_STATE_HTML) {
            return true;
        }

        if (lexer.getCurrentState() == JteLexer.CONTENT_STATE_TAG_PARAMS) {
            for (int index = position; index < myEndOffset; ++index) {
                if (isBeginOf(index, "@`")) {
                    break;
                }

                char currentChar = myBuffer.charAt(index);
                if (currentChar == ')' && (isBeginOf(position, '\n') || isBeginOf(position, '\r'))) {
                    return true;
                }

                if ((currentChar == ',' && index > position) || currentChar == ')' || currentChar == '\"') {
                    break;
                }

                if (currentChar == '=' && index + 1 < myEndOffset && myBuffer.charAt(index + 1) != '=' && myBuffer.charAt(index - 1) != '=') {
                    lexer.setCurrentState(JteLexer.CONTENT_STATE_PARAM_NAME);
                    lexer.setCurrentCount(JteLexer.CONTENT_COUNT_PARAM_NAME_TAG);
                    return isBeginOf(position, ',');
                }
            }
        }

        if (lexer.getCurrentState() == JteLexer.CONTENT_STATE_PARAM_NAME) {
            if (isBeginOf(position, '=') || isWhitespace(position) || isBeginOf(position, ',')) {
                return true;
            }

            if (lexer.getCurrentCount() == JteLexer.CONTENT_COUNT_PARAM_NAME_TAG_DONE) {
                lexer.setCurrentState(JteLexer.CONTENT_STATE_TAG_PARAMS);
            }
        }

        if (isBeginOf(position, '{')) {
            switch (lexer.getCurrentState()) {
                case JteLexer.CONTENT_STATE_OUTPUT_BEGIN:
                case JteLexer.CONTENT_STATE_STATEMENT_BEGIN:
                    lexer.incrementCurrentCount();
                    return false;
            }
        }

        if (isBeginOf(position, '}')) {
            switch (lexer.getCurrentState()) {
                case JteLexer.CONTENT_STATE_OUTPUT_BEGIN:
                case JteLexer.CONTENT_STATE_STATEMENT_BEGIN:
                    int count = lexer.getCurrentCount();
                    lexer.decrementCurrentCount();
                    return count <= 0;
            }
        }

        if (isBeginOf(position, '(')) {
            switch (lexer.getCurrentState()) {
                case JteLexer.CONTENT_STATE_IF_BEGIN:
                case JteLexer.CONTENT_STATE_ELSEIF_BEGIN:
                case JteLexer.CONTENT_STATE_FOR_BEGIN:
                case JteLexer.CONTENT_STATE_TAG_NAME_BEGIN:
                    return true;
                case JteLexer.CONTENT_STATE_IF_CONDITION:
                case JteLexer.CONTENT_STATE_ELSEIF_CONDITION:
                case JteLexer.CONTENT_STATE_FOR_CONDITION:
                case JteLexer.CONTENT_STATE_TAG_PARAMS:
                    lexer.incrementCurrentCount();
                    return false;
            }
        }

        if (isBeginOf(position, ')')) {
            switch (lexer.getCurrentState()) {
                case JteLexer.CONTENT_STATE_IF_CONDITION:
                case JteLexer.CONTENT_STATE_ELSEIF_CONDITION:
                case JteLexer.CONTENT_STATE_FOR_CONDITION:
                case JteLexer.CONTENT_STATE_TAG_PARAMS:
                    int count = lexer.getCurrentCount();
                    lexer.decrementCurrentCount();
                    return count <= 0;
            }
        }

        if (isBeginOf(position, '.')) {
            switch (lexer.getCurrentState()) {
                case JteLexer.CONTENT_STATE_TAG_BEGIN:
                case JteLexer.CONTENT_STATE_TAG_NAME_BEGIN:
                    return true;
            }
        }

        if (lexer.getCurrentState() != JteLexer.CONTENT_STATE_HTML && isBeginOf(position, '\"')) {
            insideString = true;
            return false;
        }

        return false;
    }
}
