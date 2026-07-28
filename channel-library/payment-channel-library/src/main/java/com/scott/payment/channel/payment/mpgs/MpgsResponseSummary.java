package com.scott.payment.channel.payment.mpgs;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsResponseSummary
 * @date : 2026-07-16 00:00
 * @email : scott_x@163.com
 * @description : MPGS 响应摘要值对象，位于 payment-channel-library 渠道实现层，用类型化字段承载后台排查和平台落库需要的核心响应信息。
 * @status : create
 */
@Value
@Builder
public class MpgsResponseSummary {

    /**
     * MPGS 顶层 result，例如 SUCCESS、ERROR、UNKNOWN。
     */
    String result;

    /**
     * MPGS 网关入口。
     */
    String gatewayEntryPoint;

    /**
     * MPGS 商户号。
     */
    String merchant;

    /**
     * MPGS API 版本。
     */
    String version;

    /**
     * MPGS 网关响应码。
     */
    String gatewayCode;

    /**
     * MPGS 网关建议。
     */
    String gatewayRecommendation;

    /**
     * 收单机构响应码。
     */
    String acquirerCode;

    /**
     * 收单机构响应描述。
     */
    String acquirerMessage;

    /**
     * MPGS CSC 网关校验结果。
     */
    String cardSecurityGatewayCode;

    /**
     * MPGS CSC 收单校验结果。
     */
    String cardSecurityAcquirerCode;

    /**
     * 授权响应码。
     */
    String authorizationResponseCode;

    /**
     * 授权响应 STAN。
     */
    String authorizationStan;

    /**
     * 授权响应交易识别号。
     */
    String authorizationTransactionIdentifier;

    /**
     * 金融网络编码。
     */
    String financialNetworkCode;

    /**
     * POS 录入模式。
     */
    String posEntryMode;

    /**
     * POS 数据。
     */
    String posData;

    /**
     * 处理码。
     */
    String processingCode;

    /**
     * 商务卡信息。
     */
    String commercialCard;

    /**
     * 商务卡标识。
     */
    String commercialCardIndicator;

    /**
     * MPGS 错误原因。
     */
    String errorCause;

    /**
     * MPGS 错误说明。
     */
    String errorExplanation;

    /**
     * MPGS 校验错误字段。
     */
    String errorField;

    /**
     * MPGS 校验错误类型。
     */
    String errorValidationType;

    /**
     * MPGS orderId。
     */
    String orderId;

    /**
     * MPGS 订单状态。
     */
    String orderStatus;

    /**
     * MPGS 订单参考号。
     */
    String orderReference;

    /**
     * MPGS 订单金额。
     */
    BigDecimal orderAmount;

    /**
     * MPGS 订单币种。
     */
    String orderCurrency;

    /**
     * MPGS 订单认证状态。
     */
    String orderAuthenticationStatus;

    /**
     * MPGS 订单创建时间。
     */
    String orderCreationTime;

    /**
     * MPGS 订单最近更新时间。
     */
    String orderLastUpdatedTime;

    /**
     * MPGS 商户订单金额。
     */
    BigDecimal orderMerchantAmount;

    /**
     * MPGS 商户订单币种。
     */
    String orderMerchantCurrency;

    /**
     * MPGS 商户 MCC。
     */
    String merchantCategoryCode;

    /**
     * MPGS 生命周期累计授权金额。
     */
    BigDecimal totalAuthorizedAmount;

    /**
     * MPGS 生命周期累计请款金额。
     */
    BigDecimal totalCapturedAmount;

    /**
     * MPGS 生命周期累计退款金额。
     */
    BigDecimal totalRefundedAmount;

    /**
     * MPGS 生命周期累计拒付金额。
     */
    BigDecimal chargebackAmount;

    /**
     * MPGS 拒付币种。
     */
    String chargebackCurrency;

    /**
     * MPGS transactionId。
     */
    String transactionId;

    /**
     * MPGS 交易类型。
     */
    String transactionType;

    /**
     * MPGS 当前交易金额。
     */
    BigDecimal transactionAmount;

    /**
     * MPGS 当前交易币种。
     */
    String transactionCurrency;

    /**
     * MPGS 当前交易认证状态。
     */
    String transactionAuthenticationStatus;

    /**
     * 授权码。
     */
    String authorizationCode;

    /**
     * MPGS transaction.reference，通常来自平台请求 reference，仅用于后台排查，不能作为商户 ARN 返回。
     */
    String transactionReference;

    /**
     * MPGS transaction.acquirer.transactionId，仅用于后台排查；MPGS 未明确返回 ARN 时不能映射为平台 ARN/RRN。
     */
    String acquirerReference;

    /**
     * MPGS transaction.receipt，仅用于后台排查；不能当作 RRN 返回给商户或管理端 ARN 字段。
     */
    String receipt;

    /**
     * 当前交易 STAN。
     */
    String transactionStan;

    /**
     * 当前交易终端号。
     */
    String terminal;

    /**
     * 当前交易来源。
     */
    String source;

    /**
     * 收单批次号。
     */
    String acquirerBatch;

    /**
     * 收单交易日期。
     */
    String acquirerDate;

    /**
     * 收单机构 ID。
     */
    String acquirerId;

    /**
     * 收单机构商户号。
     */
    String acquirerMerchantId;

    /**
     * 收单结算日期。
     */
    String acquirerSettlementDate;

    /**
     * 收单时区。
     */
    String acquirerTimeZone;

    /**
     * 资金来源类型。
     */
    String sourceOfFundsType;

    /**
     * 卡品牌。
     */
    String cardBrand;

    /**
     * 卡组织。
     */
    String cardScheme;

    /**
     * 渠道脱敏卡号。
     */
    String cardNumberMasked;

    /**
     * 卡有效期月份。
     */
    String cardExpiryMonth;

