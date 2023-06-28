package org.docx4j.convert.in.xhtml.utils;

import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.layout.Styleable;
import org.docx4j.convert.in.xhtml.DomCssValueAdaptor;
import org.docx4j.jaxb.Context;
import org.docx4j.model.PropertyResolver;
import org.docx4j.model.properties.run.DStrike;
import org.docx4j.model.properties.run.Strike;
import org.docx4j.model.properties.run.Underline;
import org.docx4j.model.styles.StyleUtil;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;
import org.w3c.dom.css.CSSPrimitiveValue;
import org.w3c.dom.css.CSSValue;

/**
 * @author longyg
 * @Fixed by longyg @2023.6.19:
 * <p>
 * Provide style related functions
 */
public class StyleUtils {

    private static final String LINE_THROUGH = "line-through";
    private static final String DOUBLE_LINE_THROUGH = "line-through double";
    private static final String UNDERLINE = "underline";
    private static final String DOUBLE = "double";

    public static void handleParagraphStyle(WordprocessingMLPackage wordMLPackage, PPr pPr, Styleable blockBox, Style s) {
        Style newStyle = s;
        if (isHeading(blockBox)) {
            PropertyResolver propertyResolver = getPropertyResolver(wordMLPackage);
            if (null != propertyResolver) {
                PPr effectivePPr = propertyResolver.getEffectivePPr(s.getStyleId());
                // if it is heading and the style has numPr, copy a new style and remove the numPr, then set the new style to heading.
                if (null != effectivePPr && effectivePPr.getNumPr() != null) {
                    newStyle = Context.getWmlObjectFactory().createStyle();
                    newStyle.setPPr(StyleUtil.apply(effectivePPr, newStyle.getPPr()));
                    newStyle.getPPr().setNumPr(null);
                    newStyle.setRPr(StyleUtil.apply(s.getRPr(), newStyle.getRPr()));
                    String styleId = s.getStyleId() + "-custom";
                    Style.Name name = Context.getWmlObjectFactory().createStyleName();
                    name.setVal(styleId);
                    newStyle.setName(name);
                    newStyle.setStyleId(styleId);
                    // set the based on as same as original one, so that they will be same heading level
                    newStyle.setBasedOn(s.getBasedOn());
                    propertyResolver.activateStyle(newStyle);
                }
            }
        }
        setParagraphStyle(pPr, newStyle);
    }

    public static PropertyResolver getPropertyResolver(WordprocessingMLPackage wordMLPackage) {
        if (wordMLPackage == null || wordMLPackage.getMainDocumentPart() == null ||
                wordMLPackage.getMainDocumentPart().getPropertyResolver() == null) {
            return null;
        }
        return wordMLPackage.getMainDocumentPart().getPropertyResolver();
    }

    public static boolean isHeading(Styleable blockBox) {

        if (blockBox.getElement() == null) return false;

        String elName = blockBox.getElement().getLocalName();

        return (("h1").equals(elName)
                || ("h2").equals(elName)
                || ("h3").equals(elName)
                || ("h4").equals(elName)
                || ("h5").equals(elName)
                || ("h6").equals(elName));
    }

    public static void setParagraphStyle(PPr pPr, Style s) {
        PPrBase.PStyle pStyle = Context.getWmlObjectFactory().createPPrBasePStyle();
        pPr.setPStyle(pStyle);
        pStyle.setVal(s.getStyleId());
    }

    public static void setTextDecorationProperty(RPr rPr, CSSValue value) {
        String[] subValues = value.getCssText().split(";");
        // the low index has high priority, so we loop from last to first item
        for (int i = subValues.length - 1; i >= 0; i--) {
            String cssText = subValues[i];
            if (cssText.contains(LINE_THROUGH) && cssText.contains(DOUBLE)) {
                PropertyValue val = new PropertyValue(CSSPrimitiveValue.CSS_STRING, DOUBLE_LINE_THROUGH, DOUBLE_LINE_THROUGH);
                DStrike dstrike = new DStrike(new DomCssValueAdaptor(val));
                dstrike.set(rPr);
            } else if (cssText.contains(LINE_THROUGH)) {
                PropertyValue val = new PropertyValue(CSSPrimitiveValue.CSS_STRING, LINE_THROUGH, LINE_THROUGH);
                Strike strike = new Strike(new DomCssValueAdaptor(val));
                strike.set(rPr);
            }
            if (cssText.contains(UNDERLINE)) {
                cssText = cssText.replace("[" + LINE_THROUGH + "]", "").trim();
                cssText = cssText.replace(LINE_THROUGH, "").trim();
                PropertyValue val = new PropertyValue(CSSPrimitiveValue.CSS_STRING, cssText, cssText);
                Underline underline = new Underline(new DomCssValueAdaptor(val));
                underline.set(rPr);
            }
        }
    }

    public static void setSmallCapsProperty(RPr rPr, PropertyValue cssValue) {
        String cssText = cssValue.getCssText();
        if (cssText.equals("small-caps")) {
            rPr.setSmallCaps(new BooleanDefaultTrue());
        }
    }

    public static void setAllCapsProperty(RPr rPr, PropertyValue cssValue) {
        String cssText = cssValue.getCssText();
        if (cssText.equals("uppercase")) {
            rPr.setCaps(new BooleanDefaultTrue());
        }
    }

    public static boolean handleSpecialStyle(String cssName, PropertyValue cssValue, RPr rPr) {
        // @Fixed by longyg @2023.4.18:
        // for text-decoration, handle it separately, because it may contain strike and underline
        if (cssName.equals("text-decoration")) {
            setTextDecorationProperty(rPr, new DomCssValueAdaptor(cssValue));
            return true;
        }

        // @Fixed by longyg @2023.5.22:
        // support small caps
        if (cssName.equals("font-variant")) {
            setSmallCapsProperty(rPr, cssValue);
            return true;
        }
        // @Fixed by longyg @2023.5.22:
        // support all caps
        if (cssName.equals("text-transform")) {
            setAllCapsProperty(rPr, cssValue);
            return true;
        }
        return false;
    }
}
