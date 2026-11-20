package com.scott.payment.link.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 共享链接管理输入；各服务继续保留原有公开 DTO 名称和权限边界。 */
public final class PaymentLinkManagementModels {
    private PaymentLinkManagementModels() { }
    /** 固定金额链接的创建输入；请求号用于幂等，截止时刻与相对有效期互斥。 */
    @Data
    public static class Create {
        @NotBlank @Size(max = 80)
        private String requestId;
        @Size(max = 120)
        private String linkName;
        @Size(max = 64)
        private String merchantOrderNo;
        @Size(max = 500)
        private String description;
        @NotBlank @Pattern(regexp = "[A-Z]{3}")
        private String currency;
        @NotBlank @Pattern(regexp = "[0-9]{1,6}(\\.[0-9]{1,2})?")
        private String amount;
        @Min(15) @Max(43200)
        private Integer expireMinutes;
        /** 自选截止时刻采用既有上海时间契约，与 expireMinutes 互斥。 */
        private LocalDateTime expireTime;
        @NotBlank @Pattern(regexp = "en-US|zh-CN|AUTO")
        private String locale = "en-US";
        @Size(max = 16)
        private List<@Pattern(regexp = "[A-Z_]{2,40}") String> cardBrands;
        /** 新业务契约明确选择的规范支付方式键，旧卡品牌请求继续兼容。 */
        @Size(min = 1, max = 32)
        private List<@NotBlank @Pattern(regexp = "(CARD_BRAND|METHOD):[A-Z][A-Z0-9_]{0,51}") String> paymentMethodKeys;
        @Size(max = 512)
        @lombok.ToString.Exclude
        private String notifyUrl;
        @Size(max = 512)
        @lombok.ToString.Exclude
        private String redirectUrl;

        /** 明确拒绝未支持字段，避免把调用方要求静默降级为另一种链接契约。 */
        @JsonAnySetter
        public void rejectUnsupported(String name, Object value) {
            throw new IllegalArgumentException("Unsupported payment link field");
        }
    }

    /** 由已鉴权服务构造的操作身份；商户范围不能由公开请求自行扩大。 */
    @Data
    public static class Actor {
        private String appCode;
        private String actorId;
        private boolean allMerchants;
        private Set<String> merchantIds = Set.of();
    }

    /** 当前商户可创建链接的币种、卡品牌与有效期限制，不代表未来永久可交易。 */
    @Data
    public static class Options {
        /** 默认展示商户结算币种，实际收单能力仍独立校验。 */
        private String defaultCurrency;
        private Map<String, Integer> currencyExponents = Map.of();
        private String merchantId;
        private String merchantDisplayName;
        private List<String> currencies = List.of();
        /** 商户当前币种的完整支付方式，仅作展示；首批链接仍仅授权银行卡。 */
        private Map<String, List<String>> paymentMethodsByCurrency = Map.of();
        /** 新准入模式下基于授权、费率和通道配置生成的可选规范方式。 */
        private Map<String, List<String>> paymentMethodKeysByCurrency;
        private Map<String, List<String>> cardBrandsByCurrency = Map.of();
        private List<String> cardBrands = List.of();
        private List<String> locales = List.of("en-US", "zh-CN", "AUTO");
        private int minExpireMinutes = 15;
        private int maxExpireMinutes = 43200;
    }

    /** 能力查询只含可展示配置，禁止带入 MID 密钥、卡号或付款人资料。 */
    @Data
    public static class AllowedMethod {
        private String paymentMethod;
        private String channelCode;
        private List<String> brands = List.of();
        private String threeDsMode;
    }
}
