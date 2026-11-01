package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商户通知任务实体，位于 service-payment 持久化层，保存交易结果通知商户的任务状态、配置快照和重试计划。
 */
@Data
@TableName("transaction_merchant_notification")
public class TransactionMerchantNotificationDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * {@code TransactionMerchantNotificationDO} 数据库主键，用于唯一标识当前记录。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 通知ID，用于定位 {@code TransactionMerchantNotificationDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String notifyId;

    /**
     * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
     */
    private String transactionId;

    /**
     * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
     */
    private String operationId;

    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;

    /**
     * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
     */
    private String merchantOrderNo;

    /**
     * 通知类型，用于区分交易成功、失败、处理中或补偿重试等商户通知场景。
     */
    private String notifyType;

    /**
     * {@code eventType}，用于区分 {@code TransactionMerchantNotificationDO} 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String eventType;

    /**
     * 通知状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private String notifyStatus;

    /**
     * 通知配置版本，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String notifyConfigVersion;

    /**
     * 商户回调地址明文；只允许用于通知投递，日志和管理页面继续使用脱敏摘要。
     */
    private String callbackUrl;

    /**
     * 与同步 OpenAPI 响应字段口径一致的商户通知业务载荷明文 JSON。
     */
    private String payloadJson;

    /**
     * {@code targetUrlHash}，表示回调、通知、来源站点或远程接口地址。
     */
    private String targetUrlHash;

    /**
     * {@code targetUrlMasked}，表示回调、通知、来源站点或远程接口地址。
     */
    private String targetUrlMasked;

    /**
     * {@code payloadJsonMasked}，表示请求体、响应体或消息载荷，日志中只能保留脱敏摘要。
     */
    private String payloadJsonMasked;

    /**
     * {@code signType}，用于区分 {@code TransactionMerchantNotificationDO} 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String signType;

    /**
     * 持久化的{@code lastAttemptNo}，用于还原当前记录的业务事实。
     */
    private Integer lastAttemptNo;

    /**
     * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
     */
    private Integer maxRetryCount;

    /**
     * 持久化的{@code nextRetryTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime nextRetryTime;

    /**
     * 持久化的成功时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime successTime;

    /**
     * 持久化的{@code failReason}，用于还原当前记录的业务事实。
     */
    private String failReason;

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
     * 版本，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private Integer version;

    /**
     * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
     */
    private Integer deleted;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;

    /**
     * 记录最后更新时间，持久化精度为毫秒。
     */
    private LocalDateTime updateTime;
}
