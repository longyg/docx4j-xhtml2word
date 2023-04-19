package org.docx4j.convert.in.xhtml;

import com.openhtmltopdf.context.StylesheetFactoryImpl;
import com.openhtmltopdf.css.extend.StylesheetFactory;
import com.openhtmltopdf.css.newmatch.Selector;
import com.openhtmltopdf.css.parser.CSSPrimitiveValue;
import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.css.sheet.PropertyDeclaration;
import com.openhtmltopdf.css.sheet.Ruleset;
import com.openhtmltopdf.css.sheet.Stylesheet;
import com.openhtmltopdf.css.sheet.StylesheetInfo;
import com.openhtmltopdf.extend.NamespaceHandler;
import com.openhtmltopdf.extend.UserAgentCallback;
import com.openhtmltopdf.layout.Styleable;
import com.openhtmltopdf.newtable.TableCellBox;
import com.openhtmltopdf.render.BlockBox;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.InlineBox;
import org.docx4j.wml.Style;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.StringReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author longyg
 */
public class StyleHelper {
    private final XHTMLImporterImpl importer;
    private StylesheetFactory stylesheetFactory;

    private UserAgentCallback uac;

    private static final String WHITE_SPACE = "white-space";
    private static final String TEXT_DECORATION = "text-decoration";
    private static final String PRE_WRAP = "pre-wrap";
    private static final String FONT_FAMILY = "font-family";
    private static final List<String> TABLE_CELL_DERIVED_STYLES = List.of("text-align");
    private static final List<String> PARAGRAPH_INVALID_STYLES = List.of("color", "font-size", "font-weight");
    private final Map<Node, Map<String, PropertyValue>> elementStyleCache = new ConcurrentHashMap<>();
    private final Map<Styleable, Map<String, PropertyValue>> boxStyleCache = new ConcurrentHashMap<>();
    private final Map<Styleable, String> boxClassCache = new ConcurrentHashMap<>();
    private final Map<Node, String> elementClassCache = new ConcurrentHashMap<>();

