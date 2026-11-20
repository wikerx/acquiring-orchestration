package com.scott.payment.component.core.model;

import java.util.regex.Pattern;

/** 受管图片的内部引用；只向浏览器投影不含存储路径的版本号。 */
public final class ManagedImageReference {
    private static final Pattern REFERENCE = Pattern.compile("images/(avatar|channel)/[0-9]+/[a-f0-9]{32}-[a-f0-9]{64}\\.png");
    private ManagedImageReference() { }
    /** 只接受受管头像或渠道图片的内部键格式，外部 URL 与任意路径均不合法。 */
    public static boolean valid(String value) { return value != null && REFERENCE.matcher(value).matches(); }
    /** 从合法对象键提取随机版本号供浏览器缓存标识；不暴露存储路径或内容摘要。 */
    public static String version(String value) {
        return valid(value) ? value.substring(value.lastIndexOf('/') + 1, value.lastIndexOf('/') + 33) : null;
    }
}
