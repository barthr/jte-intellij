package org.jusecase.jte.intellij.language.parsing.parsers;

import org.jusecase.jte.intellij.language.parsing.JteLexer;
import org.jusecase.jte.intellij.language.parsing.JteTokenTypes;

public class ElseIfTokenParser extends AbstractTokenParser {
    private final JteLexer lexer;

    public ElseIfTokenParser(JteLexer lexer) {
        this.lexer = lexer;
    }

    @Override
    public boolean hasToken(int position) {
        if (hasToken(position, "@elseif", JteTokenTypes.ELSEIF)) {
            lexer.setCurrentState(JteLexer.CONTENT_STATE_JAVA_ELSEIF_BEGIN);
            return true;
        }
        return false;
    }
}
