package com.scott.payment.merchant.dto.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionDTOs
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户后台交易查询 DTO 集合，位于 service-merchant 接口传输层，仅承载当前登录商户可见的交易查询、详情、统计和后续动作数据。
 * @status : create
 */
public final class MerchantTransactionDTOs {

    private MerchantTransactionDTOs() {
    }

    /**
     * 商户交易查询分页条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TransactionPageQuery extends PageRequest {

        private static final long serialVersionUID = 1L;

        /**
         * 平台商户号，仅允许服务端根据登录上下文覆盖，前端传入值会被忽略。
         */
        private String merchantId;

        /**
         * 商户订单号，可为空；商户号由服务端登录上下文强制补齐。
         */
        private String merchantOrderNo;

        /**
         * 平台交易 ID，可为空。
         */
        private String transactionId;

        /**
         * 原平台交易 ID，可为空。
         */
        private String sourceTransactionId;

        /**
         * 交易类型，对齐 transaction_type 字典。
         */
        private String transactionType;

        /**
         * 交易状态，对齐 transaction_status 字典。
         */
        private String transactionStatus;

        /**
         * 支付方式，例如 BANK_CARD、PAYPAL。
         */
        private String paymentMethod;

        /**
         * 卡品牌或钱包品牌。
         */
        private String paymentBrand;

        /**
         * 渠道订单号，可用于商户对账排查。
         */
        private String channelOrderNo;

        /**
         * 商户侧可见响应码。
         */
        private String merchantResponseCode;

        /**
         * 对账状态。
         */
        private String reconciliationStatus;

        /**
         * 结算状态。
         */
        private String settlementStatus;

        /**
         * 查询开始交易时间。
         */
        private LocalDateTime beginTime;

        /**
         * 查询结束交易时间。
         */
        private LocalDateTime endTime;

