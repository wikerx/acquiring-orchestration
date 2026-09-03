package com.scott.payment.merchant.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFinanceDTOs
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端当前费率和资金账户只读响应模型，不包含模板库来源元数据。
 * @status : create
 */
public final class MerchantFinanceDTOs {
    private MerchantFinanceDTOs() { }

    /** 只读明细分页条件。 */
    @Data
    public static class DetailQuery {
        /** 页码，从 1 开始。 */
        private int pageNo = 1;
        /** 每页条数，服务端限制为 1 至 200。 */
        private int pageSize = 10;
        /** 流水号、业务单号、交易号或摘要关键字，允许为空。 */
        private String keyword;
        /** AVAILABLE 或 RESERVE，允许为空。 */
        private String balanceType;
        /** 余额变动业务类型，允许为空。 */
        private String businessType;
        /** 入账时间范围起点，包含，使用系统时间。 */
        private LocalDateTime postedStartTime;
        /** 入账时间范围终点，包含，使用系统时间。 */
        private LocalDateTime postedEndTime;
        /** @return 修正为至少 1 的页码。 */
        public int safePageNo() { return Math.max(pageNo, 1); }
        /** @return 修正到 1 至 200 范围内的每页条数。 */
        public int safePageSize() { return Math.min(Math.max(pageSize, 1), 200); }
    }

    /** 费率阶梯。 */
    @Data
    public static class FeeTierResponse {
        /**
         * {@code FeeTierResponse} 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long id;
        /** 月累计笔数或 USD 归一金额下界，包含。 */
        private BigDecimal lowerBound;
        /** 月累计笔数或 USD 归一金额上界，不包含；末档为空。 */
        private BigDecimal upperBound;
        /** 百分比数值，例如 2.3 表示 2.3%。 */
        private BigDecimal percentageRate;
        /** 固定费用，币种恒为 USD。 */
        private BigDecimal fixedAmountUsd;
        /** 最低费用，币种恒为 USD；不设下限时为空。 */
        private BigDecimal minimumAmountUsd;
        /** 最高费用，币种恒为 USD；不设上限时为空。 */
        private BigDecimal maximumAmountUsd;
    }

    /** 当前生效费率规则。 */
    @Data
    public static class FeeRuleResponse {
        /**
         * 费用规则响应 数据库主键，用于唯一标识当前记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long id;
        /**
         * 费用类别，用于区分交易手续费、退款费、风控费、争议费和结算换汇费。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String feeCategory;
        /**
         * 费用规则名称，用于运营识别同一费用版本内的原子匹配规则。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String ruleName;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String transactionType;
        /**
         * 支付类型，用于区分 费用规则响应 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentType;
        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentMethod;
        /** 同一逻辑规则覆盖的交易类型，用于商户端按配置维度展示。 */
        private List<String> transactionTypes = new ArrayList<>();
        /** 同一逻辑规则覆盖的支付类型，用于商户端按配置维度展示。 */
        private List<String> paymentTypes = new ArrayList<>();
        /** 同一逻辑规则覆盖的支付方式，用于商户端按配置维度展示。 */
        private List<String> paymentMethods = new ArrayList<>();
        /**
         * 风控服务类型，用于区分内部风控、外部风控和 3DS 服务费用。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String riskServiceType;
        /**
         * 计费触发点，明确费用在请求、成功、失败或其它受控事件发生时计提。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String chargeTrigger;
        /**
         * 费用计算模式，决定当前规则采用标准费率还是阶梯费率。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String feeMode;
        /** 百分比数值，例如 2.3 表示 2.3%，按标签金额计提。 */
        private BigDecimal percentageRate;
        /** 固定费用，币种恒为 USD。 */
        private BigDecimal fixedAmountUsd;
        /** 最低费用，币种恒为 USD；不设下限时为空。 */
        private BigDecimal minimumAmountUsd;
        /** 最高费用，币种恒为 USD；不设上限时为空。 */
        private BigDecimal maximumAmountUsd;
        /**
         * 阶梯累计指标，用于区分月累计交易笔数和 USD 归一交易金额。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String tierMetric;
        /**
         * 阶梯累计周期，当前用于声明费用阶梯按哪个统计周期重置。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String tierPeriod;
        /**
         * {@code tiers}集合，承载 费用规则响应 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<FeeTierResponse> tiers = new ArrayList<>();
    }

    /** 当前商户生效费用配置。 */
    @Data
    public static class CurrentFeeResponse {
        /**
         * 展示名称，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String displayName;
        /**
         * 业务版本号，用于区分同一配置或方案的不可变版本。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer versionNo;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime effectiveTime;
        /**
         * 响应中的保证金汇率，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private BigDecimal reserveRate;
        /**
         * 响应中的保证金延迟天数，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer reserveDelayDays;
        /**
         * 响应中的{@code initialDelayUnit}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String initialDelayUnit;
        /**
         * 响应中的初始延迟天数，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer initialDelayDays;
        /**
         * 响应中的{@code regularDelayUnit}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String regularDelayUnit;
        /**
         * 响应中的{@code regularDelayDays}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer regularDelayDays;
        /**
         * 响应中的结算频率，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String settlementFrequency;
        /**
         * 响应中的频率日，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer frequencyDay;
        /**
         * 规则集合，承载 当前费用响应 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<FeeRuleResponse> rules = new ArrayList<>();
    }

    /** 按币种独立展示的在途余额。 */
    @Data
    public static class CurrencyBalanceResponse {
        /** ISO 4217 三位币种代码。 */
        private String currency;
        /** 该币种独立汇总金额，不与其他币种直接相加。 */
        private BigDecimal amount;
    }

