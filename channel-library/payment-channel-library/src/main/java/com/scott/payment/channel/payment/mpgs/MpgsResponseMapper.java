package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse;
import com.scott.payment.channel.payment.dto.response.ChannelPaymentResponse.PaymentMethodSummary;
import com.scott.payment.channel.payment.enums.PaymentChannelCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsResponseMapper
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 响应映射器，位于 payment-channel-library 渠道实现层，负责保留渠道真实失败原因并映射统一渠道状态；不决定商户或付款人展示文案。
 * @status : create
 */
@Component
public class MpgsResponseMapper {

    /**
     * trade Status Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final MpgsTradeStatusMapper tradeStatusMapper;

    /**
     * error Code Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final MpgsErrorCodeMapper errorCodeMapper;

    /**
     * 创建 MPGS 响应映射器。
     */
    public MpgsResponseMapper() {
        this(new MpgsTradeStatusMapper(), new MpgsErrorCodeMapper());
    }

    /**
     * 创建 MPGS 响应映射器。
     *
     * @param tradeStatusMapper 交易状态映射器
     * @param errorCodeMapper   错误码映射器
     */
    public MpgsResponseMapper(MpgsTradeStatusMapper tradeStatusMapper, MpgsErrorCodeMapper errorCodeMapper) {
        this.tradeStatusMapper = tradeStatusMapper;
        this.errorCodeMapper = errorCodeMapper;
    }

    /**
     * 映射 MPGS 响应。
     *
     * @param request  渠道统一请求
     * @param response MPGS 原始响应字段
     * @return 渠道统一响应
     */
    public ChannelPaymentResponse toChannelResponse(com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest request,
                                                     MpgsResponsePayload response) {
        ChannelPaymentResponse target = new ChannelPaymentResponse();
        target.setChannelCode(PaymentChannelCode.MPGS.getCode());
        target.setOperationId(request.getOperationId());
        target.setTransactionId(request.getTransactionId());
        target.setChannelOrderNo(request.getChannelOrderNo());
        target.setChannelTransactionId(request.getChannelTransactionId());
        if (response == null) {
            target.setChannelTradeStatus(tradeStatusMapper.map(null));
            target.setRawChannelStatus(null);
            target.setChannelResponseCode(errorCodeMapper.responseCode(null));
            target.setChannelResponseMessage(errorCodeMapper.responseMessage(null));
            return target;
        }
        target.setChannelTradeStatus(tradeStatusMapper.map(response));
        target.setRawChannelStatus(response.getResult());
        target.setChannelResponseCode(errorCodeMapper.responseCode(response));
        target.setChannelResponseMessage(errorCodeMapper.responseMessage(response));
        MpgsResponseSummary summary = responseSummary(response);
        if (response.getOrder() != null && response.getOrder().getId() != null) {
            target.setChannelOrderNo(response.getOrder().getId());
        }
        if (response.getTransaction() != null && response.getTransaction().getId() != null) {
            target.setChannelTransactionId(response.getTransaction().getId());
        }
        target.setAuthCode(summary.getAuthorizationCode());
        target.setPaymentMethodSummary(paymentMethodSummary(summary));
        target.setRawResponse(summary.toRawResponseMap());
        return target;
    }

