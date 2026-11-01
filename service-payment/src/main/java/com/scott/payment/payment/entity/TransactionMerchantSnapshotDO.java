package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 交易商户快照实体，按交易动作保存子商户 JSON 以及渠道、费用版本和路由等冻结配置。
 */
@Data
@TableName("transaction_merchant_snapshot")
public class TransactionMerchantSnapshotDO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * {@code TransactionMerchantSnapshotDO} 数据库主键，用于唯一标识当前记录。
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 快照ID，用于定位 {@code TransactionMerchantSnapshotDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String snapshotId;
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
     * 持久化的{@code subMerchantInfoJson}，用于还原当前记录的业务事实。
     */
    private String subMerchantInfoJson;
    /**
     * 商户名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String merchantName;
    /**
     * 商户国家或地区，表示国家或地区代码，用于路由、风控、卡 BIN 识别或地域限制。
     */
    private String merchantCountry;
    /**
     * 商户类别编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String merchantCategoryCode;
    /**
     * 商户状态，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private String merchantStatus;
    /**
     * 渠道ID，用于定位 {@code TransactionMerchantSnapshotDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
     * </p>
     */
    private Long channelId;
    /**
     * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
     */
    private String channelCode;
    /**
     * {@code channelMidConfigId}，用于定位 {@code TransactionMerchantSnapshotDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
     * </p>
     */
    private Long channelMidConfigId;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String channelMerchantId;
    /**
     * {@code terminalId}，用于定位 {@code TransactionMerchantSnapshotDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private String terminalId;
    /**
     * {@code channelMidMetadataJson}，用于定位渠道商户号配置或渠道侧 MID。
     */
    private String channelMidMetadataJson;
    /**
     * 持久化的{@code settlementConfigSnapshotJson}，用于还原当前记录的业务事实。
     */
    private String settlementConfigSnapshotJson;
    /**
     * 持久化的{@code feeConfigSnapshotJson}，用于还原当前记录的业务事实。
     */
    private String feeConfigSnapshotJson;
    /**
     * 持久化的{@code internalRiskConfigSnapshotJson}，用于还原当前记录的业务事实。
     */
    private String internalRiskConfigSnapshotJson;
    /**
     * 持久化的{@code routeConfigSnapshotJson}，用于还原当前记录的业务事实。
     */
    private String routeConfigSnapshotJson;
    /**
     * 费用方案ID，用于定位 {@code TransactionMerchantSnapshotDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private Long feePlanId;
    /**
     * 费用方案版本ID，用于定位 {@code TransactionMerchantSnapshotDO} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private Long feePlanVersionId;
    /**
     * 费用方案版本编号，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * </p>
     */
    private Integer feePlanVersionNo;
    /**
     * {@code feeSnapshotHash}，用于以不可逆摘要关联敏感原文或大报文。
     */
    private String feeSnapshotHash;
    /**
     * 持久化的费用快照时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime feeSnapshotTime;
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
