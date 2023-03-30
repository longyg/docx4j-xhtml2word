package org.docx4j.convert.in.xhtml;

/**
 * @author longyg
 */
public enum PlaceholderType {
    NAME("name", "CN"),
    CONTENT("content", "CC");

    private String type;
    private String display;

    PlaceholderType(String type, String display) {
        this.type = type;
        this.display = display;
    }

    public String getType() {
        return type;
    }

    public String getDisplay() {
        return display;
    }

    public static PlaceholderType fromValue(String type) {
        for (PlaceholderType value : PlaceholderType.values()) {
            if (value.getType().equals(type)) {
                return value;
            }
        }
        return null;
    }
}