    /**
     * 卡有效期年份。
     */
    String cardExpiryYear;

    /**
     * 发卡国家或地区代码。
     */
    String issuerCountryCode;

    /**
     * 资金类型。
     */
    String fundingMethod;

    /**
     * 是否存储凭证。
     */
    String storedOnFile;

    /**
     * 风险响应码。
     */
    String riskGatewayCode;

    /**
     * 风险提供方。
     */
    String riskProvider;

    /**
     * 风险复核决策。
     */
    String riskReviewDecision;

    /**
     * 风险总分。
     */
    Integer riskTotalScore;

    /**
     * MPGS 记录时间。
     */
    String timeOfRecord;

    /**
     * MPGS 最近更新时间。
     */
    String timeOfLastUpdate;

    /**
     * 转为渠道公共响应的扩展 Map，保持既有 service-payment 落库和查询兼容。
     *
     * @return 非空字段组成的有序 Map
     */
    public Map<String, String> toRawResponseMap() {
        Map<String, String> raw = new LinkedHashMap<>();
        put(raw, "result", result);
        put(raw, "gatewayEntryPoint", gatewayEntryPoint);
        put(raw, "merchant", merchant);
        put(raw, "version", version);
        put(raw, "gatewayCode", gatewayCode);
        put(raw, "gatewayRecommendation", gatewayRecommendation);
        put(raw, "acquirerCode", acquirerCode);
        put(raw, "acquirerMessage", acquirerMessage);
        put(raw, "cardSecurityGatewayCode", cardSecurityGatewayCode);
        put(raw, "cardSecurityAcquirerCode", cardSecurityAcquirerCode);
        put(raw, "authorizationResponseCode", authorizationResponseCode);
        put(raw, "authorizationStan", authorizationStan);
        put(raw, "authorizationTransactionIdentifier", authorizationTransactionIdentifier);
        put(raw, "financialNetworkCode", financialNetworkCode);
        put(raw, "posEntryMode", posEntryMode);
        put(raw, "posData", posData);
        put(raw, "processingCode", processingCode);
        put(raw, "commercialCard", commercialCard);
        put(raw, "commercialCardIndicator", commercialCardIndicator);
        put(raw, "errorCause", errorCause);
        put(raw, "errorExplanation", errorExplanation);
        put(raw, "errorField", errorField);
        put(raw, "errorValidationType", errorValidationType);
        put(raw, "orderId", orderId);
        put(raw, "orderStatus", orderStatus);
        put(raw, "orderReference", orderReference);
        put(raw, "orderAmount", orderAmount);
        put(raw, "orderCurrency", orderCurrency);
        put(raw, "orderAuthenticationStatus", orderAuthenticationStatus);
        put(raw, "orderCreationTime", orderCreationTime);
        put(raw, "orderLastUpdatedTime", orderLastUpdatedTime);
        put(raw, "orderMerchantAmount", orderMerchantAmount);
        put(raw, "orderMerchantCurrency", orderMerchantCurrency);
        put(raw, "merchantCategoryCode", merchantCategoryCode);
        put(raw, "totalAuthorizedAmount", totalAuthorizedAmount);
        put(raw, "totalCapturedAmount", totalCapturedAmount);
        put(raw, "totalRefundedAmount", totalRefundedAmount);
        put(raw, "chargebackAmount", chargebackAmount);
        put(raw, "chargebackCurrency", chargebackCurrency);
        put(raw, "transactionId", transactionId);
        put(raw, "transactionType", transactionType);
        put(raw, "transactionAmount", transactionAmount);
        put(raw, "transactionCurrency", transactionCurrency);
        put(raw, "transactionAuthenticationStatus", transactionAuthenticationStatus);
        put(raw, "authorizationCode", authorizationCode);
        put(raw, "transactionReference", transactionReference);
        put(raw, "acquirerReference", acquirerReference);
        put(raw, "receipt", receipt);
        put(raw, "transactionStan", transactionStan);
        put(raw, "terminal", terminal);
        put(raw, "source", source);
        put(raw, "acquirerBatch", acquirerBatch);
        put(raw, "acquirerDate", acquirerDate);
        put(raw, "acquirerId", acquirerId);
        put(raw, "acquirerMerchantId", acquirerMerchantId);
        put(raw, "acquirerSettlementDate", acquirerSettlementDate);
        put(raw, "acquirerTimeZone", acquirerTimeZone);
        put(raw, "sourceOfFundsType", sourceOfFundsType);
        put(raw, "cardBrand", cardBrand);
        put(raw, "cardScheme", cardScheme);
        put(raw, "cardNumberMasked", cardNumberMasked);
        put(raw, "cardExpiryMonth", cardExpiryMonth);
        put(raw, "cardExpiryYear", cardExpiryYear);
        put(raw, "issuerCountryCode", issuerCountryCode);
        put(raw, "fundingMethod", fundingMethod);
        put(raw, "storedOnFile", storedOnFile);
        put(raw, "riskGatewayCode", riskGatewayCode);
        put(raw, "riskProvider", riskProvider);
        put(raw, "riskReviewDecision", riskReviewDecision);
        put(raw, "riskTotalScore", riskTotalScore);
        put(raw, "timeOfRecord", timeOfRecord);
        put(raw, "timeOfLastUpdate", timeOfLastUpdate);
        return raw;
    }

    /**
     * 整理写入，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void put(Map<String, String> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /**
     * 整理写入，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void put(Map<String, String> target, String key, BigDecimal value) {
        if (value != null) {
            target.put(key, value.toPlainString());
        }
    }

    /**
     * 整理写入，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param target 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void put(Map<String, String> target, String key, Integer value) {
        if (value != null) {
            target.put(key, String.valueOf(value));
        }
    }
}
