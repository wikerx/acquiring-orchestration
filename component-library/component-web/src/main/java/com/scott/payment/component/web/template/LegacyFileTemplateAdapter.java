package com.scott.payment.component.web.template;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 现有模板下载路径迁移到模板中心期间的显式兼容标记。
 *
 * <p>只允许用于迁移前已经存在的端点。必须登记稳定编码规则和移除日期；新增功能禁止使用。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LegacyFileTemplateAdapter {

    /** 对应模板编码或动态编码规则。 */
    String value();

    /** ISO-8601 日期；到期后契约测试失败。 */
    String removeAfter();
}
