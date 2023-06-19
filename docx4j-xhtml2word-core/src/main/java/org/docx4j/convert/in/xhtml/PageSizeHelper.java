package org.docx4j.convert.in.xhtml;

import com.openhtmltopdf.layout.Styleable;
import com.openhtmltopdf.render.BlockBox;
import org.docx4j.UnitsOfMeasurement;
import org.docx4j.jaxb.Context;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.Jc;
import org.docx4j.wml.JcEnumeration;
import org.docx4j.wml.PPr;
import org.docx4j.wml.SectPr;

import java.math.BigInteger;

/**
 * @author longyg
 */
public class PageSizeHelper {
    private WordprocessingMLPackage wordMLPackage;
    private XHTMLImporterImpl importer;
    private BigInteger pgSz = null;
    private BigInteger pgMarLeft = null;
    private BigInteger pgMarRight = null;


    public PageSizeHelper(WordprocessingMLPackage wordMLPackage, XHTMLImporterImpl importer) {
        this.wordMLPackage = wordMLPackage;
        this.importer = importer;
    }

    private BigInteger getPgSz() {
        if (null != pgSz) {
            return pgSz;
        }
        parsePgSetting();
        return pgSz;
    }

    private BigInteger getPgMarLeft() {
        if (null != pgMarLeft) {
            return pgMarLeft;
        }
        parsePgSetting();
        return pgMarLeft;
    }

    private BigInteger getPgMarRight() {
        if (null != pgMarRight) {
            return pgMarRight;
        }
        parsePgSetting();
        return pgMarRight;
    }

    private void parsePgSetting() {
        if (null == wordMLPackage) return;

        if (null != wordMLPackage.getMainDocumentPart()) {
            org.docx4j.wml.Document document = wordMLPackage.getMainDocumentPart().getJaxbElement();
            if (null != document && null != document.getBody()) {
                SectPr sectPr = document.getBody().getSectPr();
                if (null != sectPr && null != sectPr.getPgSz()) {
                    pgSz = sectPr.getPgSz().getW();
                }
                if (null != sectPr && null != sectPr.getPgMar()) {
                    pgMarLeft = sectPr.getPgMar().getLeft();
                    pgMarRight = sectPr.getPgMar().getRight();
                }
            }
        }
    }

    public void restrictIndent(PPr pPr, Styleable styleable) {
        if (null == pPr.getInd()) return;

        BigInteger left = pPr.getInd().getLeft();
        if (null != left && left.compareTo(BigInteger.ZERO) > 0) {
            // if left indent is bigger than page size,
            // set left ident equals page size
            BigInteger pageSize = getPgSz();
            BigInteger marginLeft = getPgMarLeft() == null ? BigInteger.ZERO : getPgMarLeft();
            BigInteger marginRight = getPgMarRight() == null ? BigInteger.ZERO : getPgMarRight();
            if (null != pgSz) {
                // here, we need also consider the width of box
                // TBD: is it right way to get the box width?
                BigInteger width = getWidth(styleable);
                BigInteger ind = pageSize.subtract(marginLeft).subtract(marginRight).subtract(width);
                if (left.compareTo(ind) > 0) {
                    pPr.getInd().setLeft(BigInteger.ZERO);
                    Jc jc = Context.getWmlObjectFactory().createJc();
                    jc.setVal(JcEnumeration.RIGHT);
                    pPr.setJc(jc);
                }
            }
        }
    }

    public BigInteger getWidth(Styleable styleable) {
        if (styleable instanceof BlockBox) {
            BlockBox blockBox = (BlockBox) styleable;
            blockBox.calcMinMaxWidth(importer.getRenderer().getLayoutContext());
            int width = Math.round(dotsToTwip(blockBox.getMaxWidth()));
            return BigInteger.valueOf(width);
        }
        return BigInteger.ZERO;
    }

    public float dotsToTwip(float dots) {
        return dots * 72f / (UnitsOfMeasurement.DPI);
    }
}
