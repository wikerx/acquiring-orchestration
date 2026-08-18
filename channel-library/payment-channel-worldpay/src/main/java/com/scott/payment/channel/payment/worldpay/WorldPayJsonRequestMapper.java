package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayJsonRequestMapper
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : WorldPay JSON 请求映射器，位于 payment-channel-worldpay 渠道协议层，负责把平台统一渠道请求转换为 Access Worldpay JSON 请求载荷；输入为 ChannelPaymentRequest，输出为包含 merchant.entity、transactionReference、instruction.value 和 paymentInstrument 的渠道报文，边界限定在本地字段校验、金额最小单位换算和敏感卡字段组装，不发起外部 HTTP 调用。
 * @status : create
 */
public class WorldPayJsonRequestMapper {

    /**
     * Access Worldpay Payments API 默认明文卡支付工具类型，用于 /api/payments。
     */
    private static final String DEFAULT_PAYMENTS_PAYMENT_INSTRUMENT_TYPE = "plain";

    /**
     * Access Worldpay Card Payments v7 明文卡支付工具类型，用于 /cardPayments/customerInitiatedTransactions。
     */
    private static final String CARD_PAYMENTS_PAYMENT_INSTRUMENT_TYPE = "card/plain";

    /**
     * 默认交易通道，表示电商持卡人发起交易。
     */
    private static final String DEFAULT_CHANNEL = "ecom";

    /**
     * 将平台统一交易请求转换为 WorldPay JSON 请求体。
     * <p>
     * 前置条件：request、transactionType、channelOrderNo 必须存在；支付、授权和预授权必须携带卡号、有效期和安全码；金额类交易必须携带币种和大于 0 的主币种金额。
     * 本方法只做本地映射和校验，不写数据库、不发起外部系统调用、不改变平台交易状态；PAN、CVV 和 CAVV 只进入渠道请求对象，禁止明文日志和异常消息输出。
     * </p>
     *
     * @param request 平台统一渠道请求，来源于 service-payment 渠道调用链，包含交易类型、金额币种、平台交易号、渠道订单号、卡数据和扩展 MID 配置
     * @param merchantCode WorldPay merchant entity，由后台 MID 配置提供
     * @return WPGJSON 请求体，包含 transactionReference、merchant.entity、instruction.value 和可选 paymentInstrument
     */
    public WorldPayJsonRequestPayload toWorldPayRequest(ChannelPaymentRequest request, String merchantCode) {
        validateCommonRequest(request);
        String transactionType = normalizeType(request.getTransactionType());
        String operation = toApiOperation(transactionType);
        WorldPayJsonRequestPayload payload = new WorldPayJsonRequestPayload();
        payload.setOperation(operation);
        payload.setTransactionReference(transactionReference(request));
        payload.setOrderReference(firstText(request.getChannelOrderNo(), request.getMerchantOrderNo()));
        payload.setChannel(firstText(extensionValue(request, "mid.channel"), extensionValue(request, "channel"), DEFAULT_CHANNEL));
        payload.setMerchant(merchant(requiredText(merchantCode, "WorldPay JSON merchant entity is required")));
        payload.setActionLink(sourceTransactionId(request, transactionType));
        if (requiresInstruction(transactionType)) {
            payload.setInstruction(instruction(request, transactionType));
        }
        if (requiresActionValue(transactionType)) {
            payload.setValue(value(request));
        }
        putMetadata(payload, request, "paymentFacilitator", extensionValue(request, "mid.paymentFacilitator"));
        putMetadata(payload, request, "subMerchantId", extensionValue(request, "mid.subMerchantId"));
        putMetadata(payload, request, "requestId", extensionValue(request, "requestId"));
        return payload;
    }

    /**
     * 校验 WPGJSON 公共请求标识。
     *
     * @param request 平台统一渠道请求
     */
    private void validateCommonRequest(ChannelPaymentRequest request) {
        if (request == null) {
            throw new ChannelRequestException("WorldPay JSON request is required");
        }
        requiredText(request.getTransactionType(), "WorldPay JSON transactionType is required");
        requiredText(request.getChannelOrderNo(), "WorldPay JSON channelOrderNo is required");
    }

