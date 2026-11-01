package com.scott.payment.settlement.api.internal.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 结算内部命令接口契约；操作人身份只能由 service-admin 的可信登录上下文注入。
 */
public final class SettlementManagementDTOs {

    private SettlementManagementDTOs() {
    }

    /**
     * Admin 受控命令；操作人由 Admin 登录上下文生成后再进入内部接口。
     */
    @Data
    public static class BatchCommandRequest {
        /**
         * 命令请求幂等键，不允许为空。
         */
        private String requestKey;
        /**
         * service-admin 查询到的批次 version，用于状态 CAS。
         */
        private Long expectedVersion;
        /**
         * 人工操作原因，不允许为空且不得包含敏感凭据。
         */
        private String reason;
        /**
         * 可信登录管理账户 ID，不接受浏览器自报身份。
         */
        private Long operatorId;
        /**
         * 可信登录管理账户展示名。
         */
        private String operatorName;
        /**
         * service-admin 授权后的角色权限快照。
         */
        private String roleSnapshot;
        /**
         * 操作客户端 IP 审计值。
         */
        private String clientIp;
        /**
         * 操作客户端 User-Agent 审计值。
         */
        private String userAgent;
        /**
         * service-admin 记录的操作时间，精度为毫秒。
         */
        private LocalDateTime operationTime;
    }

    /**
     * 入账前取消命令结果。
     */
    @Data
    public static class BatchCommandResponse {
        /**
         * 受控命令作用的正式结算批次号。
         */
        private String settlementBatchNo;
        /**
         * 命令产生的结果批次号；取消场景允许为空。
         */
        private String resultBatchNo;
        /**
         * 命令执行后的批次或操作状态。
         */
        private String resultStatus;
        /**
         * 入账前取消实际释放候选数；非取消场景允许为空。
         */
        private Integer releasedCandidateCount;
        /**
         * 人工恢复后重新进入异步处理的候选数；非恢复场景允许为空。
         */
        private Integer restoredCandidateCount;
    }

    /**
     * 审核candidatereference嵌套数据模型，定义所属聚合内固定的字段集合和传递边界。
     */
    @Data
    public static class ReviewCandidateReference {
        /**
         * 待预审结算候选数据库主键。
         */
        private Long candidateId;
        /**
         * 页面读取的候选 version，用于防止过期选择。
         */
        private Long expectedVersion;
    }

    /**
     * service-admin 注入可信操作人后的预审提交命令。
     */
    @Data
    public static class ReviewSubmitRequest {
        /**
         * 预审创建请求幂等键，不允许为空。
         */
        private String requestKey;
        /**
         * REGULAR、RESERVE_RELEASE 或 ADJUSTMENT。
         */
        private String reviewType;
        /**
         * 结算日历业务日期。
         */
        private LocalDate businessDate;
        /**
         * 候选窗口闭区间起点。
         */
        private LocalDateTime cutoffBeginTime;
        /**
         * 候选窗口开区间终点。
         */
        private LocalDateTime cutoffEndTime;
        /**
         * 待锁定候选及其期望版本，不能为空且最多一千条。
         */
        private List<ReviewCandidateReference> candidates = Collections.emptyList();
        /**
         * Maker 提交原因，不允许为空。
         */
        private String reason;
        /**
         * service-admin 可信注入的 Maker 账户 ID。
         */
        private Long operatorId;
        /**
         * Maker 账户展示名。
         */
        private String operatorName;
        /**
         * Maker 提交时角色权限快照。
         */
        private String roleSnapshot;
        /**
         * Maker 客户端 IP 审计值。
         */
        private String clientIp;
        /**
         * Maker 客户端 User-Agent 审计值。
         */
        private String userAgent;
        /**
         * Maker 实际提交时间，精度为毫秒。
         */
        private LocalDateTime operationTime;
    }

