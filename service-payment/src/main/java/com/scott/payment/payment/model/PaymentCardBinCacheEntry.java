package com.scott.payment.payment.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCardBinCacheEntry
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 按卡号前 11 位缓存的 BIN 查询结果；matched=false 用于避免数据库未命中时重复查询。
 * @status : create
 */
@Data
public class PaymentCardBinCacheEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 卡 BIN，用于识别发卡行、卡组织、国家地区和风控规则。
     * <p>
     * 单位：无；格式：卡 BIN 或尾号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅保存识别片段，不保存完整 PAN；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private String cardBinPrefix;
    /**
     * {@code matched}，用于明确 {@code PaymentCardBinCacheEntry} 当前业务分支是否成立。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private Boolean matched;
    /**
     * 范围ID，用于定位 {@code PaymentCardBinCacheEntry} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private Long rangeId;
    /**
     * {@code binLength}字段，保存 {@code PaymentCardBinCacheEntry} 当前处理所需的业务取值。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private Integer binLength;
    /**
     * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private String cardBrand;
    /**
     * {@code issuerCountryAlpha2}，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。
     * <p>
     * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持国家地区；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private String issuerCountryAlpha2;
    /**
     * {@code issuerCountryAlpha3}，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。
     * <p>
     * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持国家地区；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private String issuerCountryAlpha3;
    /**
     * {@code issuerCountryName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：ISO 国家或地区代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自平台支持国家地区；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private String issuerCountryName;
    /** 当前命中区间的生效时间，用于缓存命中后的时间边界自检。 */
    private LocalDateTime effectiveTime;
    /** 当前命中区间的失效时间，达到该时间后必须回源主库。 */
    private LocalDateTime expireTime;
    /** miss 时可能匹配区间的最近未来生效时间，达到该时间后必须回源主库。 */
    private LocalDateTime nextEffectiveTime;

    /**
     * 创建未命中卡 BIN 缓存的占位结果，避免把空值写入缓存。
     * @param cardBinPrefix 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 卡 BIN 未命中占位结果
     */
    public static PaymentCardBinCacheEntry miss(String cardBinPrefix) {
        PaymentCardBinCacheEntry entry = new PaymentCardBinCacheEntry();
        entry.setCardBinPrefix(cardBinPrefix);
        entry.setMatched(Boolean.FALSE);
        return entry;
    }

    /**
     * 判断缓存条目在指定业务时间是否仍可使用。
     *
     * @param now 平台当前时间
     * @return 未跨越命中失效点或 miss 的下一生效点时返回 true
     */
    public boolean usableAt(LocalDateTime now) {
        if (now == null) {
            return false;
        }
        if (Boolean.TRUE.equals(matched)) {
            return (effectiveTime == null || !now.isBefore(effectiveTime))
                    && (expireTime == null || now.isBefore(expireTime));
        }
        return nextEffectiveTime == null || now.isBefore(nextEffectiveTime);
    }
}
