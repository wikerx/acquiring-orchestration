package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 持卡人账单信息快照实体，按确认后的数据契约使用结构化明文列持久化并禁止输出到普通日志。
 */
@Data
@TableName("transaction_billing_info")
public class TransactionBillingInfoDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * {@code TransactionBillingInfoDO} 数据库主键，用于唯一标识当前记录。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 账单信息ID，用于定位 {@code TransactionBillingInfoDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String billingInfoId;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;
    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     */
    private String operationId;
    /**
     * 账单联系人名字，日志和管理端展示时必须按个人信息规则脱敏。
     */
    @TableField("first_name")
    private String firstName;
    /**
     * 账单联系人姓氏，日志和管理端展示时必须按个人信息规则脱敏。
     */
    @TableField("last_name")
    private String lastName;
    /**
     * 邮件，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
     */
    private String email;
    /**
     * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
     */
    private String phone;
    /**
     * 国家或地区，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。
     */
    @TableField("billing_country")
    private String country;
    /**
     * 状态，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
     */
    @TableField("billing_state")
    private String state;
    /**
     * 城市，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
     */
    @TableField("billing_city")
    private String city;
    /**
     * 街道，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
     */
    private String street;
    /**
     * 邮编，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
     */
    @TableField("billing_postal_code")
    private String postal;
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
