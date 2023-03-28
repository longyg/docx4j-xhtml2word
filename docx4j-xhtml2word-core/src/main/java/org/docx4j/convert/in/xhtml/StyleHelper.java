package org.docx4j.convert.in.xhtml;

import com.openhtmltopdf.context.StylesheetFactoryImpl;
import com.openhtmltopdf.css.extend.StylesheetFactory;
import com.openhtmltopdf.css.parser.CSSPrimitiveValue;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.sheet.Ruleset;
import com.openhtmltopdf.css.sheet.StylesheetInfo;
import com.openhtmltopdf.layout.Styleable;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.InlineBox;
import org.docx4j.wml.Style;
import org.w3c.dom.Element;
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

    public Map<String, PropertyValue> getBlockBoxStyles(Box box, Box rootBox) {
        Map<String, PropertyValue> styles = getBoxStyles(box);
        setParentBoxStyles(box, rootBox, styles);
        return styles;
    }

    private void setParentBoxStyles(Box box, Box rootBox, Map<String, PropertyValue> styles) {
        if (null == box || null == box.getParent() || box == rootBox) return;
        Box curBox = box.getParent();
        Map<String, PropertyValue> parentStyles = getBoxStyles(curBox);
        copyNotExistStyles(parentStyles, styles);
        setParentBoxStyles(curBox, rootBox, styles);
    }

    public Map<String, PropertyValue> getInlineBoxStyles(InlineBox inlineBox, Box containingBox) {
        Map<String, PropertyValue> styles = getBoxStyles(inlineBox);
        setParentElementStyles(inlineBox.getElement(), containingBox, styles);
        return styles;
    }

    private void setParentElementStyles(Node node, Box containingBox, Map<String, PropertyValue> styles) {
        if (null == node) return;
        Node curNode = node.getParentNode();
        if (null == curNode || curNode == containingBox.getElement()) return;
        if (curNode.getNodeType() == Node.ELEMENT_NODE) {
            Map<String, PropertyValue> parentStyles = getElementStyles((Element) curNode);
            copyNotExistStyles(parentStyles, styles);
        }
        setParentElementStyles(curNode, containingBox, styles);
    }

    private void copyNotExistStyles(Map<String, PropertyValue> source, Map<String, PropertyValue> dest) {
        source.forEach((name, value) -> {
            if (!dest.containsKey(name)) {
                dest.put(name, value);
            }
        });
    }

    private Map<String, PropertyValue> getBoxStyles(Styleable box) {
        return getElementStyles(box.getElement());
    }

    private Map<String, PropertyValue> getElementStyles(Element element) {
        Map<String, PropertyValue> styles = new HashMap<>();
        if (null == element) return styles;
        Ruleset ruleset = stylesheetFactory.parseStyleDeclaration(
                StylesheetInfo.AUTHOR, element.getAttribute("style"));
        ruleset.getPropertyDeclarations().forEach(pd -> {
            String cssName = pd.getCSSName().toString();
            // white-space and font styles are handled separately, so do not add it
            if ((WHITE_SPACE.equals(cssName) &&
                    PRE_WRAP.equals(pd.getValue().getCssText())) ||
                    cssName.contains(FONT_FAMILY)) return;
            styles.put(cssName, (PropertyValue) pd.getValue());
        });
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

    public String getBlockBoxPrimaryClass(Box box, Box rootBox) {
        String cName = getBoxPrimaryClass(box);
        if (null != cName) return cName;
        return getParentBlockPrimaryClass(box, rootBox);
    }

    public String getParentBlockPrimaryClass(Box box, Box rootBox) {
        if (null == box || null == box.getParent() || box == rootBox) return null;
        Box curBox = box.getParent();
        String cName = getBoxPrimaryClass(curBox);
        if (null != cName) return cName;
        return getParentBlockPrimaryClass(curBox, rootBox);
    }

    public String getInlineBoxPrimaryClass(InlineBox inlineBox, Box containingBox) {
        String cName = getBoxPrimaryClass(inlineBox);
        if (null != cName) return cName;
        return getParentElementPrimaryClass(inlineBox.getElement(), containingBox);
    }

    private String getParentElementPrimaryClass(Node node, Box containingBox) {
        if (null == node) return null;
        Node curNode = node.getParentNode();
        if (null == curNode || curNode == containingBox.getElement()) return null;
        if (curNode.getNodeType() == Node.ELEMENT_NODE) {
            String cName = getElementPrimaryClass((Element) curNode);
            if (null != cName) return cName;
            return getParentElementPrimaryClass(curNode, containingBox);
        }
        return null;
    }

    public String getBoxPrimaryClass(Styleable box) {
        return getElementPrimaryClass(box.getElement());
    }

    private String getElementPrimaryClass(Element element) {
        if (null == element) return null;
        String cssClass = element.getAttribute("class").trim();
        if (!cssClass.equals("")) {
            // only get the first one as primary class
            int pos = cssClass.indexOf(" ");
            if (pos > -1) {
                cssClass = cssClass.substring(0, pos);
            }
            // only if there is style defined in style.xml of word
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
