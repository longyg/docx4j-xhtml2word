package org.docx4j.convert.in.xhtml;

import com.openhtmltopdf.context.StylesheetFactoryImpl;
import com.openhtmltopdf.css.extend.StylesheetFactory;
import com.openhtmltopdf.css.parser.CSSPrimitiveValue;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.sheet.Ruleset;
import com.openhtmltopdf.css.sheet.StylesheetInfo;
import com.openhtmltopdf.layout.Styleable;
import com.openhtmltopdf.newtable.TableCellBox;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author longyg
 */
public class StyleHelper {
    private final XHTMLImporterImpl importer;
    private final StylesheetFactory stylesheetFactory;

    private static final String WHITE_SPACE = "white-space";
    private static final String PRE_WRAP = "pre-wrap";
    private static final String FONT_FAMILY = "font-family";

    private final Map<Node, Map<String, PropertyValue>> elementStyleCache = new ConcurrentHashMap<>();
    private final Map<Styleable, Map<String, PropertyValue>> boxStyleCache = new ConcurrentHashMap<>();
    private final Map<Styleable, String> boxClassCache = new ConcurrentHashMap<>();
    private final Map<Node, String> elementClassCache = new ConcurrentHashMap<>();

    private static final String EMPTY = "";

    private static final List<String> FONT_STYLE_NAMES = Arrays.asList(
            "data-ascii",
            "data-asciiTheme",
            "data-eastAsia",
            "data-eastAsiaTheme",
            "data-hAnsi",
            "data-hAnsiTheme",
            "data-cs",
            "data-cstheme",
            "data-hint");

    public StyleHelper(XHTMLImporterImpl importer) {
        this.importer = importer;
        stylesheetFactory = new StylesheetFactoryImpl(importer.getRenderer().getDocx4jUserAgent());
    }

    public Map<String, PropertyValue> getBlockBoxStyles(Box box, Box rootBox) {
        Map<String, PropertyValue> styles = new HashMap<>();
        if (null == box) return styles;

        // try to get from cache
        if (boxStyleCache.containsKey(box)) {
            return boxStyleCache.get(box);
        }

        styles = getBoxStyles(box);
        if (box != rootBox) { // if not reach rootBox, get parent styles
            Map<String, PropertyValue> parentBoxStyles = getBlockBoxStyles(box.getParent(), rootBox);
            copyNotExistStyles(parentBoxStyles, styles);
        }
        // add to cache
        boxStyleCache.putIfAbsent(box, styles);
        return styles;
    }

    public Map<String, PropertyValue> getInlineBoxStyles(InlineBox inlineBox, Box containingBox) {
        Map<String, PropertyValue> styles = new HashMap<>();
        if (null == inlineBox) return styles;
        // try to get from box style cache
        if (boxStyleCache.containsKey(inlineBox)) {
            return boxStyleCache.get(inlineBox);
        }
        // there is a special case that if containingBox is TableCellBox,
        // then it's styles should also be added for the inline box
        boolean shouldAddContainingBoxStyle = containingBox instanceof TableCellBox;
        return getElementStyles(inlineBox.getElement(), containingBox, shouldAddContainingBoxStyle);
    }

    private Map<String, PropertyValue> getElementStyles(Element element, Box containingBox, boolean shouldAddContainingBoxStyle) {
        Map<String, PropertyValue> styles = new HashMap<>();
        if (null == element) {
            if (shouldAddContainingBoxStyle) {
                return getBoxStyles(containingBox);
            } else {
                return styles;
            }
        }

        // try to get from cache
        if (elementStyleCache.containsKey(element)) {
            return elementStyleCache.get(element);
        }

        styles = getElementStyles(element);

        Node parentNode = element.getParentNode();
        if (null != parentNode && parentNode.getNodeType() == Node.ELEMENT_NODE &&
                ((shouldAddContainingBoxStyle && element != containingBox)
                        || (!shouldAddContainingBoxStyle && parentNode != containingBox))) {
            Map<String, PropertyValue> parentBoxStyles = getElementStyles((Element) parentNode, containingBox, shouldAddContainingBoxStyle);
            copyNotExistStyles(parentBoxStyles, styles);
        }
        // add to cache
        elementStyleCache.putIfAbsent(element, styles);
        return styles;
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
//            if ((WHITE_SPACE.equals(cssName) &&
//                    PRE_WRAP.equals(pd.getValue().getCssText())) ||
//                    cssName.contains(FONT_FAMILY)) return;
            styles.put(cssName, (PropertyValue) pd.getValue());
        });
        addFontStyle(styles, element);
        return styles;
    }

    private void addFontStyle(Map<String, PropertyValue> styles, Element element) {
        NamedNodeMap attributes = element.getAttributes();
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
        if (null == box) return EMPTY;
        if (boxClassCache.containsKey(box)) {
            return boxClassCache.get(box);
        }
        String cName = getBoxPrimaryClass(box);
        if (EMPTY.equals(cName) && box != rootBox) {
            cName = getBlockBoxPrimaryClass(box.getParent(), rootBox);
        }
        boxClassCache.putIfAbsent(box, cName);
        return cName;
    }

    public String getInlineBoxPrimaryClass(InlineBox inlineBox, Box containingBox) {
        if (null == inlineBox) return null;
        // try get from box class cache
        if (boxClassCache.containsKey(inlineBox)) {
            return boxClassCache.get(inlineBox);
        }
        return getElementPrimaryClass(inlineBox.getElement(), containingBox);
    }

    private String getElementPrimaryClass(Element element, Box containingBox) {
        if (null == element) return EMPTY;
        // try get from cache
        if (elementClassCache.containsKey(element)) {
            return elementClassCache.get(element);
        }

        String cName = getElementPrimaryClass(element);
        if (EMPTY.equals(cName)) {
            Node parentNode = element.getParentNode();
            if (null != parentNode && parentNode.getNodeType() == Node.ELEMENT_NODE
                    && parentNode != containingBox.getElement()) {
                cName = getElementPrimaryClass((Element) parentNode, containingBox);
            }
        }
        // add to cache
        elementClassCache.putIfAbsent(element, cName);
        return cName;
    }

    public String getBoxPrimaryClass(Styleable box) {
        return getElementPrimaryClass(box.getElement());
    }

    private String getElementPrimaryClass(Element element) {
        if (null == element) return EMPTY;
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
        return EMPTY;
    }
}