    /**
     * 提取 MPGS 类型化响应摘要。
     * <p>
     * Map 只作为渠道公共扩展字段向后兼容；MPGS 自身字段在这里先落入类型化对象，避免业务代码散落字符串 key。
     *
     * @param response MPGS 响应载荷
     * @return MPGS 响应摘要
     */
    MpgsResponseSummary responseSummary(MpgsResponsePayload response) {
        MpgsResponsePayload.Response gatewayResponse = response.getResponse();
        MpgsResponsePayload.CardSecurityCode cardSecurityCode = gatewayResponse == null ? null : gatewayResponse.getCardSecurityCode();
        MpgsResponsePayload.AuthorizationResponse authorizationResponse = response.getAuthorizationResponse();
        MpgsResponsePayload.ErrorPayload error = response.getError();
        MpgsResponsePayload.Order order = response.getOrder();
        MpgsResponsePayload.Chargeback chargeback = order == null ? null : order.getChargeback();
        MpgsResponsePayload.Transaction transaction = response.getTransaction();
        MpgsResponsePayload.Acquirer acquirer = transaction == null ? null : transaction.getAcquirer();
        MpgsResponsePayload.SourceOfFunds sourceOfFunds = response.getSourceOfFunds();
        MpgsResponsePayload.Provided provided = sourceOfFunds == null ? null : sourceOfFunds.getProvided();
        MpgsResponsePayload.Card card = provided == null ? null : provided.getCard();
        MpgsResponsePayload.Expiry expiry = card == null ? null : card.getExpiry();
        MpgsResponsePayload.Risk risk = response.getRisk();
        MpgsResponsePayload.RiskResponse riskResponse = risk == null ? null : risk.getResponse();
        MpgsResponsePayload.Review review = riskResponse == null ? null : riskResponse.getReview();
        return MpgsResponseSummary.builder()
                .result(response.getResult())
                .gatewayEntryPoint(response.getGatewayEntryPoint())
                .merchant(response.getMerchant())
                .version(response.getVersion())
                .gatewayCode(gatewayResponse == null ? null : gatewayResponse.getGatewayCode())
                .gatewayRecommendation(gatewayResponse == null ? null : gatewayResponse.getGatewayRecommendation())
                .acquirerCode(gatewayResponse == null ? null : gatewayResponse.getAcquirerCode())
                .acquirerMessage(gatewayResponse == null ? null : gatewayResponse.getAcquirerMessage())
                .cardSecurityGatewayCode(cardSecurityCode == null ? null : cardSecurityCode.getGatewayCode())
                .cardSecurityAcquirerCode(cardSecurityCode == null ? null : cardSecurityCode.getAcquirerCode())
                .authorizationResponseCode(authorizationResponse == null ? null : authorizationResponse.getResponseCode())
                .authorizationStan(authorizationResponse == null ? null : authorizationResponse.getStan())
                .authorizationTransactionIdentifier(authorizationResponse == null ? null : authorizationResponse.getTransactionIdentifier())
                .financialNetworkCode(authorizationResponse == null ? null : authorizationResponse.getFinancialNetworkCode())
                .posEntryMode(authorizationResponse == null ? null : authorizationResponse.getPosEntryMode())
                .posData(authorizationResponse == null ? null : authorizationResponse.getPosData())
                .processingCode(authorizationResponse == null ? null : authorizationResponse.getProcessingCode())
                .commercialCard(authorizationResponse == null ? null : authorizationResponse.getCommercialCard())
                .commercialCardIndicator(authorizationResponse == null ? null : authorizationResponse.getCommercialCardIndicator())
                .errorCause(error == null ? null : error.getCause())
                .errorExplanation(error == null ? null : error.getExplanation())
                .errorField(error == null ? null : error.getField())
                .errorValidationType(error == null ? null : error.getValidationType())
                .orderId(order == null ? null : order.getId())
                .orderStatus(order == null ? null : order.getStatus())
                .orderReference(order == null ? null : order.getReference())
                .orderAmount(order == null ? null : order.getAmount())
                .orderCurrency(order == null ? null : order.getCurrency())
                .orderAuthenticationStatus(order == null ? null : order.getAuthenticationStatus())
                .orderCreationTime(order == null ? null : order.getCreationTime())
                .orderLastUpdatedTime(order == null ? null : order.getLastUpdatedTime())
                .orderMerchantAmount(order == null ? null : order.getMerchantAmount())
                .orderMerchantCurrency(order == null ? null : order.getMerchantCurrency())
                .merchantCategoryCode(order == null ? null : order.getMerchantCategoryCode())
                .totalAuthorizedAmount(order == null ? null : order.getTotalAuthorizedAmount())
                .totalCapturedAmount(order == null ? null : order.getTotalCapturedAmount())
                .totalRefundedAmount(order == null ? null : order.getTotalRefundedAmount())
                .chargebackAmount(chargeback == null ? null : chargeback.getAmount())
                .chargebackCurrency(chargeback == null ? null : chargeback.getCurrency())
                .transactionId(transaction == null ? null : transaction.getId())
                .transactionType(transaction == null ? null : transaction.getType())
                .transactionAmount(transaction == null ? null : transaction.getAmount())
                .transactionCurrency(transaction == null ? null : transaction.getCurrency())
                .transactionAuthenticationStatus(transaction == null ? null : transaction.getAuthenticationStatus())
                .authorizationCode(transaction == null ? null : transaction.getAuthorizationCode())
                .transactionReference(transaction == null ? null : transaction.getReference())
                .acquirerReference(acquirer == null ? null : acquirer.getTransactionId())
                .receipt(transaction == null ? null : transaction.getReceipt())
                .transactionStan(transaction == null ? null : transaction.getStan())
                .terminal(transaction == null ? null : transaction.getTerminal())
                .source(transaction == null ? null : transaction.getSource())
                .acquirerBatch(acquirer == null ? null : acquirer.getBatch())
                .acquirerDate(acquirer == null ? null : acquirer.getDate())
                .acquirerId(acquirer == null ? null : acquirer.getId())
                .acquirerMerchantId(acquirer == null ? null : acquirer.getMerchantId())
                .acquirerSettlementDate(acquirer == null ? null : acquirer.getSettlementDate())
                .acquirerTimeZone(acquirer == null ? null : acquirer.getTimeZone())
                .sourceOfFundsType(sourceOfFunds == null ? null : sourceOfFunds.getType())
                .cardBrand(card == null ? null : card.getBrand())
                .cardScheme(card == null ? null : card.getScheme())
                .cardNumberMasked(card == null ? null : card.getNumber())
                .cardExpiryMonth(expiry == null ? null : expiry.getMonth())
                .cardExpiryYear(expiry == null ? null : expiry.getYear())
                .issuerCountryCode(card == null ? null : card.getIssuerCountryCode())
                .fundingMethod(card == null ? null : card.getFundingMethod())
                .storedOnFile(card == null ? null : card.getStoredOnFile())
                .riskGatewayCode(riskResponse == null ? null : riskResponse.getGatewayCode())
                .riskProvider(riskResponse == null ? null : riskResponse.getProvider())
                .riskReviewDecision(review == null ? null : review.getDecision())
                .riskTotalScore(riskResponse == null ? null : riskResponse.getTotalScore())
                .timeOfRecord(response.getTimeOfRecord())
                .timeOfLastUpdate(response.getTimeOfLastUpdate())
                .build();
    }

