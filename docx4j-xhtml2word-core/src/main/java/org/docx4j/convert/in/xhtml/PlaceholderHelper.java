package org.docx4j.convert.in.xhtml;

import com.openhtmltopdf.layout.Styleable;
import jakarta.xml.bind.JAXBElement;
import org.docx4j.jaxb.Context;
import org.docx4j.wml.*;
import org.w3c.dom.Element;

import static org.docx4j.convert.in.xhtml.Placeholder.SEPARATOR;

/**
 * @author longyg
 */
public class PlaceholderHelper {

    private static final String MERGE_FIELD_PREFIX = " MERGEFIELD  ";
    private static final String MERGE_FIELD_SUFFIX = "  \\* MERGEFORMAT ";

    private static final String TEXT_PREFIX = "«";

    private static final String TEXT_SUFFIX = "»";

    private static final String PREFIX_COLOR = "800080"; // purple
    private static final String BACKGROUND_COLOR = "fef3e5";

    public Placeholder checkAndGetPlaceholder(Styleable box) {
        Element element = box.getElement();
        if (null == element) return null;
        return checkAndGetPlaceholder(element);
    }

    private Placeholder checkAndGetPlaceholder(Element element) {
        String nodeName = element.getNodeName();
        if (!"object".equals(nodeName)) return null;
        boolean isPlaceholder = Boolean.parseBoolean(element.getAttribute("data-placeholder"));
        if (!isPlaceholder) return null;
        PlaceholderType type = PlaceholderType.fromValue(element.getAttribute("data-type"));
        if (null == type) return null;
        String id = element.getAttribute("data-id");
        String name = element.getAttribute("data-name");
        return new Placeholder(id, name, type);
    }

    public void addPlaceholder(ContentAccessor contentAccessor, Placeholder placeholder) {
        contentAccessor.getContent().add(createPlaceholderMergeField(placeholder));
    }

    // do not have any style on created merge filed
    private JAXBElement<CTSimpleField> createPlaceholderMergeFieldWithoutStyle(Placeholder placeholder) {
        CTSimpleField field = Context.getWmlObjectFactory().createCTSimpleField();
        field.setInstr(buildInstruction(placeholder));
        addRun(field, createNoBackgroundRun(TEXT_PREFIX +
                placeholder.getType().getDisplay() +
                placeholder.getString() + TEXT_SUFFIX));
        return Context.getWmlObjectFactory().createPFldSimple(field);
    }

    // have color and background color style on created merge field
    private JAXBElement<CTSimpleField> createPlaceholderMergeField(Placeholder placeholder) {
        CTSimpleField field = Context.getWmlObjectFactory().createCTSimpleField();
        field.setInstr(buildInstruction(placeholder));
        addRuns(field, placeholder);
        return Context.getWmlObjectFactory().createPFldSimple(field);
    }

    private String buildInstruction(Placeholder placeholder) {
        StringBuilder sb = new StringBuilder();
        sb.append(MERGE_FIELD_PREFIX).append(placeholder.getType().getDisplay())
                .append(SEPARATOR).append(placeholder.getName()).append(SEPARATOR)
                .append(placeholder.getId()).append(MERGE_FIELD_SUFFIX);
        return sb.toString();
    }

    private void addRuns(CTSimpleField field, Placeholder placeholder) {
        addRun(field, createRun(TEXT_PREFIX));
        addRun(field, createColoredRun(placeholder.getType().getDisplay()));
        addRun(field, createRun(placeholder.getString() + TEXT_SUFFIX));
    }

    private R createRun(String text) {
        R r = Context.getWmlObjectFactory().createR();
        setText(r, text);
        setBackgroundColor(r);
        return r;
    }

    private R createNoBackgroundRun(String text) {
        R r = Context.getWmlObjectFactory().createR();
        setText(r, text);
        return r;
    }

    private R createColoredRun(String text) {
        R r = Context.getWmlObjectFactory().createR();
        RPr rPr = Context.getWmlObjectFactory().createRPr();
        Color color = Context.getWmlObjectFactory().createColor();
        color.setVal(PREFIX_COLOR);
        rPr.setColor(color);
        rPr.setB(Context.getWmlObjectFactory().createBooleanDefaultTrue());
        r.setRPr(rPr);
        setText(r, text);
        setBackgroundColor(r);
        return r;
    }

    private void addRun(CTSimpleField field, R run) {
        field.getContent().add(run);
    }

    private static void setText(R r, String text) {
        Text t = Context.getWmlObjectFactory().createText();
        r.getContent().add(t);
        t.setValue(text);
    }

    private void setBackgroundColor(R r) {
        RPr rPr = r.getRPr();
        if (null == rPr) {
            rPr = Context.getWmlObjectFactory().createRPr();
            r.setRPr(rPr);
        }
        CTShd shd = Context.getWmlObjectFactory().createCTShd();
        shd.setFill(BACKGROUND_COLOR);
        rPr.setShd(shd);
    }
}