    /**
     * 将平台交易类型映射为 WPGJSON 操作类型。
     *
     * @param transactionType 平台交易能力编码
     * @return WPGJSON 操作类型
     */
    String toApiOperation(String transactionType) {
        if (ChannelCapability.PAYMENT.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.PAYMENT;
        }
        if (ChannelCapability.AUTHORIZATION.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.AUTHORIZE;
        }
        if (ChannelCapability.PRE_AUTHORIZATION.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.PRE_AUTHORIZE;
        }
        if (ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.CAPTURE;
        }
        if (ChannelCapability.REFUND.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.REFUND;
        }
        if (ChannelCapability.VOID.getCode().equals(transactionType)
                || ChannelCapability.REVERSAL.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.VOID;
        }
        if (ChannelCapability.QUERY.getCode().equals(transactionType)) {
            return WorldPayJsonApiOperation.QUERY;
        }
        throw new ChannelRequestException("WorldPay JSON unsupported transaction type: " + transactionType);
    }

    /**
     * 判断当前交易是否需要 instruction 节点。
     *
     * @param transactionType 平台交易类型
     * @return true 表示需要 instruction
     */
    private boolean requiresInstruction(String transactionType) {
        return ChannelCapability.PAYMENT.getCode().equals(transactionType)
                || ChannelCapability.AUTHORIZATION.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTHORIZATION.getCode().equals(transactionType);
    }

