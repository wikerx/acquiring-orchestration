package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 交易认证审计实体。仅保存 3DS 协议和状态摘要，不保存 CAVV、原始报文或浏览器认证材料。
 */
@Data
@TableName("transaction_authentication_info")
public class TransactionAuthenticationInfoDO implements Serializable {

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;

    private String authenticationInfoId;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;
    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     */
    private String operationId;
    /**
     * 认证类型，用于区分 {@code TransactionAuthenticationInfoDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String authenticationType;

    private String authenticationStatus;
    /**
     * 持久化的认证来源，用于还原当前记录的业务事实。
     */
    private String authenticationSource;

    private String threeDsVersion;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String threeDsTransactionId;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String threeDsServerTransactionId;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String acsTransactionId;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String dsTransactionId;
    /**
     * 持久化的{@code eci}，用于还原当前记录的业务事实。
     */
    private String eci;

    private String cavv;

    private String xid;
    /**
     * 持久化的{@code liabilityShift}，用于还原当前记录的业务事实。
     */
    private Integer liabilityShift;
    /**
     * 持久化的{@code challengeRequired}，用于还原当前记录的业务事实。
     */
    private Integer challengeRequired;

    private String challengeStatus;

    private String authenticationRedirectUrlHash;

    private String authenticationResultCode;
    /**
     * 持久化的认证结果说明，用于还原当前记录的业务事实。
     */
    private String authenticationResultMessage;
    /**
     * 持久化的认证时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime authenticationTime;
    /**
     * 持久化的{@code authenticationExtraJson}，用于还原当前记录的业务事实。
     */
    private String authenticationExtraJson;
    /**
     * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
     */
    private LocalDateTime transactionDateTime;
    /**
     * 交易受理时刻对应的 UTC 时间，用于跨时区排序和对账。
     */
    private LocalDateTime transactionUtcTime;
    /**
     * 交易业务时区，使用 IANA 时区标识解释本地交易时间。
     */
    private String transactionTimeZone;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;
    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    private LocalDateTime updateTime;
}
