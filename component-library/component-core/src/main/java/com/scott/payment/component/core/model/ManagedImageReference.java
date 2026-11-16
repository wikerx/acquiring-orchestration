package com.scott.payment.component.core.model;

import java.util.regex.Pattern;

/** 受管图片的内部引用；只向浏览器投影不含存储路径的版本号。 */
public final class ManagedImageReference {
    private static final Pattern REFERENCE = Pattern.compile("images/(avatar|channel)/[0-9]+/[a-f0-9]{32}-[a-f0-9]{64}\\.png");
    private ManagedImageReference() { }
    public static boolean valid(String value) { return value != null && REFERENCE.matcher(value).matches(); }
    public static String version(String value) {
        return valid(value) ? value.substring(value.lastIndexOf('/') + 1, value.lastIndexOf('/') + 33) : null;
    }
}
