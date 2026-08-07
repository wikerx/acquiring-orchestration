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
     * trade Status Mapper，表示当前记录在业务流程中的处理状态。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与时间字段、操作记录和状态历史共同描述当前处理阶段。
     * </p>
     */
    private final MpgsTradeStatusMapper tradeStatusMapper;

    /**
     * error Code Mapper，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
        if (response.getTransaction() != null) {
            target.setChannelCurrency(response.getTransaction().getCurrency());
            target.setChannelAmount(response.getTransaction().getAmount());
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
     * 判断 has payment method summary 条件是否成立，用于控制 Mpgs Response Mapper 的后续分支。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param summary summary 输入值，参与 汇总数据 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
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
     * 整理首个非空文本，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param values values 输入值，参与 values 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
