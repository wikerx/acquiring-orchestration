package com.scott.payment.risk.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskRuleSnapshotRow
 * @date : 2026-07-31 15:40
 * @email : scott_x@163.com
 * @description : 风控名单和规则常驻快照行，保存运行时匹配所需的最小字段及统一决策结果
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RiskRuleSnapshotRow extends RiskListMatch {

    private static final long serialVersionUID = 1L;

    /** 规则生效范围：GLOBAL 或 MERCHANT；不能为空。 */
    private String merchantScope;

    /** 商户范围规则对应的商户号；全局规则允许为空。 */
    private String merchantId;

    /** 精确匹配使用的不可逆哈希；不得包含敏感明文。 */
    private String matchValueHash;

    /** IP 或 BIN 区间起始整数；非区间规则允许为空。 */
    private BigDecimal matchValueStartNumber;

    /** IP 或 BIN 区间结束整数；非区间规则允许为空。 */
    private BigDecimal matchValueEndNumber;

    /** IP 版本：IPV4 或 IPV6；非 IP 规则允许为空。 */
    private String ipVersion;

    /** ISO 3166-1 Alpha-3 国家或地区代码；非地域规则允许为空。 */
    private String countryAlpha3;

    /** 地域匹配级别：COUNTRY、STATE 或 CITY；非地域规则允许为空。 */
    private String regionMatchLevel;

    /** 规范化省/州名称；国家级规则及非地域规则允许为空。 */
    private String stateProvinceName;

    /** 规范化城市名称；国家/州级规则及非地域规则允许为空。 */
    private String cityName;

    /** 规范化来源主机名，不包含协议、路径、查询参数或凭据。 */
    private String sourceHost;

    /** 运行时是否允许命中；来源网址快照中仅审核通过且交易状态允许的记录为 true。 */
    private Boolean runtimeAllowed;

    /** 限额类型：SINGLE_MIN、SINGLE_MAX、DAILY、WEEKLY 或 MONTHLY。 */
    private String limitType;

    /** 单笔最低金额；非最低限额规则允许为空，币种见 {@link #currency}。 */
    private BigDecimal amountMin;

    /** 单笔最高或累计金额；非相关限额规则允许为空，币种见 {@link #currency}。 */
    private BigDecimal amountMax;

    /** ISO 4217 Alpha-3 币种代码；非金额规则允许为空。 */
    private String currency;

    /** 3DS 支付方式维度，ALL 表示全部；非 3DS 规则允许为空。 */
    private String paymentMethod;

    /** 3DS 卡品牌维度，ALL 表示全部；非 3DS 规则允许为空。 */
    private String cardBrand;

    /** 3DS 金额匹配类型：ALL、GE、LE 或 BETWEEN。 */
    private String amountMatchType;

    /** 3DS 风险条件：ANY 或指定风险等级及以上。 */
    private String riskCondition;

    /** 3DS 触发动作：FORCE_3DS 或 SKIP_3DS。 */
    private String triggerAction;

    /** 规则优先级，数值越小越优先；非优先级规则允许为空。 */
    private Integer priority;

    /** BIN 数据源优先级，数值越大越优先；非 BIN 基础数据允许为空。 */
    private Integer sourcePriority;

    /** BIN 有效长度；匹配时长度越长越具体，非 BIN 基础数据允许为空。 */
    private Integer binLength;

    /** BIN 解析得到的 ISO Alpha-3 发卡国家或地区代码；其他规则允许为空。 */
    private String issuerCountryAlpha3;
}
