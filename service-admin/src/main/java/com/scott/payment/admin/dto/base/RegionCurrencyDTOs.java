package com.scott.payment.admin.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 地区默认币种管理接口使用的请求和响应模型。
 *
 * <p>这些类型固定管理端接口字段，避免控制器和应用服务通过字符串键传递稳定契约。</p>
 */
public final class RegionCurrencyDTOs {

    /**
     * 阻止仅承载嵌套 DTO 的工具类型被实例化。
     */
    private RegionCurrencyDTOs() {
    }

    /**
     * 新增国家或地区默认币种关联的请求。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 国家或地区主键。
         */
        @NotNull(message = "countryId must not be null")
        private Long countryId;

        /**
         * ISO 4217 三位币种代码。
         */
        @NotBlank(message = "currencyAlpha3Code must not be blank")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currencyAlpha3Code must be a 3-letter code")
        private String currencyAlpha3Code;
    }

    /**
     * 更新国家或地区默认币种的请求。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * ISO 4217 三位币种代码。
         */
        @NotBlank(message = "currencyAlpha3Code must not be blank")
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currencyAlpha3Code must be a 3-letter code")
        private String currencyAlpha3Code;
    }

    /**
     * 更新国家或地区启停状态的请求。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusUpdateRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 启停状态，0 表示停用，1 表示启用。
         */
        @NotNull(message = "status must not be null")
        @Min(value = 0, message = "status must be 0 or 1")
        @Max(value = 1, message = "status must be 0 or 1")
        private Integer status;
    }

    /**
     * 国家或地区及其默认币种的管理端响应。
     */
    @Data
    public static class RegionCurrencyResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 国家或地区主键。
         */
        private Long id;

        /**
         * ISO 3166-1 alpha-2 国家或地区代码。
         */
        private String alpha2Code;

        /**
         * ISO 3166-1 alpha-3 国家或地区代码。
         */
        private String alpha3Code;

        /**
         * 国家或地区中文名称。
         */
        private String countryName;

        /**
         * 国家或地区英文名称。
         */
        private String countryEnglishName;

        /**
         * 所属大洲代码。
         */
        private String continentCode;

        /**
         * 所属大洲名称。
         */
        private String continentName;

        /**
         * 默认 ISO 4217 三位币种代码。
         */
        private String currencyAlpha3Code;

        /**
         * 默认币种中文名称；未匹配币种字典时为短横线。
         */
        private String currencyName;

        /**
         * 默认币种符号；未匹配币种字典时为空字符串。
         */
        private String currencySymbol;

        /**
         * 国家或地区启停状态，0 表示停用，1 表示启用。
         */
        private Integer status;
    }
}