    /**
     * 将 MPGS 返回的卡摘要映射为渠道统一支付工具摘要。
     *
     * @param summary MPGS 响应摘要
     * @return 渠道统一支付工具摘要；无有效字段时返回 null
     */
    private PaymentMethodSummary paymentMethodSummary(MpgsResponseSummary summary) {
        if (summary == null || !hasPaymentMethodSummary(summary)) {
            return null;
        }
        PaymentMethodSummary paymentMethodSummary = new PaymentMethodSummary();
        paymentMethodSummary.setPaymentMethod(summary.getSourceOfFundsType());
        paymentMethodSummary.setPaymentBrand(summary.getCardBrand());
        paymentMethodSummary.setScheme(summary.getCardScheme());
        paymentMethodSummary.setCardNumberMasked(summary.getCardNumberMasked());
        paymentMethodSummary.setExpiryMonth(summary.getCardExpiryMonth());
        paymentMethodSummary.setExpiryYear(summary.getCardExpiryYear());
        paymentMethodSummary.setIssuerCountry(summary.getIssuerCountryCode());
        paymentMethodSummary.setFundingMethod(summary.getFundingMethod());
        paymentMethodSummary.setStoredOnFile(summary.getStoredOnFile());
        paymentMethodSummary.setCscResult(firstText(summary.getCardSecurityGatewayCode(), summary.getCardSecurityAcquirerCode()));
        return paymentMethodSummary;
    }

    /**
     * 判断 has Payment Method Summary 条件是否成立，用于控制后续业务分支。
     * <p>
     * 层级边界：渠道适配层；输入来源、输出结构和异常语义由 MpgsResponseMapper 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param summary summary 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean hasPaymentMethodSummary(MpgsResponseSummary summary) {
        return StringUtils.hasText(summary.getSourceOfFundsType())
                || StringUtils.hasText(summary.getCardBrand())
                || StringUtils.hasText(summary.getCardScheme())
                || StringUtils.hasText(summary.getCardNumberMasked())
                || StringUtils.hasText(summary.getIssuerCountryCode())
                || StringUtils.hasText(summary.getFundingMethod())
                || StringUtils.hasText(summary.getStoredOnFile());
    }

    /**
     * 完成 first Text 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：渠道适配层；输入来源、输出结构和异常语义由 MpgsResponseMapper 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param values values 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