    /**
     * service-admin 注入可信 Checker 后的预审终态决策命令。
     */
    @Data
    public static class ReviewDecisionRequest {
        /**
         * 预审决策请求幂等键，不允许为空。
         */
        private String requestKey;
        /**
         * 页面读取的预审单 version，用于终态 CAS。
         */
        private Long expectedVersion;
        /**
         * APPROVE、REJECT 或 CANCEL。
         */
        private String decision;
        /**
         * Checker 决策意见，不允许为空。
         */
        private String comment;
        /**
         * service-admin 可信注入的 Checker 账户 ID。
         */
        private Long operatorId;
        /**
         * Checker 账户展示名。
         */
        private String operatorName;
        /**
         * Checker 决策时角色权限快照。
         */
        private String roleSnapshot;
        /**
         * Checker 客户端 IP 审计值。
         */
        private String clientIp;
        /**
         * Checker 客户端 User-Agent 审计值。
         */
        private String userAgent;
        /**
         * Checker 实际决策时间，精度为毫秒。
         */
        private LocalDateTime operationTime;
    }

    /**
     * service-admin 注入可信操作人后的失败复核任务恢复命令。
     */
    @Data
    public static class ReviewDecisionTaskResumeRequest {
        /**
         * 请求中的请求密钥，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String requestKey;
        /**
         * {@code expectedVersion}，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long expectedVersion;
        /**
         * 请求中的原因，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String reason;
        /**
         * 执行本次管理操作的可信登录账号 ID，用于操作审计。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long operatorId;
        /**
         * 执行本次管理操作时的账号显示名称快照，用于操作审计。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String operatorName;
        /**
         * 请求中的角色快照，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String roleSnapshot;
        /**
         * 请求中的{@code clientIp}，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String clientIp;
        /**
         * 请求中的{@code userAgent}，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String userAgent;
        /**
         * 请求中的动作时间，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime operationTime;
    }

    /**
     * 审核command响应模型，位于 结算服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     */
    @Data
    public static class ReviewCommandResponse {
        /**
         * 预审单业务号。
         */
        private String reviewOrderNo;
        /**
         * 预审当前或终态状态。
         */
        private String reviewStatus;
        /**
         * 批准后创建的正式结算批次号；其他状态为空。
         */
        private String settlementBatchNo;
        /**
         * 预审冻结候选总数。
         */
        private Integer candidateCount;
        /**
         * 统一目标 ISO 结算币种。
         */
        private String targetCurrency;
        /**
         * 目标币种 ISO 小数位。
         */
        private Integer targetCurrencyExponent;
        /**
         * 冻结净结果 CREDIT 或 DEBIT 方向。
         */
        private String netDirection;
        /**
         * 冻结非负净额，单位由 targetCurrencyExponent 决定。
         */
        private BigDecimal netAmount;
        /**
         * 决策时使用的预审单乐观锁版本。
         */
        private Long version;
    }

