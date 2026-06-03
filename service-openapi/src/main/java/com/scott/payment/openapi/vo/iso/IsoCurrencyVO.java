package com.scott.payment.openapi.vo.iso;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCurrencyVO
 * @date : 2026-06-03 15:10
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 币种响应参数
 * @status : create
 */
@Data
public class IsoCurrencyVO implements Serializable {

    /**
     * 序列化版本号，用于保证响应对象在服务间传输或日志落库时兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * ISO 4217 三位字母币种代码，例如 USD、CNY。
     */
    private String alphabeticCode;

    /**
     * ISO 4217 三位数字币种代码，例如 840、156。
     */
    private String numericCode;

    /**
     * 币种英文名称。
     */
    private String englishName;

    /**
     * 币种中文名称。
     */
    private String chineseName;

    /**
     * 默认辅币位，小于 0 表示无可靠辅币位定义。
     */
    private Integer defaultFractionDigits;

    /**
     * 主币转换为最小辅币单位的倍数。
     */
    private Long minorUnitMultiplier;

    /**
     * 最小金额单位，例如 USD 为 0.01，JPY 为 1。
     */
    private BigDecimal minimumAmount;

    /**
     * 币种符号或图标，例如 $、¥、€。
     */
    private String currencySymbol;
}
