package org.docx4j.convert.in.xhtml.utils;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.css.parser.CSSParser;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.css.style.DerivedValue;
import com.openhtmltopdf.css.style.FSDerivedValue;
import com.openhtmltopdf.css.style.derived.*;
import org.docx4j.convert.in.xhtml.PPrCleanser;
import org.docx4j.convert.in.xhtml.RPrCleanser;
import org.docx4j.convert.in.xhtml.StyleHelper;
import org.docx4j.model.properties.Property;
import org.docx4j.model.properties.run.Bold;
import org.docx4j.model.properties.run.Italics;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.css.CSSPrimitiveValue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

import static org.docx4j.convert.in.xhtml.utils.Constants.*;

/**
 * @author longyg
 */
public class Utils {
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static Map<String, PropertyValue> getCascadedProperties(CalculatedStyle cs) {
        Map<String, PropertyValue> cssMap = new HashMap<>();
        FSDerivedValue[] derivedValues = cs.getderivedValuesById();
        for (int i = 0; i < derivedValues.length; i++) {
            CSSName name = CSSName.getByID(i);
            if (name.toString().startsWith("-fs")) continue;

            // walks parents as necessary to get the value
            FSDerivedValue val = cs.valueByName(name);

            // An IdentValue represents a string that you can assign to a CSS property,
            // where the string is one of several enumerated values.
            // font-size could be a IdentValue (e.g. medium) or a LengthValue (eg 12px)
            if (val == null) {
                log.debug("Skipping {} .. (null value)", name);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("{}: {} = {}", val.getClass().getName(), name, val.asString());
                }
                if (val instanceof IdentValue) {
                    // Workaround for docx4j < 8.3, which doesn't handle start|end
                    if (name.toString().equals("text-align")
                            && (val.asString().equals("start")
                            || val.asString().equals("end"))) {

                        PropertyValue val2;
                        if (val.asString().equals("start")) {
                            // Not bidi aware; assume ltr
                            val2 = new PropertyValue(CSSPrimitiveValue.CSS_IDENT, "left", "left");
                        } else {
                            val2 = new PropertyValue(CSSPrimitiveValue.CSS_IDENT, "right", "right");
                        }
                        cssMap.put(name.toString(), val2);

                    } else {

                        PropertyValue val2 = new PropertyValue((IdentValue) val);
                        cssMap.put(name.toString(), val2);
                    }

                } else if (val instanceof ColorValue) {
                    PropertyValue val2 = new PropertyValue(val.asColor());
                    cssMap.put(name.toString(), val2);

                } else if (val instanceof LengthValue) {

                    PropertyValue val2 = new PropertyValue(getLengthPrimitiveType(val), val.asFloat(), val.asString());
                    cssMap.put(name.toString(), val2);

                } else if (val instanceof NumberValue) {

                    PropertyValue val2 = new PropertyValue(((NumberValue) val).getCssSacUnitType(), val.asFloat(), val.asString());
                    cssMap.put(name.toString(), val2);

                } else if (val instanceof StringValue) {

                    PropertyValue val2 = new PropertyValue(((StringValue) val).getCssSacUnitType(), val.asString(), val.asString());
                    cssMap.put(name.toString(), val2);

                } else if (val instanceof ListValue) {

                    PropertyValue val2 = new PropertyValue(((ListValue) val).getValues());
                    cssMap.put(name.toString(), val2);

                } else if (val instanceof CountersValue) {

                    boolean unused = false;
                    PropertyValue val2 = new PropertyValue(((CountersValue) val).getValues(), unused);
                    cssMap.put(name.toString(), val2);

                } else if (val instanceof FunctionValue) {

                    PropertyValue val2 = new PropertyValue(((FunctionValue) val).getFunction());
                    cssMap.put(name.toString(), val2);

                } else if (val instanceof DerivedValue) {

                    // We should've handled all known types of abstract class DerivedValue above!
                    log.warn("TODO handle DerivedValue type {} with name  {} = {}", val.getClass().getName(), name, val.asString());
                    PropertyValue val2 = new PropertyValue(((DerivedValue) val).getCssSacUnitType(), val.asString(), val.asString());
                    cssMap.put(name.toString(), val2);

                } else {
                    log.warn("TODO Skipping {} .. {}", name, val.getClass().getName());
                }
            }
        }
        return cssMap;
    }

    public static short getLengthPrimitiveType(FSDerivedValue val) {
        if (val instanceof LengthValue) {
            return ((LengthValue) val).getLengthPrimitiveType();
        } else {
            throw new RuntimeException("Unexpected type " + val.getClass().getName());
        }
    }

    public static boolean colorEqual(Color color1, Color color2) {
        if (color1 == color2) return true;
        if (color1 != null && color2 != null) {
            String val1 = color1.getVal();
            String val2 = color2.getVal();
            if (Objects.equals(val1, val2)) return true;
            if (val1 != null) {
                return val1.equalsIgnoreCase(val2);
            }
        }
        return false;
    }

    public static boolean areEqual(BooleanDefaultTrue b1,
                                   BooleanDefaultTrue b2) {
        if (b1 == b2) return true;
        if (b1 != null && b2 != null) {
            return b1.isVal() == b2.isVal();
        }
        return false;
    }

    public static boolean isBoldFalse(Property prop) {
        if (prop instanceof Bold) {
            Bold bold = (Bold) prop;
            return isFalseBdt(bold.getObject());
        }
        return false;
    }

    public static boolean isItalicsFalse(Property prop) {
        if (prop instanceof Italics) {
            Italics italics = (Italics) prop;
            return isFalseBdt(italics.getObject());
        }
        return false;
    }

    public static boolean isFalseBdt(Object bdtObj) {
        if (bdtObj instanceof BooleanDefaultTrue) {
            BooleanDefaultTrue bdt = (BooleanDefaultTrue) bdtObj;
            return !bdt.isVal();
        }
        return false;
    }

    public static void handleCssMap(Map<String, PropertyValue> cssMap, Map<String, PropertyValue> selfCssMap) {
        cssMap.remove(FONT_FAMILY);
        if (selfCssMap.containsKey(FONT_FAMILY)) {
            cssMap.put(FONT_FAMILY, selfCssMap.get(FONT_FAMILY));
        }

        for (String key : selfCssMap.keySet()) {
            if (StyleHelper.isFontStyle(key)) {
                cssMap.put(key, selfCssMap.get(key));
            }
        }
        if (selfCssMap.containsKey(TEXT_DECORATION)) {
            cssMap.put(TEXT_DECORATION, selfCssMap.get(TEXT_DECORATION));
        }
        if (selfCssMap.containsKey(BACKGROUND_COLOR)) {
            cssMap.put(BACKGROUND_COLOR, selfCssMap.get(BACKGROUND_COLOR));
        }
    }

    public static void removePRedundant(WordprocessingMLPackage wordMLPackage, PPr pPr) {
        Style defaultParagraphStyle = wordMLPackage.getMainDocumentPart().getStyleDefinitionsPart().getDefaultParagraphStyle();
        if (null != defaultParagraphStyle) {
            PPr defaultPPr = defaultParagraphStyle.getPPr();
            if (null != defaultPPr) {
                PPrCleanser.removeRedundantProperties(defaultPPr, pPr);
            }
        }
        DocDefaults docDefaults = wordMLPackage.getMainDocumentPart().getStyleDefinitionsPart().getJaxbElement().getDocDefaults();
        if (null != docDefaults) {
            DocDefaults.PPrDefault pPrDefault = docDefaults.getPPrDefault();
            if (null != pPrDefault) {
                PPr docDefaultPPr = pPrDefault.getPPr();
                if (null != docDefaultPPr) {
                    PPrCleanser.removeRedundantProperties(docDefaultPPr, pPr);
                }
            }
        }
    }

    public static void removeRunRedundant(WordprocessingMLPackage wordMLPackage, RPr rPr) {
        Style defaultParagraphStyle = wordMLPackage.getMainDocumentPart().getStyleDefinitionsPart().getDefaultParagraphStyle();
        if (null != defaultParagraphStyle) {
            RPr defaultRPr = defaultParagraphStyle.getRPr();
            if (null != defaultRPr) {
                RPrCleanser.removeRedundantProperties(defaultRPr, rPr);
            }
        }
        Style defaultCharacterStyle = wordMLPackage.getMainDocumentPart().getStyleDefinitionsPart().getDefaultCharacterStyle();
        if (null != defaultCharacterStyle) {
            RPr defaultCRPr = defaultCharacterStyle.getRPr();
            if (null != defaultCRPr) {
                RPrCleanser.removeRedundantProperties(defaultCRPr, rPr);
            }
        }
        DocDefaults docDefaults = wordMLPackage.getMainDocumentPart().getStyleDefinitionsPart().getJaxbElement().getDocDefaults();
        if (null != docDefaults) {
            DocDefaults.RPrDefault rPrDefault = docDefaults.getRPrDefault();
            if (null != rPrDefault) {
                RPr docDefaultRPr = rPrDefault.getRPr();
                if (null != docDefaultRPr) {
                    RPrCleanser.removeRedundantProperties(docDefaultRPr, rPr);
                }
            }
        }
    }

    public static String extractStyleValue(String styles, String styleName) {
        if (!styles.contains(styleName)) return null;
        int startIndex = styles.indexOf(styleName);
        int endIndex = styles.length();
        int semicolonIndex = styles.indexOf(";", startIndex);
        if (semicolonIndex > -1) {
            endIndex = semicolonIndex;
        }
        String rule = styles.substring(startIndex, endIndex);
        return rule.substring(rule.indexOf(":") + 1).trim();
    }

    public static void addSupportForMarkerPseudo() {
        try {
            Field supportedPseudoElements = CSSParser.class.getDeclaredField("SUPPORTED_PSEUDO_ELEMENTS");
            if (null == supportedPseudoElements) return;

            supportedPseudoElements.setAccessible(true);
            Object elements = supportedPseudoElements.get(null);
            if (!(elements instanceof HashSet)) return;
            ((HashSet<String>) elements).add("marker");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
