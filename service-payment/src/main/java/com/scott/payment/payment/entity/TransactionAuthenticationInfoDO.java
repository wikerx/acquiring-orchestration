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

    /**
     * {@code TransactionAuthenticationInfoDO} 数据库主键，用于唯一标识当前记录。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 认证信息ID，用于定位 {@code TransactionAuthenticationInfoDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
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
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String authenticationType;
    /**
     * 认证状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private String authenticationStatus;
    /**
     * 持久化的认证来源，用于还原当前记录的业务事实。
     */
    private String authenticationSource;
    /**
     * {@code threeDsVersion}，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
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
    /**
     * {@code cavv}，表示卡组织或 3DS 认证链路使用的安全认证值。
     */
    private String cavv;
    /**
     * {@code xid}，用于定位 {@code TransactionAuthenticationInfoDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String xid;
    /**
     * 持久化的{@code liabilityShift}，用于还原当前记录的业务事实。
     */
    private Integer liabilityShift;
    /**
     * 持久化的{@code challengeRequired}，用于还原当前记录的业务事实。
     */
    private Integer challengeRequired;
    /**
     * {@code challengeStatus}，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private String challengeStatus;
    /**
     * {@code authenticationRedirectUrlHash}，表示回调、通知、来源站点或远程接口地址。
     */
    private String authenticationRedirectUrlHash;
    /**
     * 认证结果编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
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
