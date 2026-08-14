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
 * @classname : TransactionShippingInfoDO
 * @date : 2026-08-14 12:43
 * @email : scott_x@163.com
 * @description : 收货人信息快照实体，按交易季度分表保存结构化明文字段，供风控、查询和商户回显使用。
 * @status : create
 */
@Data
@TableName("transaction_shipping_info")
public class TransactionShippingInfoDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String shippingInfoId;
    private String transactionId;
    private String operationId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String country;
    private String state;
    private String city;
    private String street;
    private String postal;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