    /**
     * service-admin 注入可信 Maker 后创建服务端冻结的交易结算预览。
     */
    @Data
    public static class ManualReviewPreviewRequest {
        /**
         * 请求中的请求密钥，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String requestKey;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
         * </p>
         */
        private String merchantId;
        /**
         * 结算资料ID，用于定位 {@code ManualReviewPreviewRequest} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long settlementProfileId;
        /**
         * 支付类型，用于区分 {@code ManualReviewPreviewRequest} 记录的处理类别、配置维度或外部协议枚举。
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
        /**
         * 请求中的原因，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String reason;
        /**
         * 执行本次管理操作的可信登录账号 ID，用于操作审计。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long operatorId;
        /**
         * 执行本次管理操作时的账号显示名称快照，用于操作审计。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String operatorName;
        /**
         * 请求中的角色快照，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String roleSnapshot;
        /**
         * 请求中的{@code clientIp}，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String clientIp;
        /**
         * 请求中的{@code userAgent}，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String userAgent;
        /**
         * 请求中的动作时间，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime operationTime;
    }

    /**
     * 将已冻结预览提交到后台异步生成队列。
     */
    @Data
    public static class ManualReviewStartRequest {
        /**
         * 请求中的请求密钥，用于限定本次操作的输入和校验范围。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String requestKey;
        /**
         * {@code expectedVersion}，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long expectedVersion;
    }

    /**
     * 按来源币种展示的候选统计，金额不得跨币种直接合计。
     */
    @Data
    public static class ManualReviewPreviewLineResponse {
        /**
         * 来源币种，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String sourceCurrency;
        /**
         * 来源币种小数位，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private Integer sourceCurrencyExponent;
        /**
         * 交易计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long transactionCount;
        /**
         * {@code grossAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal grossAmount;
        /**
         * 平台费用金额，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal platformFeeAmount;
        /**
         * 保证金金额，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal reserveAmount;
        /**
         * {@code releasedReserveAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal releasedReserveAmount;
        /**
         * {@code netSettlementAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal netSettlementAmount;
        /**
         * 等待费用计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long pendingFeeCount;
        /**
         * 响应中的{@code reserveDelayUnit}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String reserveDelayUnit;
        /**
         * 响应中的{@code minimumReserveDelayDays}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer minimumReserveDelayDays;
        /**
         * 响应中的{@code maximumReserveDelayDays}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer maximumReserveDelayDays;
        /**
         * 响应中的{@code earliestExpectedReleaseDate}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDate earliestExpectedReleaseDate;
        /**
         * 响应中的{@code latestExpectedReleaseDate}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDate latestExpectedReleaseDate;
    }

    /**
     * 手动交易结算预览及后台生成进度。
     */
    @Data
    public static class ManualReviewTaskResponse {
        /**
         * 响应中的{@code taskNo}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String taskNo;
        /**
         * 响应中的审核订单编号，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String reviewOrderNo;
        /**
         * {@code taskStatus}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String taskStatus;
        /**
         * 审核类型，用于区分 {@code ManualReviewTaskResponse} 记录的处理类别、配置维度或外部协议枚举。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String reviewType;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
         * </p>
         */
        private String merchantId;
        /**
         * 结算资料ID，用于定位 {@code ManualReviewTaskResponse} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long settlementProfileId;
        /**
         * 结算账号ID，用于定位 {@code ManualReviewTaskResponse} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long settlementAccountId;
        /**
         * {@code targetCurrency}，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String targetCurrency;
        /**
         * {@code targetCurrencyExponent}，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private Integer targetCurrencyExponent;
        /**
         * 支付类型，用于区分 {@code ManualReviewTaskResponse} 记录的处理类别、配置维度或外部协议枚举。
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
        /**
         * 响应中的提交原因，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String submitReason;
        /**
         * 响应中的{@code businessDate}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDate businessDate;
        /**
         * 响应中的{@code cutoffEndTime}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime cutoffEndTime;
        /**
         * {@code snapshotMaxCandidateId}，用于定位 {@code ManualReviewTaskResponse} 关联的上游配置、渠道、账号、角色或业务记录。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long snapshotMaxCandidateId;
        /**
         * {@code expectedCandidateCount}，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer expectedCandidateCount;
        /**
         * {@code processedCandidateCount}，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer processedCandidateCount;
        /**
         * {@code lockedCandidateCount}，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer lockedCandidateCount;
        /**
         * 响应中的{@code progressPercent}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer progressPercent;
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
         * 预览集合，承载 {@code ManualReviewTaskResponse} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<ManualReviewPreviewLineResponse> preview = Collections.emptyList();
        /**
         * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer retryCount;
        /**
         * 处理失败码，用于补偿策略、告警聚合和后台排障，不直接暴露底层异常。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String failureCode;
        /**
         * 响应中的失败说明，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String failureMessage;
        /**
         * 响应中的{@code startedTime}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime startedTime;
        /**
         * 响应中的{@code completedTime}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime completedTime;
        /**
         * 版本，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long version;
    }

    /**
     * 大批量预审单的异步审批、驳回或取消进度。
     */
    @Data
    public static class ReviewDecisionTaskResponse {
        /**
         * 响应中的{@code taskNo}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String taskNo;
        /**
         * 响应中的审核订单编号，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String reviewOrderNo;
        /**
         * 响应中的结论动作，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String decisionAction;
        /**
         * {@code taskStatus}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String taskStatus;
        /**
         * {@code totalSegmentCount}，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer totalSegmentCount;
        /**
         * {@code processedSegmentCount}，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer processedSegmentCount;
        /**
         * 结果批次计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer resultBatchCount;
        /**
         * 响应中的{@code progressPercent}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer progressPercent;
        /**
         * 响应中的首个结算批次编号，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String firstSettlementBatchNo;
        /**
         * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer retryCount;
        /**
         * 处理失败码，用于补偿策略、告警聚合和后台排障，不直接暴露底层异常。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String failureCode;
        /**
         * 响应中的失败说明，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String failureMessage;
        /**
         * 响应中的{@code startedTime}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime startedTime;
        /**
         * 响应中的{@code completedTime}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime completedTime;
        /**
         * 版本，用于配置快照追踪、缓存代际判断或乐观锁并发控制。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Long version;
        /**
         * {@code recoverable}，用于明确 {@code ReviewDecisionTaskResponse} 当前业务分支是否成立。
         * <p>
         * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Boolean recoverable;
    }

    /**
     * service-admin 注入可信 Maker 后的冲正申请。
     */
    @Data
    public static class ReversalSubmitRequest {
        /**
         * 冲正创建请求幂等键，不允许为空。
         */
        private String requestKey;
        /**
         * 待冲正的已入账正式结算批次号。
         */
        private String originalBatchNo;
        /**
         * 页面读取的原批次 version，用于冻结前校验。
         */
        private Long expectedBatchVersion;
        /**
         * Maker 冲正申请原因，不允许为空。
         */
        private String reason;
        /**
         * service-admin 可信注入的 Maker 账户 ID。
         */
        private Long operatorId;
        /**
         * Maker 账户展示名。
         */
        private String operatorName;
        /**
         * Maker 提交时角色权限快照。
         */
        private String roleSnapshot;
        /**
         * Maker 客户端 IP 审计值。
         */
        private String clientIp;
        /**
         * Maker 客户端 User-Agent 审计值。
         */
        private String userAgent;
        /**
         * Maker 实际提交时间，精度为毫秒。
         */
        private LocalDateTime operationTime;
    }

