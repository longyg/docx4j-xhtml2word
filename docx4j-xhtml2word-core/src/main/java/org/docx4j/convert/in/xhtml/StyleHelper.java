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
import com.openhtmltopdf.newtable.TableBox;
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
    /**
     * the styles which should be added to the cell content from cell level
     */
    private static final List<String> TABLE_CELL_DERIVED_STYLES = List.of("text-align", "margin-top", "margin-bottom");
    /**
     * the styles which are not applicable for paragraph in word, they need to be added to run level
     */
    private static final List<String> PARAGRAPH_INVALID_STYLES = List.of("color", "font-size", "font-weight");
    /**
     * the elements which should enable the agent default css
     */
    private static final List<String> ENABLE_AGENT_DEFAULT_ELEMENT = List.of("s", "strong", "em", "p", "span", "u");
    private final Map<Node, Map<String, PropertyValue>> elementStyleCache = new ConcurrentHashMap<>();
    private final Map<Styleable, Map<String, PropertyValue>> boxStyleCache = new ConcurrentHashMap<>();
    private final Map<Styleable, String> boxClassCache = new ConcurrentHashMap<>();
    private final Map<Node, String> elementClassCache = new ConcurrentHashMap<>();

    // current document's common css rule set
    private Map<String, List<PropertyDeclaration>> docRuleSets;

    // browser default css rule set
    private Map<String, List<PropertyDeclaration>> defaultRuleSets;

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
        stylesheetFactory = new StylesheetFactoryImpl(getUac());
        return stylesheetFactory;
    }

    public Document getDocument() {
        return this.importer.getRenderer().getDocument();
    }

    public NamespaceHandler getNamespaceHandler() {
        return this.importer.getRenderer().getSharedContext()
                .getNamespaceHandler();
    }

    private Map<String, List<PropertyDeclaration>> getDocumentRuleSets() {
        if (null != docRuleSets) return docRuleSets;
        docRuleSets = getRuleSets(getDocumentStylesheets());
        return docRuleSets;
    }

    private Map<String, List<PropertyDeclaration>> getDefaultRuleSets() {
        if (null != defaultRuleSets) return defaultRuleSets;
        StylesheetInfo defaultStylesheet = getNamespaceHandler().getDefaultStylesheet(getStylesheetFactory());
        if (null != defaultStylesheet) {
            defaultRuleSets = getRuleSets(Collections.singletonList(defaultStylesheet));
        }
        return defaultRuleSets;
    }

    private Map<String, List<PropertyDeclaration>> getRuleSets(List<StylesheetInfo> infos) {
        Map<String, List<PropertyDeclaration>> ruleSets = new HashMap<>();
        for (StylesheetInfo info : infos) {
            if (null == info.getStylesheet()) continue;
            for (Object content : info.getStylesheet().getContents()) {
                if (content instanceof Ruleset) {
                    Ruleset ruleset = (Ruleset) content;
                    for (Selector fsSelector : ruleset.getFSSelectors()) {
                        String selector = fsSelector.toString().trim();
                        List<PropertyDeclaration> props = ruleSets.getOrDefault(selector, new ArrayList<>());
                        props.addAll(ruleset.getPropertyDeclarations());
                        ruleSets.putIfAbsent(selector, props);
                    }
                }
            }
        }
        return ruleSets;
    }

    private List<StylesheetInfo> getDocumentStylesheets() {
        List<StylesheetInfo> infos = new ArrayList<>();
        Document document = getDocument();
        if (null == document) return infos;

        StylesheetInfo[] refs = getNamespaceHandler().getStylesheets(document);
        if (null == refs) return infos;

        int inlineStyleCount = 0;
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

        return infos;
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
            Map<String, List<PropertyDeclaration>> documentRuleSets = getDocumentRuleSets();
            if (!"".equals(cssClass)) {
                String[] classes = cssClass.split("\\s+");
                for (String aClass : classes) {
                    String cName = "." + aClass.trim();
                    if (documentRuleSets.containsKey(cName)) {
                        documentRuleSets.get(cName).forEach(pd -> {
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

    /**
     * source is parent node's style, dest is child node's style
     */
    private void copyNotExistStyles(Map<String, PropertyValue> source, Map<String, PropertyValue> dest) {
        source.forEach((name, value) -> {
            // special handling for text-decoration, because in HTML, it needs to be merged from parent
            if (name.equals(TEXT_DECORATION) && dest.containsKey(TEXT_DECORATION)) {
                // merge css value
                String cssText = dest.get(name).getCssText() + ";" + source.get(name).getCssText();
                PropertyValue val = new PropertyValue(CSSPrimitiveValue.CSS_STRING, cssText, cssText);
                dest.put(name, val);
            } else if (!dest.containsKey(name)) {
                dest.put(name, value);
            }
        });
    }

    private Map<String, PropertyValue> getBoxStyles(Styleable box) {
        return getElementStyles(box.getElement());
    }

    private void addStylesFromRuleSet(Map<String, PropertyValue> styles, Ruleset ruleset) {
        if (null == ruleset) return;
        ruleset.getPropertyDeclarations().forEach(pd -> {
            String cssName = pd.getCSSName().toString();
            if (!styles.containsKey(cssName)) {
                styles.put(cssName, (PropertyValue) pd.getValue());
            }
        });
    }

    private Map<String, PropertyValue> getElementStyles(Element element) {
        Map<String, PropertyValue> styles = new HashMap<>();
        if (null == element) return styles;
        String styleText = element.getAttribute("style");
        if (!styleText.isEmpty()) {
            Ruleset ruleset = getStylesheetFactory().parseStyleDeclaration(StylesheetInfo.AUTHOR, styleText);
            addStylesFromRuleSet(styles, ruleset);
        }
        addTextDecorationStyle(styles, element);
        addFontStyle(styles, element);
        addAgentDefaultStyle(styles, element);
        return styles;
    }

    private void addAgentDefaultStyle(Map<String, PropertyValue> styles, Element element) {
        String name = element.getNodeName();
        if (ENABLE_AGENT_DEFAULT_ELEMENT.contains(name)) {
            addStyleFromRuleSets(styles, getDefaultRuleSets(), name);
        }
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
        String tdValue = td.substring(td.indexOf(":") + 1).trim();
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
        Map<String, List<PropertyDeclaration>> documentRulesets = getDocumentRuleSets();
        if (!"".equals(cssClass)) {
            String[] classes = cssClass.split("\\s+");
            for (String aClass : classes) {
                String selector = "." + aClass.trim();
                addStyleFromRuleSets(blockCssMap, documentRulesets, selector);
            }
        }
    }

    private void addStyleFromRuleSets(Map<String, PropertyValue> styles, Map<String,
            List<PropertyDeclaration>> ruleSets, String selector) {
        if (null == ruleSets || !ruleSets.containsKey(selector)) return;
        ruleSets.get(selector).forEach(pd -> {
            String cssName = pd.getCSSName().toString();
            if (!styles.containsKey(cssName)) {
                styles.put(cssName, (PropertyValue) pd.getValue());
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
        addTableDefaultStyles(tableCellBox, result);
        return result;
    }

    private void addTableDefaultStyles(Box box, Map<String, PropertyValue> styles) {
        if (null == box || null == getDefaultRuleSets()) return;
        Box parent = box.getParent();
        if (parent instanceof TableBox) {
            String name = parent.getElement().getNodeName();
            List<PropertyDeclaration> propertyDeclarations = getDefaultRuleSets().get(name);
            if (null != propertyDeclarations) {
                propertyDeclarations.forEach(pd -> {
                    String cssName = pd.getCSSName().toString();
                    if (TABLE_CELL_DERIVED_STYLES.contains(cssName) && !styles.containsKey(cssName)) {
                        styles.put(cssName, (PropertyValue) pd.getValue());
                    }
                });
            }
        } else {
            addTableDefaultStyles(parent, styles);
        }
    }

    public Map<String, PropertyValue> getListMarkerPseudoElementStyle(Element element) {
        Map<String, PropertyValue> styles = new HashMap<>();
        // this method is only applicable for <li> element
        if (!element.getNodeName().equals("li")) return styles;

        // grouped selectors by priority, the last is the highest priority
        List<List<String>> groupedSelectors = getListMarkerPseudoSelectors(element);
        if (groupedSelectors.isEmpty()) return styles;

        // documentRuleSets are ordered according to definition order in HTML document
        List<RuleSet> documentRuleSets = getRuleSetList(getDocumentStylesheets());

        for (List<String> selectors : groupedSelectors) {
            for (RuleSet ruleSet : documentRuleSets) {
                if (containsIgnoreCase(selectors, ruleSet.getSelector())) {
                    ruleSet.getPropertyDeclarations().forEach(pd -> {
                        String cssName = pd.getCSSName().toString();
                        if (!styles.containsKey(cssName)) {
                            styles.put(cssName, (PropertyValue) pd.getValue());
                        }
                    });
                }
            }
        }
        return styles;
    }

    private boolean containsIgnoreCase(List<String> list, String target) {
        for (String s : list) {
            if (s.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * get list marker pseduo selects by priority
     */
    private List<List<String>> getListMarkerPseudoSelectors(Element element) {
        List<List<String>> result = new ArrayList<>();
        if (!element.getNodeName().equals("li")) return result;

        String liMarker = "li";
        // li::marker, lowest priority
        result.add(Collections.singletonList(liMarker));
        String listType = getListType(element);
        // ol > li::marker or ul > li::marker
        result.add(Collections.singletonList(listType + " > " + liMarker));

        // .li1>li::marker
        List<String> liClassSelectors = new ArrayList<>();
        addClassSelectors(liClassSelectors, element, liMarker);
        if (!liClassSelectors.isEmpty()) {
            result.add(liClassSelectors);
        }

        // highest priority
        // ol1>li::marker
        Node parentNode = element.getParentNode();
        if (null != parentNode && parentNode.getNodeType() == Node.ELEMENT_NODE) {
            Element parent = (Element) parentNode;
            if (parent.getNodeName().equalsIgnoreCase("ol") ||
                    parent.getNodeName().equalsIgnoreCase("ul")) {
                List<String> listClassSelectors = new ArrayList<>();
                addClassSelectors(listClassSelectors, parent, liMarker);
                if (!listClassSelectors.isEmpty()) {
                    result.add(listClassSelectors);
                }
            }
        }
        return result;
    }

    private void addClassSelectors(List<String> classSelectors, Element element, String liMarker) {
        String aClass = element.getAttribute("class");
        String[] classes = aClass.split("\\s+");
        for (String clz : classes) {
            if (!"".equals(clz)) {
                classSelectors.add("." + clz + " > " + liMarker);
            }
        }
    }

    private String getListType(Element liElement) {
        String listType = "ol";
        Node parentNode = liElement.getParentNode();
        if (parentNode != null && parentNode.getNodeName().equalsIgnoreCase("ul")) {
            listType = "ul";
        }
        return listType;
    }

    private List<RuleSet> getRuleSetList(List<StylesheetInfo> infos) {
        List<RuleSet> ruleSets = new ArrayList<>();
        for (StylesheetInfo info : infos) {
            if (null == info.getStylesheet()) continue;
            for (Object content : info.getStylesheet().getContents()) {
                if (content instanceof Ruleset) {
                    Ruleset ruleset = (Ruleset) content;
                    addRuleSet(ruleSets, ruleset);
                }
            }
        }
        return ruleSets;
    }

    private void addRuleSet(List<RuleSet> ruleSets, Ruleset ruleset) {
        for (Selector fsSelector : ruleset.getFSSelectors()) {
            String selector = fsSelector.toString().trim();

            RuleSet ruleSet = getRuleSetBySelector(ruleSets, selector);
            if (null == ruleSet) {
                // must create a new ArrayList, because the ruleset.getPropertyDeclarations() is immutable
                ruleSet = new RuleSet(selector, new ArrayList<>(ruleset.getPropertyDeclarations()));
                ruleSets.add(ruleSet);
            } else {
                ruleSet.getPropertyDeclarations().addAll(ruleset.getPropertyDeclarations());
            }
        }
    }

    private RuleSet getRuleSetBySelector(List<RuleSet> ruleSets, String selector) {
        for (RuleSet ruleSet : ruleSets) {
            if (ruleSet.getSelector().equals(selector)) {
                return ruleSet;
            }
        }
        return null;
    }

    static class RuleSet {
        private String selector;
        private List<PropertyDeclaration> propertyDeclarations = new ArrayList<>();

        public RuleSet(String selector, List<PropertyDeclaration> propertyDeclarations) {
            this.selector = selector;
            this.propertyDeclarations = propertyDeclarations;
        }

        public String getSelector() {
            return selector;
        }

        public void setSelector(String selector) {
            this.selector = selector;
        }

        public List<PropertyDeclaration> getPropertyDeclarations() {
            return propertyDeclarations;
        }

        public void setPropertyDeclarations(List<PropertyDeclaration> propertyDeclarations) {
            this.propertyDeclarations = propertyDeclarations;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RuleSet ruleSet = (RuleSet) o;

            return selector.equals(ruleSet.selector);
        }

        @Override
        public int hashCode() {
            return selector.hashCode();
        }
    }
}
