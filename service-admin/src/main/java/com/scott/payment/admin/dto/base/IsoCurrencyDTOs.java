package com.scott.payment.admin.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ISO 4217 币种管理接口使用的请求与响应模型。
 *
 * <p>金额精度字段沿用数据库定义的整数和 {@link BigDecimal} 类型，避免精度损失。</p>
 */
public final class IsoCurrencyDTOs {

    /**
     * 阻止仅承载嵌套 DTO 的类型被实例化。
     */
    private IsoCurrencyDTOs() {
    }

    /**
     * 新增或编辑币种资料的请求。
     *
     * <p>未声明字段会被忽略，主键、审计时间和删除标记始终由服务端管理。</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaveRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * ISO 4217 三位字母代码。
         */
        private String alpha3Code;

        /**
         * ISO 4217 三位数字代码。
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
         * 币种符号。
         */
        private String currencySymbol;

        /**
         * 受控币种展示图标键。
         */
        private String iconKey;

        /**
         * 默认辅币位数。
         */
        private Integer fractionDigits;

        /**
         * 主币转换为最小辅币单位的倍数。
         */
        private Long minorUnitMultiplier;

        /**
         * 币种支持的最小金额。
         */
        private BigDecimal minimumAmount;

        /**
         * 启停状态，0 表示停用，1 表示启用。
         */
        private Integer status;
    }

    /**
     * 币种资料响应。
     */
    @Data
    public static class Response implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 记录主键。
         */
        private Long id;

        /**
         * ISO 4217 三位字母代码。
         */
        private String alpha3Code;

        /**
         * ISO 4217 三位数字代码。
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
         * 币种符号。
         */
        private String currencySymbol;

        /**
         * 受控币种展示图标键。
         */
        private String iconKey;

        /**
         * 默认辅币位数。
         */
        private Integer fractionDigits;

        /**
         * 主币转换为最小辅币单位的倍数。
         */
        private Long minorUnitMultiplier;

        /**
         * 币种支持的最小金额。
         */
        private BigDecimal minimumAmount;

        /**
         * 启停状态，0 表示停用，1 表示启用。
         */
        private Integer status;

        /**
         * 记录创建时间。
         */
        private LocalDateTime createdAt;

        /**
         * 记录最后更新时间。
         */
        private LocalDateTime updatedAt;

        /**
         * 逻辑删除标记，保留该字段以兼容既有管理端响应。
         */
        private Integer deleted;
    }
}
