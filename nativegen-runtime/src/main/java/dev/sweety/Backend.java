package dev.sweety;

import java.util.Locale;

public enum Backend {
    CPP("native_cpp"),
    RUST("native_rust");

    private final String libName;

    Backend(String libName) {
        this.libName = libName;
    }

    public static Backend fromProperty() {
        return "rust".equalsIgnoreCase(System.getProperty("jni.backend", "cpp")) ? RUST : CPP;
    }

    public String libName() {
        return libName;
    }
}
