package com.scott.payment.component.db.auth.support;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLocaleSupport
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Merchant-level locale values supported by email templates and the merchant portal.
 * @status : create
 */
public final class MerchantLocaleSupport {

    /**
     * {@code CHINESE}常量，统一 商户语言区域支持 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String CHINESE = "zh-CN";
    /**
     * {@code ENGLISH}常量，统一 商户语言区域支持 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String ENGLISH = "en-US";

    private MerchantLocaleSupport() {
    }

    /**
     * 将输入归一为当前类型接受的标准格式。
     * <p>
     * 仅返回规范化或计算结果，不直接提交交易状态。
     * </p>
     * @param locale 语言区域、年份或月份值，用于格式化、分区或缓存窗口计算
     * @return 构造、转换或解析后的业务值
     */
    public static String normalize(String locale) {
        return ENGLISH.equalsIgnoreCase(locale == null ? "" : locale.trim()) ? ENGLISH : CHINESE;
    }
}
