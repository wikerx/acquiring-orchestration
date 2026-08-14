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
    private String transactionId;
    private String operationId;
    private String authenticationType;
    private String authenticationStatus;
    private String authenticationSource;
    private String threeDsVersion;
    private String threeDsTransactionId;
    private String threeDsServerTransactionId;
    private String acsTransactionId;
    private String dsTransactionId;
    private String eci;
    private String cavv;
    private String xid;
    private Integer liabilityShift;
    private Integer challengeRequired;
    private String challengeStatus;
    private String authenticationRedirectUrlHash;
    private String authenticationResultCode;
    private String authenticationResultMessage;
    private LocalDateTime authenticationTime;
    private String authenticationExtraJson;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
