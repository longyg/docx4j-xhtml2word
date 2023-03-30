package org.docx4j.convert.in.xhtml;

import lombok.Builder;
import lombok.Data;

/**
 * @author longyg
 */
@Data
@Builder
public class Placeholder {
    private String id;
    private String name;
    private PlaceholderType type;

    public String getString() {
        return "-" + name + "-" + id;
    }
}
