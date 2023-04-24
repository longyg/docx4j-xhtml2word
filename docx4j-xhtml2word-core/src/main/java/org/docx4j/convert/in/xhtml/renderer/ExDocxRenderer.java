package org.docx4j.convert.in.xhtml.renderer;

import org.docx4j.convert.in.xhtml.box.ExBlockBox;

/**
 * Extended DocxRenderer
 *
 * @author longyg
 */
public class ExDocxRenderer extends DocxRenderer {

    private ExBlockBox exRootBox;

    @Override
    public void layout() {
        super.layout();
        layoutEx();
    }

    private void layoutEx() {
        ExBlockBox root = new ExBlockBox(getRootBox());
        root.layoutEx(getLayoutContext(), null);
        exRootBox = root;
    }

    public ExBlockBox getExRootBox() {
        return exRootBox;
    }
}
