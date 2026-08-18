package com.scott.payment.component.db.route.model;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRouteProfile
 * @date : 2026-08-01 15:30
 * @email : scott_x@163.com
 * @description : 商户收单路由永久 Redis 快照，只保存交易选路所需的非敏感绑定、渠道能力和币种字段
 * @status : create
 *
 * <p>物理 Key 为 {@code acquiring:{environment}:merchant:route:{merchantId}}。数据库是最终事实源，
 * 管理端变更通过门禁和 Outbox 可靠失效。本模型禁止加入渠道密码、API Key、证书、私钥、
 * 完整令牌和 {@code metadata_value_json}。</p>
 */
@Data
public class MerchantRouteProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前快照结构版本，用于永久 Redis 数据的在线兼容升级。 */
    public static final int CURRENT_SCHEMA_VERSION = 2;

    /** 构建该快照时使用的结构版本；旧缓存反序列化后为空。 */
    private Integer schemaVersion;

    /** 平台商户号，同时作为永久缓存业务键。 */
    private String merchantId;

    /** 当前未删除绑定数量，用于路由诊断，不包含任何渠道凭据。 */
    private Integer bindingCount;

    /** 已展开的路由候选快照；使用 ArrayList 保持 Redis wire type 稳定。 */
    private List<RouteOption> routeOptions = new ArrayList<>();

    /**
     * 单个商户绑定、MID、渠道和支付能力组合后的路由候选。
     *
     * <p>渠道 MID 用于交易协议定位，属于受保护标识，日志必须掩码；渠道敏感元数据不在本对象中。</p>
     */
    @Data
    public static class RouteOption implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 商户渠道绑定记录主键。 */
        private Long bindingId;

        /** 绑定启用状态。 */
        private Integer bindingStatus;

        /** 绑定生效时间；为空表示立即生效。 */
        private LocalDateTime bindingEffectiveTime;

        /** 绑定失效时间；为空表示长期有效。 */
        private LocalDateTime bindingExpireTime;

        /** 渠道 MID 配置主键。 */
        private Long midConfigId;

        /** 渠道侧 MID；只能按掩码写日志。 */
        private String channelMid;

        /** MID 业务类型，例如 ACQUIRING。 */
        private String businessType;

        /** MID 支付方式范围。 */
        private String paymentMethodScope;

        /** MID 银行卡品牌范围。 */
        private String cardBrandScope;

        /** MID 交易类型范围。 */
        private String transactionTypeScope;

        /** MID 币种范围。 */
        private String currencyScope;

        /** MID 允许国家范围。 */
        private String allowedCountryScope;

        /** MID 启用状态。 */
        private Integer midStatus;

        /** MID 生效时间；为空表示立即生效。 */
        private LocalDateTime midEffectiveTime;

        /** MID 失效时间；为空表示长期有效。 */
        private LocalDateTime midExpireTime;

        /** MID 最近修改时间，仅作为本地敏感元数据缓存版本。 */
        private LocalDateTime midModifiedTime;

        /** 渠道主键。 */
        private Long channelId;

        /** 渠道编码，例如 MPGS。 */
        private String channelCode;

        /** 渠道启用状态。 */
        private Integer channelStatus;

        /** 渠道收单能力开关。 */
        private Integer supportAcquiring;

        /** 渠道请求地址，不允许携带用户名、密码或令牌。 */
        private String requestUrl;

        /** 渠道连接超时，单位秒。 */
        private Integer connectTimeoutSeconds;

        /** 渠道读取超时，单位秒。 */
        private Integer readTimeoutSeconds;

        /** 渠道支付能力主键。 */
        private Long capabilityId;

        /** 能力业务类型。 */
        private String capabilityBusinessType;

        /** 能力支付方式。 */
        private String capabilityPaymentMethod;

        /** 能力支持的交易类型范围。 */
        private String capabilityTransactionType;

        /** 能力明确启用的卡品牌；银行卡路由必须同时满足该范围和 MID 范围。 */
        private List<String> capabilitySupportedCardBrands = new ArrayList<>();

        /** 能力是否支持 3DS：0 不支持，1 支持。 */
        private Integer capabilitySupport3ds;

        /** 能力是否支持增量授权：0 不支持，1 支持。 */
        private Integer capabilitySupportIncrementalAuthorization;

        /** 能力启用状态。 */
        private Integer capabilityStatus;

        /** 能力路由顺序，数值越小优先级越高。 */
        private Integer capabilitySortOrder;

        /** 能力和 MID 范围交集后的支持币种。 */
        private List<String> supportedCurrencies = new ArrayList<>();
    }
}
