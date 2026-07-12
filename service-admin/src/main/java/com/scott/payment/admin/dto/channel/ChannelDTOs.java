package com.scott.payment.admin.dto.channel;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelDTOs
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 渠道管理Channel  DTO 集合，位于 service-admin 的接口传输层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class ChannelDTOs {

    private ChannelDTOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelInfoQuery extends PageRequest {
        /**
         * 渠道管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer channelStatus;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportAcquiring;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportPayout;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer support3ds;
    }

    @Data
    public static class ChannelInfoSaveRequest {
        /**
         * 渠道管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "channelCode is required")
        private String channelCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "channelCnName is required")
        private String channelCnName;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "channelEnName is required")
        private String channelEnName;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        @NotNull(message = "channelStatus is required")
        private Integer channelStatus;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotNull(message = "supportAcquiring is required")
        private Integer supportAcquiring;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotNull(message = "supportPayout is required")
        private Integer supportPayout;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotNull(message = "support3ds is required")
        private Integer support3ds;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String defaultRequestUrl;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String defaultInteractionMode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    @Data
    public static class ChannelInfoResponse {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 渠道管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String channelCnName;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String channelEnName;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer channelStatus;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportAcquiring;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportPayout;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer support3ds;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String defaultRequestUrl;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String defaultInteractionMode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        private List<String> acquiringPaymentMethods = new ArrayList<>();
        private List<String> payoutPaymentMethods = new ArrayList<>();
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CapabilityQuery extends PageRequest {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long channelId;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String businessType;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String paymentMethod;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String transactionType;
        /**
         * 渠道管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String currencyCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String cardBrand;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer capabilityStatus;
    }

    @Data
    public static class CapabilitySaveRequest {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @NotNull(message = "channelId is required")
        private Long channelId;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "businessType is required")
        private String businessType;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "paymentMethod is required")
        private String paymentMethod;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String transactionType;
        private List<String> transactionTypes = new ArrayList<>();
        private List<String> currencyCodes = new ArrayList<>();
        private List<String> cardBrands = new ArrayList<>();
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer support3ds;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportIncrementalAuthorization;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        @NotNull(message = "capabilityStatus is required")
        private Integer capabilityStatus;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    @Data
    public static class CapabilitySupportRequest {
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer support3ds;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportIncrementalAuthorization;
    }

    @Data
    public static class CapabilityResponse {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long channelId;
        /**
         * 渠道管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String channelName;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String businessType;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String paymentMethod;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String transactionType;
        private List<String> transactionTypes = new ArrayList<>();
        private List<String> currencyCodes = new ArrayList<>();
        private List<String> cardBrands = new ArrayList<>();
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer support3ds;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportIncrementalAuthorization;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer capabilityStatus;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LimitQuery extends PageRequest {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long channelId;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String businessType;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String paymentMethod;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String cardBrand;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String limitType;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer ruleStatus;
    }

    @Data
    public static class LimitSaveRequest {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @NotNull(message = "channelId is required")
        private Long channelId;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "businessType is required")
        private String businessType;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String paymentMethod;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String cardBrand;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "limitType is required")
        private String limitType;
        /**
         * 渠道管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        @NotNull(message = "limitAmount is required")
        private BigDecimal limitAmount;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        @NotNull(message = "ruleStatus is required")
        private Integer ruleStatus;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    @Data
    public static class LimitBatchSaveRequest {
        @Valid
        private List<LimitSaveRequest> items = new ArrayList<>();
    }

    @Data
    public static class LimitResponse {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long channelId;
        /**
         * 渠道管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String channelName;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String businessType;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String paymentMethod;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String cardBrand;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String limitType;
        /**
         * 渠道管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String limitCurrency;
        /**
         * 渠道管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal limitAmount;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer ruleStatus;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
    }

    @Data
    public static class StatusRequest {
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        @NotNull(message = "status is required")
        private Integer status;
    }

    @Data
    public static class ChannelOption {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 渠道管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String channelName;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer channelStatus;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportAcquiring;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportPayout;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer support3ds;
    }
}