    /**
     * 判断当前交易是否需要顶层 value 节点。
     *
     * @param transactionType 平台交易类型
     * @return true 表示请款、预授权完成或退款需要顶层金额
     */
    private boolean requiresActionValue(String transactionType) {
        return ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)
                || ChannelCapability.REFUND.getCode().equals(transactionType);
    }

    /**
     * 构造 Worldpay merchant 节点。
     *
     * @param merchantCode Worldpay merchant entity
     * @return merchant 节点
     */
    private WorldPayJsonRequestPayload.Merchant merchant(String merchantCode) {
        WorldPayJsonRequestPayload.Merchant merchant = new WorldPayJsonRequestPayload.Merchant();
        merchant.setEntity(merchantCode);
        return merchant;
    }

    /**
     * 构造 Worldpay instruction 节点。
     *
     * @param request 平台统一渠道请求
     * @param transactionType 平台交易类型
     * @return instruction 节点
     */
    private WorldPayJsonRequestPayload.Instruction instruction(ChannelPaymentRequest request, String transactionType) {
        WorldPayJsonRequestPayload.Instruction instruction = new WorldPayJsonRequestPayload.Instruction();
        instruction.setMethod(firstText(extensionValue(request, "mid.method"), extensionValue(request, "paymentMethod"), "card"));
        instruction.setRequestAutoSettlement(requestAutoSettlement(request, transactionType));
        instruction.setNarrative(narrative(request));
        instruction.setAuthentication(authentication(request));
        if (requiresAmount(transactionType)) {
            instruction.setValue(value(request));
        }
        if (requiresCard(transactionType)) {
            instruction.setPaymentInstrument(paymentInstrument(request));
        }
        return instruction;
    }

    /**
     * 构造自动请款节点。
     *
     * @param request 平台统一渠道请求
     * @param transactionType 平台交易类型
     * @return 自动请款节点
     */
    private WorldPayJsonRequestPayload.RequestAutoSettlement requestAutoSettlement(ChannelPaymentRequest request, String transactionType) {
        WorldPayJsonRequestPayload.RequestAutoSettlement autoSettlement = new WorldPayJsonRequestPayload.RequestAutoSettlement();
        autoSettlement.setEnabled(Boolean.parseBoolean(firstText(
                extensionValue(request, "mid.requestAutoSettlement"),
                extensionValue(request, "requestAutoSettlement"),
                ChannelCapability.PAYMENT.getCode().equals(transactionType) ? "true" : "false")));
        return autoSettlement;
    }

    /**
     * 判断当前 WPGJSON 交易是否需要金额和币种。
     *
     * @param transactionType 平台交易类型
     * @return true 表示请求体需要 instruction.value
     */
    private boolean requiresAmount(String transactionType) {
        return !ChannelCapability.VOID.getCode().equals(transactionType)
                && !ChannelCapability.REVERSAL.getCode().equals(transactionType)
                && !ChannelCapability.QUERY.getCode().equals(transactionType);
    }

    /**
     * 判断当前 WPGJSON 交易是否需要卡数据。
     *
     * @param transactionType 平台交易类型
     * @return true 表示需要提交 paymentInstrument
     */
    private boolean requiresCard(String transactionType) {
        return ChannelCapability.PAYMENT.getCode().equals(transactionType)
                || ChannelCapability.AUTHORIZATION.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTHORIZATION.getCode().equals(transactionType);
    }

    /**
     * 构造 Worldpay 金额节点。
     *
     * @param request 平台统一渠道请求
     * @return 金额节点，amount 为最小辅币单位
     */
    private WorldPayJsonRequestPayload.Value value(ChannelPaymentRequest request) {
        String currency = currency(request.getCurrency());
        WorldPayJsonRequestPayload.Value value = new WorldPayJsonRequestPayload.Value();
        value.setCurrency(currency);
        value.setAmount(minorAmount(request.getAmount(), currency, request.getExtension()));
        return value;
    }

    /**
     * 构造 Worldpay 支付工具节点。
     *
     * @param request 平台统一渠道请求，包含 PAN、有效期和 CVC
     * @return 支付工具节点
     */
    private WorldPayJsonRequestPayload.PaymentInstrument paymentInstrument(ChannelPaymentRequest request) {
        requiredText(request.getCardNo(), "WorldPay JSON card number is required");
        requiredText(request.getExpirationMonth(), "WorldPay JSON card expiry month is required");
        requiredText(request.getExpirationYear(), "WorldPay JSON card expiry year is required");
        requiredText(request.getSecurityCode(), "WorldPay JSON card security code is required");
        WorldPayJsonRequestPayload.PaymentInstrument paymentInstrument = new WorldPayJsonRequestPayload.PaymentInstrument();
        paymentInstrument.setType(paymentInstrumentType(request));
        paymentInstrument.setCardNumber(request.getCardNo());
        paymentInstrument.setCardExpiryDate(expiryDate(request));
        paymentInstrument.setCvc(request.getSecurityCode());
        paymentInstrument.setCardBrand(request.getCardBrand());
        paymentInstrument.setCardHolderName(firstText(extensionValue(request, "cardHolderName"), billingFullName(request)));
        paymentInstrument.setBillingAddress(billingAddress(request));
        return paymentInstrument;
    }

    /**
     * 解析 Worldpay JSON 支付工具类型。
     * <p>
     * Payments API 的 /api/payments 使用 plain；Card Payments v7 的 /cardPayments/customerInitiatedTransactions 使用 card/plain。
     * 调用方可通过 MID 元数据显式覆盖，避免不同 Worldpay API 族混用导致 validation error。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @return paymentInstrument.type
     */
    private String paymentInstrumentType(ChannelPaymentRequest request) {
        String configured = firstText(extensionValue(request, "mid.paymentInstrumentType"),
                extensionValue(request, "paymentInstrumentType"));
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        String apiFamily = firstText(extensionValue(request, "mid.apiFamily"), extensionValue(request, "apiFamily"));
        String endpointPath = firstText(extensionValue(request, "mid.endpointPath"), extensionValue(request, "mid.paymentPath"));
        if ("CARD_PAYMENTS".equalsIgnoreCase(apiFamily)
                || (StringUtils.hasText(endpointPath) && endpointPath.toLowerCase(Locale.ROOT).contains("cardpayments"))) {
            return CARD_PAYMENTS_PAYMENT_INSTRUMENT_TYPE;
        }
        return DEFAULT_PAYMENTS_PAYMENT_INSTRUMENT_TYPE;
    }

    /**
     * 构造 Worldpay 卡有效期节点。
     *
     * @param request 平台统一渠道请求
     * @return 有效期节点
     */
    private WorldPayJsonRequestPayload.ExpiryDate expiryDate(ChannelPaymentRequest request) {
        WorldPayJsonRequestPayload.ExpiryDate expiryDate = new WorldPayJsonRequestPayload.ExpiryDate();
        expiryDate.setMonth(normalizeMonth(request.getExpirationMonth()));
        expiryDate.setYear(normalizeYear(request.getExpirationYear()));
        return expiryDate;
    }

    /**
     * 构造 Worldpay 账单地址节点。
     *
     * @param request 平台统一渠道请求
     * @return 账单地址；未提供任何地址字段时返回 null
     */
    private WorldPayJsonRequestPayload.BillingAddress billingAddress(ChannelPaymentRequest request) {
        ChannelPaymentRequest.BillingInfo billing = request.getBillingInfo();
        if (billing == null) {
            return null;
        }
        if (!StringUtils.hasText(billing.getStreet())
                && !StringUtils.hasText(billing.getPostal())
                && !StringUtils.hasText(billing.getCity())
                && !StringUtils.hasText(billing.getState())
                && !StringUtils.hasText(billing.getCountry())) {
            return null;
        }
        WorldPayJsonRequestPayload.BillingAddress address = new WorldPayJsonRequestPayload.BillingAddress();
        address.setAddress1(billing.getStreet());
        address.setPostalCode(billing.getPostal());
        address.setCity(billing.getCity());
        address.setState(billing.getState());
        address.setCountryCode(billing.getCountry());
        return address;
    }

    /**
     * 构造 Worldpay 认证节点。
     *
     * @param request 平台统一渠道请求
     * @return 认证节点；没有 3DS 结果时返回 null
     */
    private WorldPayJsonRequestPayload.Authentication authentication(ChannelPaymentRequest request) {
        ChannelPaymentRequest.ThreeDsInfo threeDsInfo = request.getThreeDsInfo();
        if (threeDsInfo == null || (!StringUtils.hasText(threeDsInfo.getEci())
                && !StringUtils.hasText(threeDsInfo.getCavv())
                && !StringUtils.hasText(threeDsInfo.getDsTransactionId())
                && !StringUtils.hasText(threeDsInfo.getThreeDsVersion()))) {
            return null;
        }
        WorldPayJsonRequestPayload.ThreeDS threeDS = new WorldPayJsonRequestPayload.ThreeDS();
        threeDS.setVersion(threeDsInfo.getThreeDsVersion());
        threeDS.setEci(threeDsInfo.getEci());
        threeDS.setAuthenticationValue(threeDsInfo.getCavv());
        threeDS.setDsTransactionId(threeDsInfo.getDsTransactionId());
        WorldPayJsonRequestPayload.Authentication authentication = new WorldPayJsonRequestPayload.Authentication();
        authentication.setThreeDS(threeDS);
        return authentication;
    }

    /**
     * 构造账单描述节点。
     *
     * @param request 平台统一渠道请求
     * @return 账单描述节点；未配置时返回 null
     */
    private WorldPayJsonRequestPayload.Narrative narrative(ChannelPaymentRequest request) {
        String line1 = firstText(
                extensionValue(request, "mid.statementNarrative"),
                extensionValue(request, "mid.narrative"),
                extensionValue(request, "statementNarrative"));
        if (!StringUtils.hasText(line1)) {
            return null;
        }
        WorldPayJsonRequestPayload.Narrative narrative = new WorldPayJsonRequestPayload.Narrative();
        narrative.setLine1(line1);
        return narrative;
    }

    /**
     * 解析渠道交易引用。
     *
     * @param request 平台统一渠道请求
     * @return transactionReference
     */
    private String transactionReference(ChannelPaymentRequest request) {
        return requiredText(firstText(
                request.getChannelTransactionId(),
                extensionValue(request, "requestId"),
                request.getTransactionId()), "WorldPay JSON transactionReference is required");
    }

    /**
     * 从账单信息生成持卡人姓名。
     *
     * @param request 平台统一渠道请求
     * @return 持卡人姓名；账单姓名为空时返回 null
     */
    private String billingFullName(ChannelPaymentRequest request) {
        ChannelPaymentRequest.BillingInfo billing = request == null ? null : request.getBillingInfo();
        if (billing == null) {
            return null;
        }
        return firstText((firstText(billing.getFirstName(), "") + " " + firstText(billing.getLastName(), "")).trim(), null);
    }

    /**
     * 解析后续动作关联的原渠道交易 ID 或 action link。
     * <p>
     * 请款、退款和撤销需要定位原渠道交易；优先使用 Worldpay 返回的 action link，其次使用 sourceChannelTransactionId 这类渠道侧交易号。
     * 不能把平台 sourceTransactionId 当成渠道交易号发给 Worldpay。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @param transactionType 平台交易类型
     * @return 原渠道 action link 或交易 ID；首次交易或查询允许为空
     */
    private String sourceTransactionId(ChannelPaymentRequest request, String transactionType) {
        if (!(ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)
                || ChannelCapability.REFUND.getCode().equals(transactionType)
                || ChannelCapability.VOID.getCode().equals(transactionType)
                || ChannelCapability.REVERSAL.getCode().equals(transactionType))) {
            return request.getSourceTransactionId();
        }
        String target = firstText(
                extensionValue(request, "worldpayActionLink"),
                extensionValue(request, "worldpaySettleLink"),
                extensionValue(request, "worldpayCaptureLink"),
                extensionValue(request, "worldpayRefundLink"),
                extensionValue(request, "worldpayCancelLink"),
                extensionValue(request, "worldpayVoidLink"),
                extensionValue(request, "targetActionLink"),
                extensionValue(request, "targetTransactionId"),
                extensionValue(request, "sourceChannelTransactionId"),
                extensionValue(request, "sourceChannelTransactionNo"));
        return requiredText(target, "WorldPay JSON source action link or channel transactionId is required for follow-up operation");
    }

    /**
     * 将主币种金额转换为最小辅币单位。
     *
     * @param amount 主币种金额
     * @param currency ISO 4217 三位币种代码
     * @param extension 渠道扩展字段，优先读取 service-payment 透传的 currencyExponent
     * @return 最小辅币单位整数
     */
    private long minorAmount(BigDecimal amount, String currency, Map<String, String> extension) {
        if (amount == null) {
            throw new ChannelRequestException("WorldPay JSON transaction amount is required");
        }
        if (amount.signum() <= 0) {
            throw new ChannelRequestException("WorldPay JSON transaction amount must be greater than 0");
        }
        Integer exponent = currencyExponent(extension);
        try {
            if (exponent != null) {
                return amount
                        .movePointRight(exponent)
                        .setScale(0, RoundingMode.UNNECESSARY)
                        .longValueExact();
            }
            IsoCurrencyInfo currencyInfo = IsoCurrencyResolver.resolve(currency)
                    .orElseThrow(() -> new ChannelRequestException("WorldPay JSON transaction currency can not be resolved"));
            return IsoCurrencyResolver.toMinorUnit(amount, currencyInfo);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ChannelRequestException("WorldPay JSON amount fraction digits exceed currency minor unit", exception);
        }
    }

    /**
     * 从扩展字段读取交易币种辅币位。
     *
     * @param extension 渠道扩展字段
     * @return 辅币位；未传入返回 null
     */
    private Integer currencyExponent(Map<String, String> extension) {
        String value = extension == null ? null : firstText(
                extension.get("currencyExponent"),
                extension.get("mid.currencyExponent"));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            int exponent = Integer.parseInt(value);
            if (exponent < 0 || exponent > 9) {
                throw new ChannelRequestException("WorldPay JSON currencyExponent is out of range");
            }
            return exponent;
        } catch (NumberFormatException exception) {
            throw new ChannelRequestException("WorldPay JSON currencyExponent is invalid", exception);
        }
    }

    /**
     * 校验并标准化币种字段。
     *
     * @param currency 平台交易币种
     * @return 大写 ISO 4217 币种代码
     */
    private String currency(String currency) {
        return requiredText(currency, "WorldPay JSON transaction currency is required").toUpperCase(Locale.ROOT);
    }

    /**
     * 标准化银行卡有效期月份。
     *
     * @param month 银行卡有效期月份
     * @return 两位月份
     */
    private String normalizeMonth(String month) {
        String normalized = requiredText(month, "WorldPay JSON card expiry month is required");
        return normalized.length() == 1 ? "0" + normalized : normalized;
    }

    /**
     * 标准化银行卡有效期年份。
     *
     * @param year 银行卡有效期年份
     * @return 四位年份
     */
    private String normalizeYear(String year) {
        String normalized = requiredText(year, "WorldPay JSON card expiry year is required");
        if (normalized.length() == 2) {
            return "20" + normalized;
        }
        return normalized;
    }

    /**
     * 标准化平台交易类型。
     *
     * @param transactionType 平台交易类型
     * @return 大写交易类型
     */
    private String normalizeType(String transactionType) {
        return requiredText(transactionType, "WorldPay JSON transactionType is required").toUpperCase(Locale.ROOT);
    }

    /**
     * 写入受控 metadata。
     *
     * @param payload WPGJSON 请求体
     * @param request 平台统一渠道请求
     * @param key metadata key
     * @param value metadata value
     */
    private void putMetadata(WorldPayJsonRequestPayload payload, ChannelPaymentRequest request, String key, String value) {
        if (payload == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        payload.getMetadata().put(key, value.trim());
    }

    /**
     * 读取渠道扩展字段。
     *
     * @param request 平台统一渠道请求
     * @param key 扩展字段 key
     * @return 扩展字段值
     */
    private String extensionValue(ChannelPaymentRequest request, String key) {
        return request == null || request.getExtension() == null ? null : request.getExtension().get(key);
    }

    /**
     * 返回首个非空文本。
     *
     * @param values 候选文本
     * @return 首个非空文本
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 校验文本输入，避免缺失必要协议字段。
     *
     * @param value 待校验文本
     * @param message 缺失时抛出的错误消息
     * @return 去除首尾空白后的文本
     */
    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ChannelRequestException(message);
        }
        return value.trim();
    }
}
