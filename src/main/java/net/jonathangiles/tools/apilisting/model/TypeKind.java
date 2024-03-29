package net.jonathangiles.tools.apilisting.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TypeKind {
    CLASS("class"),
    INTERFACE("interface"),
    ENUM("enum"),
    UNKNOWN("unknown");

    private final String name;

    TypeKind(String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() {
        return name;
    }

    public static TypeKind fromClass(Class<?> cls) {
        if (cls.isEnum()) {
            return ENUM;
        } else if (cls.isInterface()) {
            return INTERFACE;
        } else {
            return CLASS;
        }
    }
}
