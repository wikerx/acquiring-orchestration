package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 渠道 MID 配置只读实体，位于 service-payment 数据访问层，用于交易路由读取真实渠道 MID、能力范围和渠道元数据。
 */
@Data
@TableName("channel_mid_config")
public class ChannelMidConfigDO {


    @TableId(type = IdType.AUTO)
    private Long id;


    private Long channelId;

    /**
     * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
     */
    private String channelCode;


    private String channelMid;


    private String midName;


    private String terminalId;

    /**
     * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
     */
    private String businessType;


    private String paymentMethodScope;

    /**
     * 银行卡品牌范围：ALL、NONE 或逗号分隔的品牌编码。
     */
    private String cardBrandScope;

    /**
     * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
     */
    private String transactionTypeScope;


    private String currencyScope;


    private String allowedCountryScope;


    private String defaultSettlementCurrency;

    /**
     * 持久化的{@code settlementCycle}，用于还原当前记录的业务事实。
     */
    private String settlementCycle;

    /**
     * 持久化的{@code settlementCutoffTime}，用于还原当前记录的业务事实。
     */
    private LocalTime settlementCutoffTime;

    /**
     * 结算时间时区，使用 IANA 时区标识解释关联的本地日期时间。
     */
    private String settlementTimeZone;

    /**
     * 持久化的{@code mcc}，用于还原当前记录的业务事实。
     */
    private String mcc;

    /**
     * 持久化的{@code statementDescriptor}，用于还原当前记录的业务事实。
     */
    private String statementDescriptor;

    /**
     * 持久化的{@code metadataValueJson}，用于还原当前记录的业务事实。
     */
    private String metadataValueJson;


    private Integer midStatus;

    /**
     * 业务配置或汇率开始生效的具体时刻。
     */
    private LocalDateTime effectiveTime;

    /**
     * 业务配置、令牌或缓存条目的失效时刻。
     */
    private LocalDateTime expireTime;

    /**
     * MID 配置最后修改时间，用于隔离进程内短时敏感元数据缓存版本。
     */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
     */
    private Long deleted;
}
