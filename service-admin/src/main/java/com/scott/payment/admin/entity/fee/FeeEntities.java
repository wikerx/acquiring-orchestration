package com.scott.payment.admin.entity.fee;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeEntities
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 费用方案、可编辑草稿、审核后不可变版本、规则、阶梯和试算记录的持久化模型集合。
 * @status : create
 */
public final class FeeEntities {

    private FeeEntities() {
    }

    /** 费用模板或商户费用方案主记录。 */
    @Data
    @TableName("fee_plan")
    public static class FeePlanDO {
        /**
         * {@code FeePlanDO} 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 方案编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String planCode;
        /**
         * 方案名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String planName;
        /** TEMPLATE 或 MERCHANT；商户方案必须关联 merchantId。 */
        private String planType;
        /** 商户号；模板方案为空。 */
        private String merchantId;
        /** 商户配置复制或调整时的来源模板主键，可为空。 */
        private Long sourceTemplateId;
        /** 复制时锁定的模板版本号，可为空且不随模板后续变更。 */
        private Integer sourceTemplateVersionNo;
        /** TEMPLATE、TEMPLATE_CUSTOMIZED 或 INDEPENDENT。 */
        private String originType;
        /** 当前已审核生效版本主键；未生效时为空。 */
        private Long currentVersionId;
        /** 当前已审核生效版本号；未生效时为空。 */
        private Integer currentVersionNo;
        /** ENABLED、DISABLED 或 ARCHIVED；归档代替物理删除。 */
        private String status;
        /**
         * 备注，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String remark;
        /**
         * 记录创建人账号标识，用于操作审计。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String createBy;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新人账号标识，用于操作审计。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String updateBy;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime updateTime;
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         * <p>
         * 单位：无；格式：布尔值或 0/1 标识；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的真假取值；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long deleted;
    }

    /** 费用方案版本；草稿可编辑，提交审核后保持不可变。 */
    @Data
    @TableName("fee_plan_version")
    public static class FeePlanVersionDO {
        /**
         * {@code FeePlanVersionDO} 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 方案ID，用于定位 {@code FeePlanVersionDO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long planId;
        /** 方案内从 1 递增且不复用的版本号。 */
        private Integer versionNo;
        /** DRAFT、PENDING_REVIEW、ACTIVE、REJECTED 或 SUPERSEDED。 */
        private String versionStatus;
        /**
         * 变更类型，用于区分 {@code FeePlanVersionDO} 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String changeType;
        /**
         * 来源模板ID，用于定位 {@code FeePlanVersionDO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long sourceTemplateId;
        /**
         * 来源模板版本编号，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Integer sourceTemplateVersionNo;
        /**
         * {@code originType}，用于区分 {@code FeePlanVersionDO} 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String originType;
        /** 滚动保证金比例，例如 10 表示 10%。 */
        private BigDecimal reserveRate;
        /** 滚动保证金留存周期单位：T 工作日、D 自然日。 */
        private String reserveDelayUnit;
        /** 滚动保证金 T/D+N 留存天数。 */
        private Integer reserveDelayDays;
        /** 商户单一结算币种快照；模板版本为空，商户版本使用 ISO 4217 三位代码。 */
        private String settlementCurrency;
        /** 首次与常规结算周期共用的单位。 */
        private String initialDelayUnit;
        /** 首次结算延迟天数，最小为 1。 */
        private Integer initialDelayDays;
        /** 常规结算延迟天数，最小为 1。 */
        private Integer regularDelayDays;
        /** DAILY、WEEKLY、BIWEEKLY 或 MONTHLY。 */
        private String settlementFrequency;
        /** 周结为 1 至 7，月结为 1 至 28，日结为空。 */
        private Integer frequencyDay;
        /**
         * 持久化的变更原因，用于还原当前记录的业务事实。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String changeReason;
        /** 草稿阶段为最后保存账号，提交后为本次提交账号。 */
        private Long submitById;
        /** 草稿阶段为最后保存人，提交后为本次提交人名称快照。 */
        private String submitByName;
        /** 草稿阶段为最后保存时间，提交后为提交审核时间，不等同于生效时间。 */
        private LocalDateTime submitTime;
        /** 审核账号 ID；待审核时为空，且不能等于提交账号。 */
        private Long reviewById;
        /**
         * 审核按名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String reviewByName;
        /**
         * 持久化的{@code reviewComment}，用于还原当前记录的业务事实。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String reviewComment;
        /** 审核动作系统时间；待审核时为空。 */
        private LocalDateTime reviewTime;
        /** 审核通过时间即生效时间；未通过时为空。 */
        private LocalDateTime effectiveTime;
        /** 被后续版本替代的系统时间；当前版本为空。 */
        private LocalDateTime supersededTime;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime updateTime;
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         * <p>
         * 单位：无；格式：布尔值或 0/1 标识；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的真假取值；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long deleted;
    }

