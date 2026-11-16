package com.scott.payment.component.web.template;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记受模板中心治理的文件模板下载端点。
 *
 * <p>契约测试会拒绝未标记的模板下载接口。标记方法必须委托模板中心读取已发布资产，
 * 或作为有明确迁移期限且已登记模板编码的兼容适配器。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ManagedFileTemplateEndpoint {

    /** 固定模板编码；通用按编码下载端点使用 DYNAMIC。 */
    String value();
}
