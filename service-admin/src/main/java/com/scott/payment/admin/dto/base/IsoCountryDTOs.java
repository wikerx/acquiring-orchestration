package com.scott.payment.admin.dto.base;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ISO 3166 国家和地区管理接口使用的请求与响应模型。
 *
 * <p>模型保持管理端既有 JSON 字段，同时隔离数据库实体中的持久化实现。</p>
 */
public final class IsoCountryDTOs {

    /**
     * 阻止仅承载嵌套 DTO 的类型被实例化。
     */
    private IsoCountryDTOs() {
    }

    /**
     * 新增或编辑国家和地区资料的请求。
     *
     * <p>未声明字段会被忽略，主键、审计时间和删除标记始终由服务端管理。</p>
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SaveRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 所属大洲代码。
         */
        private String continentCode;

        /**
         * 所属大洲名称。
         */
        private String continentName;

        /**
         * 国家或地区英文全称。
         */
        private String englishName;

        /**
         * 国家或地区英文简称。
         */
        private String shortEnglishName;

        /**
         * 国家或地区中文名称。
         */
        private String chineseName;

        /**
         * ISO 3166-1 alpha-2 代码。
         */
        private String alpha2Code;

        /**
         * ISO 3166-1 alpha-3 代码。
         */
        private String alpha3Code;

        /**
         * ISO 3166-1 数字代码。
         */
        private String numericCode;

        /**
         * 国家或地区旗帜图标。
         */
        private String flagEmoji;

        /**
         * 主要语言代码。
         */
        private String primaryLanguageCode;

        /**
         * 主要语言英文名称。
         */
        private String primaryLanguageEnglish;

        /**
         * 主要语言中文名称。
         */
        private String primaryLanguageChinese;

        /**
         * 默认 ISO 4217 币种代码。
         */
        private String currencyAlpha3Code;

        /**
         * 启停状态，0 表示停用，1 表示启用。
         */
        private Integer status;
    }

    /**
     * 国家和地区资料响应。
     */
    @Data
    public static class Response implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 记录主键。
         */
        private Long id;

        /**
         * 所属大洲代码。
         */
        private String continentCode;

        /**
         * 所属大洲名称。
         */
        private String continentName;

        /**
         * 国家或地区英文全称。
         */
        private String englishName;

        /**
         * 国家或地区英文简称。
         */
        private String shortEnglishName;

        /**
         * 国家或地区中文名称。
         */
        private String chineseName;

        /**
         * ISO 3166-1 alpha-2 代码。
         */
        private String alpha2Code;

        /**
         * ISO 3166-1 alpha-3 代码。
         */
        private String alpha3Code;

        /**
         * ISO 3166-1 数字代码。
         */
        private String numericCode;

        /**
         * 国家或地区旗帜图标。
         */
        private String flagEmoji;

        /**
         * 主要语言代码。
         */
        private String primaryLanguageCode;

        /**
         * 主要语言英文名称。
         */
        private String primaryLanguageEnglish;

        /**
         * 主要语言中文名称。
         */
        private String primaryLanguageChinese;

        /**
         * 默认 ISO 4217 币种代码。
         */
        private String currencyAlpha3Code;

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