    /** 单个交易匹配维度下的费用规则。 */
    @Data
    @TableName("fee_rule")
    public static class FeeRuleDO {
        /**
         * {@code FeeRuleDO} 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 方案版本ID，用于定位 {@code FeeRuleDO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long planVersionId;
        /** 同一条页面多选规则展开后的分组编码；历史原子规则允许为空。 */
        private String ruleGroupCode;
        /** TRANSACTION_FEE、REFUND_FEE、RISK_FEE、DISPUTE_FEE 或 SETTLEMENT_FX_FEE。 */
        private String feeCategory;
        /**
         * 费用规则名称，用于运营识别同一费用版本内的原子匹配规则。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String ruleName;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String transactionType;
        /**
         * 支付类型，用于区分 {@code FeeRuleDO} 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String paymentType;
        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String paymentMethod;
        /** INTERNAL、EXTERNAL、THREE_DS；非风控费用使用 NONE。 */
        private String riskServiceType;
        /** NO_CHARGE、SUCCESS、SUCCESS_OR_FAILURE、ON_CALL；非风控费用使用 NOT_APPLICABLE。 */
        private String chargeTrigger;
        /**
         * 费用计算模式，决定当前规则采用标准费率还是阶梯费率。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String feeMode;
        /** 百分比数值，例如 2.3 表示 2.3%，按标签币种金额计提。 */
        private BigDecimal percentageRate;
        /** 固定费用，币种恒为 USD，不能为空。 */
        private BigDecimal fixedAmountUsd;
        /** 最低费用，币种恒为 USD；不设下限时为空。 */
        private BigDecimal minimumAmountUsd;
        /** 最高费用，币种恒为 USD；不设上限时为空。 */
        private BigDecimal maximumAmountUsd;
        /** COUNT 或 AMOUNT；标准费率时为空。 */
        private String tierMetric;
        /** 阶梯累计周期，本期固定为 MONTH；标准费率时为空。 */
        private String tierPeriod;
        /**
         * 排序号，数值越小越优先展示或匹配。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Integer sortNo;
        /**
         * 备注，用于保存人工备注、交易说明或配置补充说明。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String remark;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createTime;
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         * <p>
         * 单位：无；格式：布尔值或 0/1 标识；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的真假取值；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long deleted;
    }

    /** 月累计笔数或 USD 金额阶梯。 */
    @Data
    @TableName("fee_rule_tier")
    public static class FeeRuleTierDO {
        /**
         * {@code FeeRuleTierDO} 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 费用规则ID，用于定位 {@code FeeRuleTierDO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long feeRuleId;
        /** 月累计笔数或归一到 USD 的累计金额下界，包含。 */
        private BigDecimal lowerBound;
        /** 月累计笔数或归一到 USD 的累计金额上界，不包含；末档为空。 */
        private BigDecimal upperBound;
        /** 当前档百分比数值，例如 2.3 表示 2.3%。 */
        private BigDecimal percentageRate;
        /** 当前档固定费用，币种恒为 USD。 */
        private BigDecimal fixedAmountUsd;
        /** 当前档最低费用，币种恒为 USD；不设下限时为空。 */
        private BigDecimal minimumAmountUsd;
        /** 当前档最高费用，币种恒为 USD；不设上限时为空。 */
        private BigDecimal maximumAmountUsd;
        /**
         * 排序号，数值越小越优先展示或匹配。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Integer sortNo;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createTime;
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         * <p>
         * 单位：无；格式：布尔值或 0/1 标识；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的真假取值；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long deleted;
    }

    /** 无副作用试算的输入和结果快照。 */
    @Data
    @TableName("fee_simulation_record")
    public static class FeeSimulationRecordDO {
        /**
         * {@code FeeSimulationRecordDO} 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 持久化的{@code simulationNo}，用于还原当前记录的业务事实。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String simulationNo;
        /**
         * 方案版本ID，用于定位 {@code FeeSimulationRecordDO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long planVersionId;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
         * </p>
         */
        private String merchantId;
        /**
         * 费用类别，用于区分交易手续费、退款费、风控费、争议费和结算换汇费。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String feeCategory;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String transactionType;
        /**
         * 支付类型，用于区分 {@code FeeSimulationRecordDO} 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String paymentType;
        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String paymentMethod;
        /** INTERNAL、EXTERNAL、THREE_DS；非风控费用试算使用 NONE。 */
        private String riskServiceType;
        /** 试算标签金额，币种由 labelCurrency 指定，不做展示层舍入。 */
        private BigDecimal labelAmount;
        /** 标签金额 ISO 4217 三位币种代码。 */
        private String labelCurrency;
        /** 系统选用的标签币种到 USD 正向结算汇率，禁止取反向汇率倒数。 */
        private BigDecimal labelToUsdRate;
        /** 标签金额按试算汇率归一后的 USD 快照。 */
        private BigDecimal labelAmountUsd;
        /** 系统业务汇率记录 ID；USD 恒等汇率允许为空。 */
        private Long settlementRateId;
        /** 本次试算选用的汇率来源编码，不包含敏感信息。 */
        private String settlementRateSource;
        /** 被选汇率的生效时间，使用系统业务时间。 */
        private LocalDateTime rateEffectiveTime;
        /** 本次试算解析汇率的估值时间，使用系统业务时间。 */
        private LocalDateTime rateValuationTime;
        /** 本次交易发生前的当月累计笔数。 */
        private Long monthlyCountBefore;
        /** 本次交易发生前的当月累计金额，已归一为 USD。 */
        private BigDecimal monthlyAmountUsdBefore;
        /**
         * 本次费用计算命中的规则主键，用于审计和复现计算过程。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long matchedRuleId;
        /**
         * 本次费用计算命中的阶梯主键；标准费率或未命中阶梯时为空。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long matchedTierId;
        /** 按标签币种计算的百分比费用，尚未换算为 USD。 */
        private BigDecimal percentageFeeLabel;
        /** 百分比费用换算并叠加固定费用后的 USD 金额，尚未应用上下限。 */
        private BigDecimal rawFeeUsd;
        /** 应用最低和最高费用后的最终 USD 试算金额。 */
        private BigDecimal finalFeeUsd;
        /** 试算使用的滚动保证金比例快照。 */
        private BigDecimal reserveRate;
        /**
         * 试算保证金金额，按规则计算并换算为 USD，仅用于预览不产生资金流水。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal reserveAmountUsd;
        /**
         * 预计净结算金额，币种为 USD，仅用于费用试算展示。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private BigDecimal estimatedNetSettlementUsd;
        /**
         * 费用计算公式快照，用于运营展示和事后审计，不作为重新计算的输入。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String formulaSnapshot;
        /**
         * 净结算金额公式快照，用于解释费用和保证金如何影响预计净入账金额。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String netSettlementFormulaSnapshot;
        /**
         * 执行本次管理操作的可信登录账号 ID，用于操作审计。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long operatorId;
        /**
         * 执行本次管理操作时的账号显示名称快照，用于操作审计。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String operatorName;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createTime;
    }

    /** 费用试算逐项计算快照，不通过展示公式反推历史选择或金额。 */
    @Data
    @TableName("fee_simulation_record_detail")
    public static class FeeSimulationRecordDetailDO {
        /**
         * {@code FeeSimulationRecordDetailDO} 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * {@code simulationRecordId}，用于定位 {@code FeeSimulationRecordDetailDO} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long simulationRecordId;
        /**
         * 持久化的{@code lineNo}，用于还原当前记录的业务事实。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Integer lineNo;
        /** FEE 或 RESERVE。 */
        private String itemType;
        /**
         * 费用类别，用于区分交易手续费、退款费、风控费、争议费和结算换汇费。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String feeCategory;
        /**
         * 风控服务类型，用于区分内部风控、外部风控和 3DS 服务费用。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String riskServiceType;
        /** CALCULATED、NOT_APPLICABLE 或 NOT_CONFIGURED。 */
        private String calculationStatus;
        /**
         * 是否计入费用合计；1 表示计入，0 表示仅展示该费用明细。
         * <p>
         * 单位：无；格式：布尔值或 0/1 标识；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的真假取值；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Integer includedInFeeTotal;
        /**
         * 计费触发点，明确费用在请求、成功、失败或其它受控事件发生时计提。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String chargeTrigger;
        /**
         * 费用规则名称，用于运营识别同一费用版本内的原子匹配规则。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String ruleName;
        /**
         * 费用计算模式，决定当前规则采用标准费率还是阶梯费率。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String feeMode;
        /**
         * 本次费用计算命中的规则主键，用于审计和复现计算过程。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long matchedRuleId;
        /**
         * 本次费用计算命中的阶梯主键；标准费率或未命中阶梯时为空。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private Long matchedTierId;
        /**
         * 按标签金额计算出的百分比费用，尚未换算为 USD，也未应用固定费和上下限。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private BigDecimal percentageFeeLabel;
        /**
         * 百分比费用币种，与交易标签币种保持一致。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String percentageFeeCurrency;
        /**
         * 百分比费用换算为 USD 后与固定单笔费相加得到的原始费用，尚未应用最低和最高限制。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private BigDecimal rawFeeUsd;
        /**
         * 应用最低和最高限制后的最终费用，币种恒为 USD。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private BigDecimal finalFeeUsd;
        /**
         * 费用上下限应用结果，用于标识未触发限制、命中最低费用或命中最高费用。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
         * </p>
         */
        private String appliedLimit;
        /**
         * 费用计算公式快照，用于运营展示和事后审计，不作为重新计算的输入。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：数据库表记录或持久化写入对象。
         * </p>
         */
        private String formulaSnapshot;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
         * 字段关系：与创建人、更新人和版本字段共同形成记录审计信息。
         * </p>
         */
        private LocalDateTime createTime;
    }
}
