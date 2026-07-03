package com.scott.payment.admin.entity.channel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 渠道管理数据库实体集合。
 *
 * <p>仅承载管理后台渠道基础资料、支付能力、限额和接入配置维护数据，不承载渠道调用或交易状态机逻辑。</p>
 */
public final class ChannelEntities {

    private ChannelEntities() {
    }

    /**
     * 渠道基础信息数据库实体。
     */
    @Data
    @TableName("channel_info")
    public static class ChannelInfoDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String channelCode;
        private String channelCnName;
        private String channelEnName;
        private Integer channelStatus;
        private Integer supportAcquiring;
        private Integer supportPayout;
        @TableField("support_3ds")
        private Integer support3ds;
        private String defaultRequestUrl;
        private String defaultInteractionMode;
        private Integer sortOrder;
        private String remark;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /**
     * 渠道支付能力数据库实体。
     */
    @Data
    @TableName("channel_payment_capability")
    public static class ChannelPaymentCapabilityDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long channelId;
        private String channelCode;
        private String businessType;
        private String paymentMethod;
        private String transactionType;
        @TableField("support_3ds")
        private Integer support3ds;
        @TableField("support_incremental_authorization")
        private Integer supportIncrementalAuthorization;
        private Integer capabilityStatus;
        private Integer sortOrder;
        private String remark;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /**
     * 渠道支付能力币种数据库实体。
     */
    @Data
    @TableName("channel_capability_currency")
    public static class ChannelCapabilityCurrencyDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long capabilityId;
        private Long channelId;
        private String channelCode;
        private String currencyCode;
        private Integer currencyStatus;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /**
     * 渠道卡品牌绑定数据库实体。
     */
    @Data
    @TableName("channel_capability_card_brand")
    public static class ChannelCapabilityCardBrandDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long capabilityId;
        private Long channelId;
        private String channelCode;
        private String cardBrand;
        private Integer brandStatus;
        private Integer sortOrder;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /**
     * 渠道限额规则数据库实体。
     */
    @Data
    @TableName("channel_limit_rule")
    public static class ChannelLimitRuleDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long channelId;
        private String channelCode;
        private String businessType;
        private String paymentMethod;
        private String transactionType;
        private String cardBrand;
        private String limitType;
        private String limitCurrency;
        private BigDecimal limitAmount;
        private LocalDateTime effectiveStartTime;
        private LocalDateTime effectiveEndTime;
        private Integer ruleStatus;
        private String remark;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /**
     * 渠道接入配置数据库实体，敏感字段仅保存密文。
     */
    @Data
    @TableName("channel_access_config")
    public static class ChannelAccessConfigDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long channelId;
        private String channelCode;
        private String envMode;
        private String baseUrl;
        private String callbackUrl;
        private String interactionMode;
        private String channelMerchantNo;
        private String apiKeyCipher;
        private String apiSecretCipher;
        private String clientCertPath;
        private String clientCertPasswordCipher;
        private String serverCertPath;
        private String extraConfigJson;
        private Integer configStatus;
        private String remark;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }
}
