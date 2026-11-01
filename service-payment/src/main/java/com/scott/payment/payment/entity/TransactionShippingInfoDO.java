package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收货人信息快照实体，按交易季度分表保存结构化明文字段，供风控、查询和商户回显使用。
 */
@Data
@TableName("transaction_shipping_info")
public class TransactionShippingInfoDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * {@code TransactionShippingInfoDO} 数据库主键，用于唯一标识当前记录。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * {@code shippingInfoId}，用于定位 {@code TransactionShippingInfoDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String shippingInfoId;
    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;
    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     */
    private String operationId;
    /**
     * 首个名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String firstName;
    /**
     * {@code lastName}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
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
    private String country;
    /**
     * 状态，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
     */
    private String state;
    /**
     * 城市，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
     */
    private String city;
    /**
     * 街道，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
     */
    private String street;
    /**
     * 邮编，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
     */
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