    /** 当前商户资金账户。 */
    @Data
    public static class FundAccountResponse {
        /** 资金账户数据库主键。 */
        private Long id;
        /** 平台资金账户号，不包含敏感银行账号信息。 */
        private String accountNo;
        /** ISO 4217 三位结算币种，一期每个商户仅一个。 */
        private String settlementCurrency;
        /** 可用余额，单位为 settlementCurrency，允许为负。 */
        private BigDecimal availableBalance;
        /** 保证金余额，单位为 settlementCurrency，不允许为负。 */
        private BigDecimal reserveBalance;
        /** 按标签币种分别统计的在途余额。 */
        private List<CurrencyBalanceResponse> pendingBalances = new ArrayList<>();
        /** 人工账户状态：NORMAL、FROZEN 或 CLOSED。 */
        private String accountStatus;
        /** 1 表示负余额限制主动逆向交易，0 表示未限制。 */
        private Integer reverseRestricted;
        /** 当前状态是否允许充值或人工入账。 */
        private Boolean creditAllowed;
        /** 当前状态是否允许资金转出。 */
        private Boolean debitAllowed;
        /** 当前状态是否允许提现。 */
        private Boolean withdrawalAllowed;
        /** 当前状态是否允许交易或保证金结算。 */
        private Boolean settlementAllowed;
        /** 账户状态和负余额规则共同判定的主动逆向交易能力。 */
        private Boolean reverseTransactionAllowed;
        /** 账户最近修改系统时间。 */
        private LocalDateTime updateTime;
    }

    /** 商户余额流水，不展示内部幂等键等运维字段。 */
    @Data
    public static class FundLedgerResponse {
        /** 余额流水数据库主键。 */
        private Long id;
        /** 平台唯一余额流水号。 */
        private String ledgerNo;
        /** AVAILABLE 或 RESERVE。 */
        private String balanceType;
        /** 余额变动业务类型。 */
        private String businessType;
        /** 面向商户核对的变动摘要。 */
        private String summary;
        /** 来源业务单号。 */
        private String businessNo;
        /** 关联交易号，非交易类变动时为空。 */
        private String transactionId;
        /** 本笔变动 ISO 4217 三位币种代码。 */
        private String currency;
        /** CREDIT 表示增加，DEBIT 表示减少。 */
        private String direction;
        /** 发生金额，单位为 currency，始终为非负数。 */
        private BigDecimal amount;
        /** 操作前余额，单位为 currency，可为负。 */
        private BigDecimal balanceBefore;
        /** 操作后余额，单位为 currency，可为负。 */
        private BigDecimal balanceAfter;
        /** 同一账户内严格递增序号，用于核对连续余额。 */
        private Long accountSequence;
        /** 原操作人名称快照。 */
        private String operatorName;
        /** 最终复核人名称快照，自动入账时允许为空。 */
        private String reviewerName;
        /** 人工余额变动的完整业务原因，自动入账时允许为空。 */
        private String operationReason;
        /** 审核和复核意见摘要，自动入账时允许为空。 */
        private String reviewComment;
        /** 来源业务事件发生系统时间。 */
        private LocalDateTime businessTime;
        /** 可用余额实际发生变化的系统时间。 */
        private LocalDateTime postedTime;
    }

}
