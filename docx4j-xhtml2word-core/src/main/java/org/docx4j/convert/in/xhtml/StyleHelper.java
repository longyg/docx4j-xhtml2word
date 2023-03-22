package org.docx4j.convert.in.xhtml;

import com.openhtmltopdf.context.StylesheetFactoryImpl;
import com.openhtmltopdf.css.extend.StylesheetFactory;
import com.openhtmltopdf.css.parser.CSSPrimitiveValue;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.sheet.Ruleset;
import com.openhtmltopdf.css.sheet.StylesheetInfo;
import com.openhtmltopdf.layout.Styleable;
import org.docx4j.wml.Style;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author longyg
 */
public class StyleHelper {
    private final XHTMLImporterImpl importer;
    private final StylesheetFactory stylesheetFactory;

    private static final String WHITE_SPACE = "white-space";
    private static final String PRE_WRAP = "pre-wrap";
    private static final String FONT_FAMILY = "font-family";

    private static final List<String> FONT_STYLE_NAMES = Arrays.asList("ascii", "asciiTheme", "eastAsia", "eastAsiaTheme", "hAnsi", "hAnsiTheme", "cs", "cstheme", "hint");

    public StyleHelper(XHTMLImporterImpl importer) {
        this.importer = importer;
        stylesheetFactory = new StylesheetFactoryImpl(importer.getRenderer().getDocx4jUserAgent());
    }

    public Map<String, PropertyValue> getStyles(Styleable box) {
        Map<String, PropertyValue> styles = new HashMap<>();
        if (null == box || null == box.getElement()) return styles;
        Ruleset ruleset = stylesheetFactory.parseStyleDeclaration(
                StylesheetInfo.AUTHOR, box.getElement().getAttribute("style"));
        ruleset.getPropertyDeclarations().forEach(pd -> {
            String cssName = pd.getCSSName().toString();
            // white-space and font styles are handled separately, so do not add it
            if ((WHITE_SPACE.equals(cssName) &&
                    PRE_WRAP.equals(pd.getValue().getCssText())) ||
                    cssName.contains(FONT_FAMILY)) return;
            styles.put(cssName, (PropertyValue) pd.getValue());
        });
        addFontStyle(styles, box);
        return styles;
    }

    private void addFontStyle(Map<String, PropertyValue> styles, Styleable box) {
        NamedNodeMap attributes = box.getElement().getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node item = attributes.item(i);
            String nodeName = item.getNodeName();
            if (isFontStyle(nodeName)) {
                styles.put(nodeName, new PropertyValue(
                        CSSPrimitiveValue.CSS_STRING,
                        item.getNodeValue(),
                        item.getNodeValue()));
            }
        }
    }

    public static boolean isFontStyle(String styleName) {
        return FONT_STYLE_NAMES.contains(styleName);
    }

    public String getPrimaryClass(Styleable box) {
        if (null == box || null == box.getElement()) return null;
        String cssClass = box.getElement().getAttribute("class").trim();
        if (!cssClass.equals("")) {
            int pos = cssClass.indexOf(" ");
            if (pos > -1) {
                cssClass = cssClass.substring(0, pos);
            }
            Style s = importer.stylesByID.get(cssClass);
            // do not add default class, as default class do not need to generate to word for paragraph
            // need to check for other parts
            if (null != s && !s.isDefault()) {
                return cssClass;
            }
        }
        return null;
    }
}
