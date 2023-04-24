package org.docx4j.convert.in.xhtml.box;

import com.openhtmltopdf.css.parser.PropertyValue;
import com.openhtmltopdf.layout.LayoutContext;
import com.openhtmltopdf.render.BlockBox;
import org.docx4j.convert.in.xhtml.utils.Utils;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extended BlockBox
 *
 * @author longyg
 */
public class ExBlockBox extends BlockBox implements ExStyleable {
    /**
     * The real original block box which this block box extended from.
     */
    private BlockBox box;

    /**
     * The HTML element hold by this block box.
     * If the block box is anonymous, it is the same element of its parent.
     */
    private Element element;

    /**
     * The parent block box which hold this block box.
     */
    private ExBlockBox parent;

    /**
     * The block boxes children which this block box hold.
     * It is only applicable when _childrenContentType = BLOCK.
     */
    private List<ExBlockBox> blockChildren;

    /**
     * The inline boxes children which this block box hold.
     * It is only applicable when _childrenContentType = INLINE.
     */
    private List<ExInlineBox> inlineChildren;

    /**
     * All css styles of this block box, includes all possible styles from inline style, classes,
     * tag styles, user agent default styles. The css value 'inherit' is resolved as real value.
     * It also inherits the ancestors' styles if the css rule is inheritable.
     */
    private Map<String, PropertyValue> allCssMap = new HashMap<>();

    private Map<String, PropertyValue> inlineCssMap = new HashMap<>();

    public ExBlockBox(BlockBox blockBox) {
        super();
        this.box = blockBox;
    }

    public void layoutEx(LayoutContext c, ExBlockBox parent) {
        this.parent = parent;
        Element e = box.getElement();
        if (null == e && null != parent) {
            e = parent.getElement();
        }
        element = e;
        allCssMap = Utils.getCascadedProperties(box.getStyle());
    }


    private BlockBox getOriginalBox() {
        return box;
    }
}
