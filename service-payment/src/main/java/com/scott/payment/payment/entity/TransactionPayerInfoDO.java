package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionPayerInfoDO
 * @date : 2026-08-14 12:43
 * @email : scott_x@163.com
 * @description : 付款人信息快照实体，按商户契约使用结构化明文列保存，并保留名单检索所需的不可逆摘要。
 * @status : create
 */
@Data
@TableName("transaction_payer_info")
public class TransactionPayerInfoDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String payerInfoId;
    private String transactionId;
    private String operationId;
    private String payerId;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String country;
    private String state;
    private String city;
    private String street;
    private String postal;
    private String ipAddress;
    private String sessionId;
    private String browserInfoJson;
    private String userAgent;
    private String payerEmailHash;
    private String payerPhoneHash;
    private String ipAddressHash;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