    /**
     * service-admin 注入可信 Checker 后的冲正决策。
     */
    @Data
    public static class ReversalDecisionRequest {
        /**
         * 冲正决策请求幂等键，不允许为空。
         */
        private String requestKey;
        /**
         * 页面读取的冲正单 version，用于终态 CAS。
         */
        private Long expectedVersion;
        /**
         * APPROVE 或 REJECT。
         */
        private String decision;
        /**
         * Checker 决策意见，不允许为空。
         */
        private String comment;
        /**
         * service-admin 可信注入的 Checker 账户 ID。
         */
        private Long operatorId;
        /**
         * Checker 账户展示名。
         */
        private String operatorName;
        /**
         * Checker 决策时角色权限快照。
         */
        private String roleSnapshot;
        /**
         * Checker 客户端 IP 审计值。
         */
        private String clientIp;
        /**
         * Checker 客户端 User-Agent 审计值。
         */
        private String userAgent;
        /**
         * Checker 实际决策时间，精度为毫秒。
         */
        private LocalDateTime operationTime;
    }

    /**
     * 冲正command响应模型，位于 结算服务，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     */
    @Data
    public static class ReversalCommandResponse {
        /**
         * 冲正申请单号。
         */
        private String reversalOrderNo;
        /**
         * PENDING_APPROVAL、APPROVED 或 REJECTED。
         */
        private String reversalStatus;
        /**
         * 被冲正的原正式批次号。
         */
        private String originalBatchNo;
        /**
         * 批准后创建的独立反向批次号；其他状态为空。
         */
        private String reversalBatchNo;
        /**
         * 原批次所属平台商户号。
         */
        private String merchantId;
        /**
         * 原批次目标 ISO 结算币种。
         */
        private String currency;
        /**
         * 原净结果 CREDIT 或 DEBIT 方向。
         */
        private String netDirection;
        /**
         * 原净结果非负金额，单位由 currency 的 ISO exponent 决定。
         */
        private BigDecimal netAmount;
        /**
         * 冲正单当前乐观锁版本。
         */
        private Long version;
    }
}
