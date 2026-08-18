package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionBillingInfoDO
 * @date : 2026-08-14 12:43
 * @email : scott_x@163.com
 * @description : 持卡人账单信息快照实体，按确认后的数据契约使用结构化明文列持久化并禁止输出到普通日志。
 * @status : create
 */
@Data
@TableName("transaction_billing_info")
public class TransactionBillingInfoDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String billingInfoId;
    private String transactionId;
    private String operationId;
    @TableField("first_name")
    private String firstName;
    @TableField("last_name")
    private String lastName;
    private String email;
    private String phone;
    @TableField("billing_country")
    private String country;
    @TableField("billing_state")
    private String state;
    @TableField("billing_city")
    private String city;
    private String street;
    @TableField("billing_postal_code")
    private String postal;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