    private Map<String, List<PropertyDeclaration>> docRuleSets;

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
    }

    public UserAgentCallback getUac() {
        if (null != uac) return uac;
        uac = importer.getRenderer().getDocx4jUserAgent();
        return uac;
    }

    public StylesheetFactory getStylesheetFactory() {
        if (null != stylesheetFactory) return stylesheetFactory;
        stylesheetFactory = new StylesheetFactoryImpl(uac);
        return stylesheetFactory;
    }

    public Document getDocument() {
        return this.importer.getRenderer().getDocument();
    }

    public NamespaceHandler getNamespaceHandler() {
        return this.importer.getRenderer().getSharedContext()
                .getNamespaceHandler();
    }

    private Map<String, List<PropertyDeclaration>> getDocumentRulesets() {
        if (null != docRuleSets) return docRuleSets;

        Map<String, List<PropertyDeclaration>> rulesets = new HashMap<>();
        Document document = getDocument();
        if (null == document) return rulesets;
        StylesheetInfo[] refs = getNamespaceHandler().getStylesheets(document);

        List<StylesheetInfo> infos = new ArrayList<>();
        int inlineStyleCount = 0;
        if (refs != null) {
            for (StylesheetInfo ref : refs) {
                String uri;

                if (!ref.isInline()) {
                    uri = getUac().resolveURI(ref.getUri());
                    ref.setUri(uri);
                } else {
                    ref.setUri(getUac().getBaseURL() + "#inline_style_" + (++inlineStyleCount));
                    Stylesheet sheet = getStylesheetFactory().parse(
                            new StringReader(ref.getContent()), ref);
                    ref.setStylesheet(sheet);
                    ref.setUri(null);
                }
            }
            infos.addAll(Arrays.asList(refs));
        }

        for (StylesheetInfo info : infos) {
            if (null == info.getStylesheet()) continue;
            for (Object content : info.getStylesheet().getContents()) {
                if (content instanceof Ruleset) {
                    Ruleset ruleset = (Ruleset) content;
                    Selector selector = ruleset.getFSSelectors().get(0);
                    rulesets.putIfAbsent(selector.toString().trim(), ruleset.getPropertyDeclarations());
                }
            }
        }

        docRuleSets = rulesets;
        return docRuleSets;
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
        styles = getElementStyles(inlineBox.getElement(), containingBox);
        addParagraphInvalidStylesFromContainingBox(styles, containingBox);
        return styles;
    }

    // some styles like 'color' is not valid for paragraph in word, we need to add those styles to run level
    private void addParagraphInvalidStylesFromContainingBox(Map<String, PropertyValue> styles, Box containingBox) {
        if (boxStyleCache.containsKey(containingBox)) {
            Map<String, PropertyValue> stylesMap = boxStyleCache.get(containingBox);
            stylesMap.forEach((k, v) -> {
                if (PARAGRAPH_INVALID_STYLES.contains(k) && !styles.containsKey(k)) {
                    styles.put(k, v);
                }
            });
        }
        if (hasUndefinedClass(containingBox)) {
            String cssClass = containingBox.getElement().getAttribute("class").trim();
            Map<String, List<PropertyDeclaration>> documentRulesets = getDocumentRulesets();
            if (!"".equals(cssClass)) {
                String[] classes = cssClass.split("\\s+");
                for (String aClass : classes) {
                    String cName = "." + aClass.trim();
                    if (documentRulesets.containsKey(cName)) {
                        documentRulesets.get(cName).forEach(pd -> {
                            String cssName = pd.getCSSName().toString();
                            if (PARAGRAPH_INVALID_STYLES.contains(cssName) && !styles.containsKey(cssName)) {
                                styles.put(cssName, (PropertyValue) pd.getValue());
                            }
                        });
                    }
                }
            }
        }
    }

    private Map<String, PropertyValue> getElementStyles(Element element, Box containingBox) {
        Map<String, PropertyValue> styles = new HashMap<>();
        if (null == element) return styles;

        // try to get from cache
        if (elementStyleCache.containsKey(element)) {
            return elementStyleCache.get(element);
        }

        styles = getElementStyles(element);

        Node parentNode = element.getParentNode();
        if (null != parentNode && parentNode.getNodeType() == Node.ELEMENT_NODE
                && parentNode != containingBox.getElement()) {
            Map<String, PropertyValue> parentBoxStyles = getElementStyles((Element) parentNode, containingBox);
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
        Ruleset ruleset = getStylesheetFactory().parseStyleDeclaration(
                StylesheetInfo.AUTHOR, element.getAttribute("style"));
        ruleset.getPropertyDeclarations().forEach(pd -> {
            String cssName = pd.getCSSName().toString();
            styles.put(cssName, (PropertyValue) pd.getValue());
        });
        addTextDecorationStyle(styles, element);
        addFontStyle(styles, element);
        return styles;
    }

    /**
     * For text-decoration with multiple values, the underling library is not able to parse.
     * So here we parse it by ourselves.
     */
    private void addTextDecorationStyle(Map<String, PropertyValue> styles, Element element) {
        String style = element.getAttribute("style");
        if (!style.contains(TEXT_DECORATION)) return;

        int startIndex = style.indexOf(TEXT_DECORATION);
        int endIndex = style.length();
        int semicolonIndex = style.indexOf(";", startIndex);
        if (semicolonIndex > -1) {
            endIndex = semicolonIndex;
        }
        String td = style.substring(startIndex, endIndex);
        String tdValue = td.substring(td.indexOf(":")+1).trim();
        String[] values = tdValue.split("\\s+");
        // if there is only one value, it is already parsed by underling library, so we don't parse it
        if (values.length == 1) return;

        PropertyValue val = new PropertyValue(org.w3c.dom.css.CSSPrimitiveValue.CSS_STRING, tdValue, tdValue);
        styles.put(TEXT_DECORATION, val);
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

    public void addCommonStyle(BlockBox blockBox, Map<String, PropertyValue> blockCssMap) {
        Element element = blockBox.getElement();
        if (null == element) return;
        String cssClass = element.getAttribute("class").trim();
        Map<String, List<PropertyDeclaration>> documentRulesets = getDocumentRulesets();
        if (!"".equals(cssClass)) {
            String[] classes = cssClass.split("\\s+");
            for (String aClass : classes) {
                String cName = "." + aClass.trim();
                addClassStyle(blockCssMap, documentRulesets, cName);
            }
        }
    }

    private void addClassStyle(Map<String, PropertyValue> blockCssMap, Map<String,
            List<PropertyDeclaration>> documentRulesets, String className) {
        if (!documentRulesets.containsKey(className)) return;
        documentRulesets.get(className).forEach(pd -> {
            String cssName = pd.getCSSName().toString();
            if (!blockCssMap.containsKey(cssName)) {
                blockCssMap.put(cssName, (PropertyValue) pd.getValue());
            }
        });
    }

    public boolean hasUndefinedClass(Box box) {
        Element element = box.getElement();
        if (null == element) return false;
        String cssClass = element.getAttribute("class").trim();
        if (!cssClass.equals("")) {
            int pos = cssClass.indexOf(" ");
            if (pos > -1) {
                cssClass = cssClass.substring(0, pos);
            }
            Style s = importer.stylesByID.get(cssClass);
            return null == s;
        }
        return false;
    }

    public Map<String, PropertyValue> getTableCellDerivedStyles(TableCellBox tableCellBox) {
        Map<String, PropertyValue> result = new HashMap<>();
        Map<String, PropertyValue> styles = getBoxStyles(tableCellBox);
        styles.forEach((k, v) -> {
            if (TABLE_CELL_DERIVED_STYLES.contains(k) && !result.containsKey(k)) {
                result.put(k, v);
            }
        });
        return result;
    }
}
