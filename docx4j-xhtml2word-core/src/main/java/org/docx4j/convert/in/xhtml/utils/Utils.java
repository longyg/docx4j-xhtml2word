package org.docx4j.convert.in.xhtml.utils;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.style.CalculatedStyle;
import com.openhtmltopdf.css.style.DerivedValue;
import com.openhtmltopdf.css.style.FSDerivedValue;
import com.openhtmltopdf.css.style.derived.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.css.CSSPrimitiveValue;

import java.util.HashMap;
import java.util.Map;

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
}
