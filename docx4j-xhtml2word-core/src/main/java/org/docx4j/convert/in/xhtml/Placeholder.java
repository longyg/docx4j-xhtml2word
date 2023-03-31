package org.docx4j.convert.in.xhtml;

/**
 * @author longyg
 */
public class Placeholder {
    public static final String SEPARATOR = ".";
    private String id;
    private String name;
    private PlaceholderType type;

    public Placeholder(String id, String name, PlaceholderType type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PlaceholderType getType() {
        return type;
    }

    public void setType(PlaceholderType type) {
        this.type = type;
    }

    public String getString() {
        return SEPARATOR + name + SEPARATOR + id;
    }
}
