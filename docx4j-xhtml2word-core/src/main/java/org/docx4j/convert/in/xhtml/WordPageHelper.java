package org.docx4j.convert.in.xhtml;

import com.openhtmltopdf.render.InlineBox;
import jakarta.xml.bind.JAXBElement;
import org.docx4j.jaxb.Context;
import org.docx4j.wml.*;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @Fixed by longyg @2023.6.15:
 * <p>
 * Helper class to handle word page and page nums.
 * @author longyg
 */
public class WordPageHelper {
    public static final String WORD_PAGE_TAG = "data-word-page-tag";
    public static final String PAGE_INSTR_TEXT = " PAGE   \\* MERGEFORMAT ";
    public static final String NUMPAGES_INSTR_TEXT = " NUMPAGES  \\* Arabic  \\* MERGEFORMAT ";

    public static final ObjectFactory objectFactory = Context.getWmlObjectFactory();

    public enum WordPageTag {
        CURRENT_PAGE("current-page"), TOTAL_PAGE("total-page");

        private String tag;

        WordPageTag(String tag) {
            this.tag = tag;
        }

        public String getTag() {
            return tag;
        }

        public static WordPageTag fromValue(String tag) {
            for (WordPageTag value : WordPageTag.values()) {
                if (value.getTag().equals(tag)) {
                    return value;
                }
            }
            return null;
        }
    }

    public static WordPageTag getWordPageTag(InlineBox inlineBox) {
        if (null == inlineBox || null == inlineBox.getElement()) return null;
        Element element = inlineBox.getElement();
        if ("span".equals(element.getNodeName())) {
            String tag = element.getAttribute(WORD_PAGE_TAG);
            return WordPageTag.fromValue(tag);
        }
        return null;
    }

    public static List<R> createWordPageRuns(WordPageTag wordPageTag, InlineBox inlineBox) {
        List<R> runs = new ArrayList<>();
        runs.add(createFldCharRun(STFldCharType.BEGIN));
        runs.add(createInstrTextRun(wordPageTag));
        runs.add(createFldCharRun(STFldCharType.SEPARATE));
        runs.add(createTextRun(inlineBox));
        runs.add(createFldCharRun(STFldCharType.END));
        return runs;
    }

    public static R createFldCharRun(STFldCharType fldCharType) {
        R run = objectFactory.createR();
        FldChar fldChar = objectFactory.createFldChar();
        fldChar.setFldCharType(fldCharType);
        run.getContent().add(fldChar);
        return run;
    }

    public static R createInstrTextRun(WordPageTag wordPageTag) {
        R run = objectFactory.createR();
        Text text = objectFactory.createText();
        text.setSpace("preserve");
        text.setValue(getInstrText(wordPageTag));
        JAXBElement<Text> instrText = objectFactory.createRInstrText(text);
        run.getContent().add(instrText);
        return run;
    }

    public static R createTextRun(InlineBox inlineBox) {
        R run = objectFactory.createR();
        Text text = objectFactory.createText();
        text.setValue(inlineBox.getText());
        run.getContent().add(text);
        return run;
    }

    private static String getInstrText(WordPageTag wordPageTag) {
        switch (wordPageTag) {
            case CURRENT_PAGE:
                return PAGE_INSTR_TEXT;
            case TOTAL_PAGE:
                return NUMPAGES_INSTR_TEXT;
            default:
                return "";
        }
    }
}