        /**
         * 查询时区，支付核心按该时区解释 beginTime/endTime。
         */
        private String queryTimeZone;
    }

    /**
     * 商户交易动作请求。
     */
    @Data
    public static class TransactionActionRequest implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 本次动作唯一请求号；为空时商户后台生成并作为支付核心幂等键组成部分。
         */
        private String merchantOrderId;

        /**
         * 动作金额，退款必填。
         */
        private BigDecimal amount;

        /**
         * 动作币种，默认取原交易币种。
         */
        private String currency;

        /**
         * 商户操作原因，写入交易描述和操作审计。
         */
        private String reason;

        /**
         * 被操作交易的真实分片时间，必须来自当前商户交易列表结果。
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime transactionDateTime;

        /** 被操作交易所属生命周期根主单的真实分片时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;
    }

    /**
     * 商户交易动作响应。
     */
    @Data
    public static class TransactionActionResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 operationId、merchantOrderNo 共同定位一笔平台交易。
         * </p>
         */
        private String transactionId;

        /**
         * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 transactionId 建立后续请款、退款、撤销和原交易之间的关联。
         * </p>
         */
        private String sourceTransactionId;

        /**
         * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 merchantId、transactionId 共同支持幂等、查询和对账。
         * </p>
         */
        private String merchantOrderNo;

        /**
         * 商户请求订单标识，用于区分同一商户订单下的一次接口提交或后续交易动作。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 merchantId、transactionId 共同支持幂等、查询和对账。
         * </p>
         */
        private String merchantOrderId;

        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String transactionType;

        /**
         * 状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String status;

        /**
         * 商户响应码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String merchantResponseCode;

        /**
         * 响应中的商户响应说明，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String merchantResponseMessage;

        /**
         * 响应中的{@code processStage}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String processStage;

        /**
         * {@code failReasonCode}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String failReasonCode;

        /**
         * 等待原因编码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String pendingReasonCode;

        /**
         * 金额，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private Long amount;

        /**
         * 币种，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String currency;
    }

    /**
     * 商户交易主单列表响应。
     */
    @Data
    public static class TransactionOrderResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 transactionId、transactionType 共同定位一次交易动作。
         * </p>
         */
        private String operationId;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String rootTransactionId;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String latestTransactionId;

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
         * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 merchantId、transactionId 共同支持幂等、查询和对账。
         * </p>
         */
        private String merchantOrderNo;

        /**
         * 商户请求订单标识，用于区分同一商户订单下的一次接口提交或后续交易动作。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 merchantId、transactionId 共同支持幂等、查询和对账。
         * </p>
         */
        private String merchantOrderId;

        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentMethod;

        /**
         * 支付品牌编码，用于区分银行卡、钱包或本地支付品牌。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentBrand;

        /**
         * 卡 BIN，用于识别发卡行、卡组织、国家地区和风控规则。
         * <p>
         * 单位：无；格式：卡 BIN 或尾号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅保存识别片段，不保存完整 PAN；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String cardBin;

        /**
         * 卡编号脱敏，表示银行卡号或脱敏卡号字段。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String cardNumberMasked;

        /**
         * {@code authCode}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String authCode;

        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String transactionType;

        /**
         * 交易状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String transactionStatus;

        /**
         * {@code lifecycleStatus}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String lifecycleStatus;

        /**
         * {@code lifecycleStatusMessage}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String lifecycleStatusMessage;

        /**
         * 响应中的{@code processStage}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String processStage;

        /**
         * 标签币种，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String labelCurrency;

        /**
         * 标签金额，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal labelAmount;

        /**
         * 交易币种，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String transactionCurrency;

        /**
         * 交易金额，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal transactionAmount;

        /**
         * 当前金额，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal currentAmount;

        /**
         * 当前币种，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String currentCurrency;

        /**
         * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
         * <p>
         * 单位：位；格式：非负整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：必须等于 ISO 4217 币种精度，禁止默认按 2 位处理；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private Integer currencyExponent;

        /**
         * 响应中的交易汇率，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private BigDecimal transactionRate;

        /** 是否按 3DS 交易处理：0 否，1 是。 */
        private Integer threeDsEnabled;

        /** 是否实际启用 DCC：0 未启用，1 启用。 */
        private Integer dccEnabled;

        /** 是否实际启用 EDC：0 未启用，1 启用。 */
        private Integer edcEnabled;

        /**
         * 商户响应码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String merchantResponseCode;

        /**
         * 响应中的商户响应说明，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String merchantResponseMessage;

        /**
         * {@code authorizedAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal authorizedAmount;

        /**
         * {@code capturedAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal capturedAmount;

        /**
         * {@code refundedAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal refundedAmount;

        /**
         * {@code availableCaptureAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal availableCaptureAmount;

        /**
         * {@code availableRefundAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal availableRefundAmount;

        /** 最近真实动作的最终结算币种；未结算时为空。 */
        private String settlementCurrency;

        /** 最近真实动作的最终有符号结算金额，单位由 settlementCurrency 决定。 */
        private BigDecimal settlementAmount;

        /** 最近真实动作中 1 单位交易币种兑换的结算币种数量，最多 12 位小数。 */
        private BigDecimal settlementRate;

        /** 最近真实动作的结算业务日期；未结算时为空。 */
        private LocalDate settlementDate;

        /** 最近一次结算或冲正批次号。 */
        private String settlementBatchNo;

        /** 当前主单结算快照对应的真实动作交易号。 */
        private String settlementTransactionId;

        /** 当前主单结算快照对应的真实动作分片时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime settlementTransactionDateTime;

        /**
         * 结算状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String settlementStatus;

        /**
         * {@code reconciliationStatus}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String reconciliationStatus;

        /**
         * {@code accountingStatus}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String accountingStatus;

        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelCode;

        /**
         * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelOrderNo;

        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime transactionDateTime;

        /** 生命周期根主单的真实分片时间，动作详情必须与 transactionDateTime 一并传入。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;

        /**
         * 交易业务时区，使用 IANA 时区标识解释本地交易时间。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String transactionTimeZone;
    }

    /**
     * 商户交易动作单列表响应。
     */
    @Data
    public static class TransactionOperationResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 transactionId、transactionType 共同定位一次交易动作。
         * </p>
         */
        private String operationId;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 operationId、merchantOrderNo 共同定位一笔平台交易。
         * </p>
         */
        private String transactionId;

        /**
         * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 transactionId 建立后续请款、退款、撤销和原交易之间的关联。
         * </p>
         */
        private String sourceTransactionId;

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
         * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 merchantId、transactionId 共同支持幂等、查询和对账。
         * </p>
         */
        private String merchantOrderNo;

        /**
         * 商户请求订单标识，用于区分同一商户订单下的一次接口提交或后续交易动作。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 merchantId、transactionId 共同支持幂等、查询和对账。
         * </p>
         */
        private String merchantOrderId;

        /**
         * 响应中的{@code operationSequence}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private Integer operationSequence;

        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String transactionType;

        /**
         * 交易状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String transactionStatus;

        /**
         * 响应中的{@code processStage}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String processStage;

        /**
         * 标签币种，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String labelCurrency;

        /**
         * 标签金额，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal labelAmount;

        /**
         * 交易币种，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String transactionCurrency;

        /**
         * 交易金额，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal transactionAmount;

        /**
         * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
         * <p>
         * 单位：位；格式：非负整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：必须等于 ISO 4217 币种精度，禁止默认按 2 位处理；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private Integer currencyExponent;

        /**
         * 响应中的交易汇率，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private BigDecimal transactionRate;

        /** 是否按 3DS 交易处理：0 否，1 是。 */
        private Integer threeDsEnabled;

        /** 是否实际启用 DCC：0 未启用，1 启用。 */
        private Integer dccEnabled;

        /** 是否实际启用 EDC：0 未启用，1 启用。 */
        private Integer edcEnabled;

        /**
         * 商户响应码，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String merchantResponseCode;

        /**
         * 响应中的商户响应说明，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String merchantResponseMessage;

        /**
         * 商户通知状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String merchantNotificationStatus;

        /**
         * {@code authorizedAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal authorizedAmount;

        /**
         * {@code capturedAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal capturedAmount;

        /**
         * {@code refundedAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal refundedAmount;

        /**
         * {@code availableCaptureAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal availableCaptureAmount;

        /**
         * {@code availableRefundAmount}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal availableRefundAmount;

        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentMethod;

        /**
         * 支付品牌编码，用于区分银行卡、钱包或本地支付品牌。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentBrand;

        /**
         * 卡 BIN，用于识别发卡行、卡组织、国家地区和风控规则。
         * <p>
         * 单位：无；格式：卡 BIN 或尾号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：仅保存识别片段，不保存完整 PAN；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String cardBin;

        /**
         * 卡编号脱敏，表示银行卡号或脱敏卡号字段。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String cardNumberMasked;

        /**
         * 访问类型，用于区分登录、查询、导出或配置变更等审计场景。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String accessType;

        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelCode;

        /**
         * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String channelOrderNo;

        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
         * </p>
         */
        private String channelTransactionId;

        /**
         * {@code authCode}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String authCode;

        /**
         * 响应中的{@code acquirerReferenceNo}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String acquirerReferenceNo;

        /** 当前动作最终结算币种；未结算时为空。 */
        private String settlementCurrency;

        /** 当前动作最终有符号结算金额，单位由 settlementCurrency 决定。 */
        private BigDecimal settlementAmount;

        /** 当前动作中 1 单位交易币种兑换的结算币种数量，最多 12 位小数。 */
        private BigDecimal settlementRate;

        /** 当前动作最终结算业务日期；未结算时为空。 */
        private LocalDate settlementDate;

        /** 当前动作最近一次结算或冲正批次号。 */
        private String settlementBatchNo;

        /**
         * 结算状态，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String settlementStatus;

        /**
         * {@code reconciliationStatus}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String reconciliationStatus;

        /**
         * {@code accountingStatus}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private String accountingStatus;

        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime transactionDateTime;

        /** 生命周期根主单的真实分片时间。 */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
        private LocalDateTime rootTransactionDateTime;

        /**
         * 响应中的动作时间，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：具体时刻使用系统约定业务时区，业务日期不附加时区；格式：ISO 日期或日期时间；持久化时刻保留毫秒精度；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：时间范围由业务流程或查询条件限定；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private LocalDateTime operationTime;
    }

    /**
     * 商户交易动作分页和统计响应。
     */
    @Data
    public static class TransactionOperationSearchResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 页，用于控制分页查询、批量扫描或任务单次处理规模。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
         * </p>
         */
        private PageResult<TransactionOperationResponse> page;

        /**
         * 响应中的汇总数据，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private TransactionOperationSummaryResponse summary;
    }

    /**
     * 商户交易动作统计响应。
     */
    @Data
    public static class TransactionOperationSummaryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 合计计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private long totalCount;

        /**
         * 成功计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private long successCount;

        /**
         * 失败计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private long failedCount;

        /**
         * 按币种拆分的金额汇总集合，禁止直接跨币种相加。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<TransactionAmountSummaryResponse> amountSummaries = Collections.emptyList();

        /**
         * 按币种拆分的成功金额汇总集合，禁止直接跨币种相加。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<TransactionAmountSummaryResponse> successAmountSummaries = Collections.emptyList();

        /**
         * 按币种拆分的失败金额汇总集合，禁止直接跨币种相加。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<TransactionAmountSummaryResponse> failedAmountSummaries = Collections.emptyList();

        /**
         * {@code paymentMethodSummaries}，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<TransactionPaymentMethodSummaryResponse> paymentMethodSummaries = Collections.emptyList();
    }

    /**
     * 商户交易按币种聚合金额。
     */
    @Data
    public static class TransactionAmountSummaryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 币种，表示金额字段使用的币种。
         * <p>
         * 单位：无；格式：ISO 4217 三位大写币种代码；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自平台支持币种；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private String currency;

        /**
         * 金额，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private BigDecimal amount;

        /**
         * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
         * <p>
         * 单位：位；格式：非负整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：必须等于 ISO 4217 币种精度，禁止默认按 2 位处理；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
         * </p>
         */
        private Integer currencyExponent;
    }

    /**
     * 商户交易按支付方式聚合统计。
     */
    @Data
    public static class TransactionPaymentMethodSummaryResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 支付方式，表示支付方式、通知方式或调用方式。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentMethod;

        /**
         * 支付品牌编码，用于区分银行卡、钱包或本地支付品牌。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private String paymentBrand;

        /**
         * 计数，表示当前统计、分页、扫描或重试场景中的数量。
         * <p>
         * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private long count;

        /**
         * 按币种拆分的金额汇总集合，禁止直接跨币种相加。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<TransactionAmountSummaryResponse> amountSummaries = Collections.emptyList();
    }

    /**
     * 商户交易聚合详情响应。
     */
    @Data
    public static class TransactionDetailResponse implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 响应中的订单，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private TransactionOrderResponse order;

        /**
         * 交易动作集合，承载 {@code TransactionDetailResponse} 当前请求或响应中的多值数据。
         * <p>
         * 单位：无；格式：集合或键值映射；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：元素类型和数量由所属请求、响应或聚合模型约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
         * </p>
         */
        private List<TransactionOperationResponse> operations = Collections.emptyList();

        /**
         * {@code statusHistory}，表示当前记录在业务流程中的处理状态。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
         * </p>
         */
        private List<Map<String, Object>> statusHistory = Collections.emptyList();

        /**
         * 响应中的{@code flowEvents}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private List<Map<String, Object>> flowEvents = Collections.emptyList();

        /**
         * {@code amountChanges}，表示当前交易、费用、限额或统计口径下的金额值。
         * <p>
         * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * 字段关系：必须与 currency 或同名币种字段一起解释。
         * </p>
         */
        private List<Map<String, Object>> amountChanges = Collections.emptyList();

        /**
         * 响应中的{@code channelRequests}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private List<Map<String, Object>> channelRequests = Collections.emptyList();

        /**
         * 响应中的{@code channelInteractionLogs}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private List<Map<String, Object>> channelInteractionLogs = Collections.emptyList();

        /**
         * 响应中的商户通知任务，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private List<Map<String, Object>> merchantNotifications = Collections.emptyList();

        /**
         * 响应中的商户通知日志，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private List<Map<String, Object>> merchantNotificationLogs = Collections.emptyList();

        /**
         * 响应中的{@code merchantApiInteractionLogs}，用于管理端或商户端展示当前处理结果。
         * <p>
         * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
         * </p>
         */
        private List<Map<String, Object>> merchantApiInteractionLogs = Collections.emptyList();
    }
}
