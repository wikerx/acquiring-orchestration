package com.scott.payment.channel.payment.mpgs;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.component.core.json.JsonUtils;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsRequestMapper
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 渠道请求映射组件，位于 payment-channel-mpgs 渠道适配层，负责把平台统一支付请求转换为 MPGS 授权、支付、增量授权、请款、退款和撤销请求字段；输入为 ChannelPaymentRequest，输出为 MpgsRequestPayload，边界限定在本地参数校验、金额币种格式化、卡信息与 3DS 字段组装，不发起渠道网络调用。
 * @status : create
 */
public class MpgsRequestMapper {

    /**
     * 将平台统一交易请求转换为 MPGS 请求体。
     * <p>
     * 前置条件：request、channelOrderNo、channelTransactionId、transactionType 必须存在；支付、授权和预授权必须携带卡号、有效期和安全码；撤销类交易必须在 extension.targetTransactionId 中提供原渠道交易号。
     * 本方法只做本地对象映射和参数校验，不写数据库、不发起外部系统调用、不改变交易状态；幂等由调用方使用平台交易号和渠道订单号控制。
     * 卡号、有效期、安全码和 3DS CAVV 属于敏感或高敏感数据，只进入渠道请求对象，禁止在日志和异常消息中输出明文。
     * </p>
     * @param request 平台统一渠道请求，来源于支付核心渠道调用链，包含交易类型、金额币种、平台交易号、渠道订单号、卡数据和可选 3DS 认证数据
     * @return MPGS 请求体，包含 apiOperation、order、transaction、sourceOfFunds 和 authentication 等渠道协议字段
     */
    public MpgsRequestPayload toMpgsRequest(ChannelPaymentRequest request) {
        validateCommonRequest(request);
        MpgsRequestPayload payload = new MpgsRequestPayload();
        String transactionType = normalizeType(request.getTransactionType());
        payload.setApiOperation(toApiOperation(transactionType));
        if (requiresOrderAmount(transactionType)) {
            payload.setOrder(order(request));
        } else if (ChannelCapability.INCREMENTAL_AUTHORIZATION.getCode().equals(transactionType)) {
            MpgsRequestPayload.Order order = new MpgsRequestPayload.Order();
            order.setReference(request.getTransactionId());
            payload.setOrder(order);
        }
        payload.setTransaction(transaction(request, transactionType));
        if (requiresCard(request, transactionType)) {
            payload.setSourceOfFunds(sourceOfFunds(request));
        }
        if (requiresCard(request, transactionType) && request.getThreeDsInfo() != null) {
            payload.setAuthentication(authentication(request.getThreeDsInfo()));
        }
        return payload;
    }

    /**
     * 将平台 3DS 认证请求转换为 MPGS authentication 请求体，不发起网络调用。
     */
    public MpgsRequestPayload toMpgsThreeDsRequest(MpgsThreeDsAuthenticationRequest request,
                                                   String apiOperation) {
        validateThreeDsRequest(request, apiOperation);
        MpgsRequestPayload payload = new MpgsRequestPayload();
        payload.setApiOperation(apiOperation);
        payload.setOrder(threeDsOrder(request, apiOperation));
        payload.setSourceOfFunds(threeDsSourceOfFunds(request, apiOperation));
        payload.setAuthentication(authentication(request, apiOperation));
        if (MpgsApiOperation.AUTHENTICATE_PAYER.equals(apiOperation)) {
            payload.setDevice(device(request.getBrowserInfoJson()));
        }
        return payload;
    }

    /**
     * 校验 MPGS 请求映射所需的公共交易标识。
     * <p>
     * request 不能为空，channelOrderNo 用于 MPGS order 维度识别，channelTransactionId 用于渠道交易维度识别，transactionType 用于选择 apiOperation。
     * 校验失败时抛出 ChannelRequestException；本方法不读取敏感卡数据、不写状态、不调用外部系统。
     * </p>
     * @param request 平台统一渠道请求，来源于支付核心的渠道调用入口
     */
    private void validateCommonRequest(ChannelPaymentRequest request) {
        if (request == null) {
            throw new ChannelRequestException("MPGS request is required");
        }
        requireText(request.getChannelOrderNo(), "MPGS channelOrderNo is required");
        requireText(request.getChannelTransactionId(), "MPGS channelTransactionId is required");
        requireText(request.getTransactionType(), "MPGS transactionType is required");
    }

    /**
     * 将平台交易能力映射为 MPGS apiOperation。
     * <p>
     * 支付映射 PAY，授权和预授权映射 AUTHORIZE，增量授权映射 UPDATE_AUTHORIZATION，请款映射 CAPTURE，退款映射 REFUND，撤销和冲正映射 VOID。
     * 未支持的交易类型会抛出 ChannelRequestException，调用方需阻断后续渠道请求。
     * </p>
     * @param transactionType 交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型
     * @return MPGS apiOperation 字符串
     */
    private String toApiOperation(String transactionType) {
        if (ChannelCapability.PAYMENT.getCode().equals(transactionType)) {
            return MpgsApiOperation.PAY;
        }
        if (ChannelCapability.AUTHORIZATION.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTHORIZATION.getCode().equals(transactionType)) {
            return MpgsApiOperation.AUTHORIZE;
        }
        if (ChannelCapability.INCREMENTAL_AUTHORIZATION.getCode().equals(transactionType)) {
            return MpgsApiOperation.UPDATE_AUTHORIZATION;
        }
        if (ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)) {
            return MpgsApiOperation.CAPTURE;
        }
        if (ChannelCapability.REFUND.getCode().equals(transactionType)) {
            return MpgsApiOperation.REFUND;
        }
        if (ChannelCapability.VOID.getCode().equals(transactionType)
                || ChannelCapability.REVERSAL.getCode().equals(transactionType)) {
            return MpgsApiOperation.VOID;
        }
        throw new ChannelRequestException("MPGS unsupported transaction type: " + transactionType);
    }

    /**
     * 判断当前交易是否需要在 MPGS order 节点携带金额和币种。
     * <p>
     * 首次支付、授权和预授权创建新的订单金额语义，需要 order.amount 与 order.currency；后续请款、退款、增量授权等操作金额写入 transaction 节点。
     * </p>
     * @param transactionType 交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型
     * @return 当前交易需要 order 金额币种时返回 true，否则返回 false
     */
    private boolean requiresOrderAmount(String transactionType) {
        return ChannelCapability.PAYMENT.getCode().equals(transactionType)
                || ChannelCapability.AUTHORIZATION.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTHORIZATION.getCode().equals(transactionType);
    }

    /**
     * 判断当前交易是否需要卡信息，并校验卡号、有效期和安全码。
     * <p>
     * 支付、授权和预授权为持卡人发起交易，需要 sourceOfFunds.provided.card；请款、退款、增量授权和撤销基于既有渠道交易号处理，不重复提交卡数据。
     * 卡号、有效期和 securityCode 属于敏感或高敏感认证数据，只允许进入 MPGS 请求体。
     * </p>
     * @param request 平台统一渠道请求，提供卡号、有效期和安全码
     * @param transactionType 交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型
     * @return 当前交易需要提交卡数据时返回 true，否则返回 false
     */
    private boolean requiresCard(ChannelPaymentRequest request, String transactionType) {
        if (!(ChannelCapability.PAYMENT.getCode().equals(transactionType)
                || ChannelCapability.AUTHORIZATION.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTHORIZATION.getCode().equals(transactionType))) {
            return false;
        }
        requireText(request.getCardNo(), "MPGS card number is required");
        requireText(request.getExpirationMonth(), "MPGS card expiry month is required");
        requireText(request.getExpirationYear(), "MPGS card expiry year is required");
        requireText(request.getSecurityCode(), "MPGS card security code is required");
        return true;
    }

    /**
     * 构造 MPGS order 节点。
     * <p>
     * order.amount 来源于平台交易金额，order.currency 来源于平台交易币种，order.reference 使用平台交易号，便于渠道侧订单维度与平台交易维度关联。
     * 金额必须大于 0，币种必须为 ISO 4217 代码；本方法不做汇率换算和舍入。
     * </p>
     * @param request 平台统一渠道请求，提供金额、币种和平台交易号
     * @return MPGS order 节点
     */
    private MpgsRequestPayload.Order order(ChannelPaymentRequest request) {
        MpgsRequestPayload.Order order = new MpgsRequestPayload.Order();
        order.setAmount(amount(request.getAmount()));
        order.setCurrency(currency(request.getCurrency()));
        order.setReference(request.getTransactionId());
        return order;
    }

    /**
     * 构造 MPGS transaction 节点。
     * <p>
     * 请款、退款和增量授权在 transaction 节点携带本次操作金额与币种；所有交易均写入平台交易号作为 reference。
     * 撤销和冲正还需要 targetTransactionId 指向原渠道交易，缺失时抛出 ChannelRequestException，避免无目标撤销。
     * </p>
     * @param request 平台统一渠道请求，提供金额、币种、平台交易号和可选原渠道交易号
     * @param transactionType 交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型
     * @return MPGS transaction 节点
     */
    private MpgsRequestPayload.Transaction transaction(ChannelPaymentRequest request, String transactionType) {
        MpgsRequestPayload.Transaction transaction = new MpgsRequestPayload.Transaction();
        if (requiresTransactionAmount(transactionType)) {
            transaction.setAmount(amount(request.getAmount()));
            transaction.setCurrency(currency(request.getCurrency()));
        }
        transaction.setReference(request.getTransactionId());
        if (ChannelCapability.VOID.getCode().equals(transactionType)
                || ChannelCapability.REVERSAL.getCode().equals(transactionType)) {
            transaction.setTargetTransactionId(requiredTargetTransactionId(request));
        }
        return transaction;
    }

    /**
     * 判断当前交易是否需要在 MPGS transaction 节点携带金额和币种。
     * <p>
     * 请款、预授权完成、退款和增量授权是对既有订单或授权的金额操作，需要 transaction.amount 与 transaction.currency。
     * 支付、授权和预授权的初始金额放在 order 节点，撤销和冲正依赖原交易号。
     * </p>
     * @param transactionType 交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型
     * @return 当前交易需要 transaction 金额币种时返回 true，否则返回 false
     */
    private boolean requiresTransactionAmount(String transactionType) {
        return ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)
                || ChannelCapability.REFUND.getCode().equals(transactionType)
                || ChannelCapability.INCREMENTAL_AUTHORIZATION.getCode().equals(transactionType);
    }

    /**
     * 构造 MPGS sourceOfFunds.card 节点。
     * <p>
     * 卡号、有效期月份、有效期年份和 securityCode 来源于平台渠道请求；securityCode 为高敏感认证数据，仅用于本次渠道请求，不允许入库或明文日志输出。
     * sourceOfFunds.type 固定为 CARD，与 provided.card 节点共同表达持卡人卡支付资金来源。
     * </p>
     * @param request 平台统一渠道请求，提供卡号、有效期和安全码
     * @return MPGS sourceOfFunds 节点
     */
    private MpgsRequestPayload.SourceOfFunds sourceOfFunds(ChannelPaymentRequest request) {
        MpgsRequestPayload.Expiry expiry = new MpgsRequestPayload.Expiry();
        expiry.setMonth(normalizeMonth(request.getExpirationMonth()));
        expiry.setYear(normalizeYear(request.getExpirationYear()));

        MpgsRequestPayload.Card card = new MpgsRequestPayload.Card();
        card.setNumber(request.getCardNo());
        card.setExpiry(expiry);
        card.setSecurityCode(request.getSecurityCode());

        MpgsRequestPayload.Provided provided = new MpgsRequestPayload.Provided();
        provided.setCard(card);

        MpgsRequestPayload.SourceOfFunds sourceOfFunds = new MpgsRequestPayload.SourceOfFunds();
        sourceOfFunds.setType(MpgsApiOperation.CARD);
        sourceOfFunds.setProvided(provided);
        return sourceOfFunds;
    }

    /**
     * 构造使用 MPGS 网关认证结果的 payment/authorization authentication 节点。
     * <p>
     * MPGS 文档要求：付款人由同一网关完成认证时只发送 authentication.transactionId；
     * authentication.3ds/3ds2 字段仅适用于其他 3DS 服务商，不能与网关认证引用混用。
     * </p>
     * @param source 平台统一 3DS 信息，必须提供已通过的 MPGS authentication transaction id
     * @return MPGS authentication 节点
     */
    private MpgsRequestPayload.Authentication authentication(ChannelPaymentRequest.ThreeDsInfo source) {
        MpgsRequestPayload.Authentication authentication = new MpgsRequestPayload.Authentication();
        requireText(source.getAuthenticationTransactionId(),
                "MPGS authenticationTransactionId is required for a 3DS payment or authorization");
        authentication.setTransactionId(source.getAuthenticationTransactionId());
        return authentication;
    }

    /**
     * 校验 MPGS 3DS 请求必填字段，AUTHENTICATE_PAYER 必须携带平台回跳地址。
     */
    private void validateThreeDsRequest(MpgsThreeDsAuthenticationRequest request, String apiOperation) {
        if (request == null) {
            throw new ChannelRequestException("MPGS 3DS request is required");
        }
        requireText(request.getChannelOrderNo(), "MPGS 3DS channelOrderNo is required");
        requireText(request.getAuthenticationTransactionId(), "MPGS 3DS authenticationTransactionId is required");
        requireText(apiOperation, "MPGS 3DS apiOperation is required");
        requireText(request.getCardNo(), "MPGS card number is required");
        requireText(request.getExpirationMonth(), "MPGS card expiry month is required");
        requireText(request.getExpirationYear(), "MPGS card expiry year is required");
        if (MpgsApiOperation.AUTHENTICATE_PAYER.equals(apiOperation)) {
            requireText(request.getRedirectResponseUrl(), "MPGS 3DS redirectResponseUrl is required");
        }
    }

    /**
     * 构造 MPGS 3DS order 节点，金额币种与后续支付请求保持一致。
     */
    private MpgsRequestPayload.Order threeDsOrder(MpgsThreeDsAuthenticationRequest request,
                                                  String apiOperation) {
        MpgsRequestPayload.Order order = new MpgsRequestPayload.Order();
        order.setCurrency(currency(request.getCurrency()));
        if (MpgsApiOperation.INITIATE_AUTHENTICATION.equals(apiOperation)) {
            order.setReference(request.getTransactionId());
        } else if (MpgsApiOperation.AUTHENTICATE_PAYER.equals(apiOperation)) {
            order.setAmount(amount(request.getAmount()));
        }
        return order;
    }

    /**
     * 构造 MPGS 3DS 卡信息节点。Initiate 只接受 PAN；Authenticate Payer 接受有效期和 CVV，
     * 但当前网关版本会拒绝 sourceOfFunds.type，因此该阶段只提交 provided.card。
     */
    private MpgsRequestPayload.SourceOfFunds threeDsSourceOfFunds(MpgsThreeDsAuthenticationRequest request,
                                                                 String apiOperation) {
        MpgsRequestPayload.Card card = new MpgsRequestPayload.Card();
        card.setNumber(request.getCardNo());
        if (MpgsApiOperation.AUTHENTICATE_PAYER.equals(apiOperation)) {
            MpgsRequestPayload.Expiry expiry = new MpgsRequestPayload.Expiry();
            expiry.setMonth(normalizeMonth(request.getExpirationMonth()));
            expiry.setYear(normalizeYear(request.getExpirationYear()));
            card.setExpiry(expiry);
            card.setSecurityCode(request.getSecurityCode());
        }

        MpgsRequestPayload.Provided provided = new MpgsRequestPayload.Provided();
        provided.setCard(card);

        MpgsRequestPayload.SourceOfFunds sourceOfFunds = new MpgsRequestPayload.SourceOfFunds();
        if (MpgsApiOperation.INITIATE_AUTHENTICATION.equals(apiOperation)) {
            sourceOfFunds.setType(MpgsApiOperation.CARD);
        }
        sourceOfFunds.setProvided(provided);
        return sourceOfFunds;
    }

    /** 构造符合 MPGS Direct REST 字段定义的 authentication 节点。 */
    private MpgsRequestPayload.Authentication authentication(MpgsThreeDsAuthenticationRequest request,
                                                            String apiOperation) {
        MpgsRequestPayload.Authentication authentication = new MpgsRequestPayload.Authentication();
        if (MpgsApiOperation.INITIATE_AUTHENTICATION.equals(apiOperation)) {
            authentication.setAcceptVersions("3DS2");
            authentication.setChannel("PAYER_BROWSER");
            authentication.setPurpose("PAYMENT_TRANSACTION");
        } else if (MpgsApiOperation.AUTHENTICATE_PAYER.equals(apiOperation)) {
            authentication.setRedirectResponseUrl(request.getRedirectResponseUrl());
        }
        return authentication;
    }

    /** 将平台浏览器摘要转换为 MPGS Authenticate Payer 的 device.browserDetails。 */
    private MpgsRequestPayload.Device device(String browserInfoJson) {
        BrowserInfo browserInfo;
        try {
            browserInfo = JsonUtils.parseObject(browserInfoJson, BrowserInfo.class);
        } catch (RuntimeException exception) {
            throw new ChannelRequestException("MPGS 3DS browserInfoJson is invalid", exception);
        }
        if (browserInfo == null) {
            throw new ChannelRequestException("MPGS 3DS browserInfoJson is required");
        }
        requireText(browserInfo.getUserAgent(), "MPGS 3DS browser userAgent is required");
        requireText(browserInfo.getAcceptHeaders(), "MPGS 3DS browser acceptHeaders is required");
        requireText(browserInfo.getChallengeWindowSize(), "MPGS 3DS challengeWindowSize is required");
        requireText(browserInfo.getLanguage(), "MPGS 3DS browser language is required");
        if (browserInfo.getColorDepth() == null
                || browserInfo.getJavaEnabled() == null
                || browserInfo.getJavaScriptEnabled() == null
                || browserInfo.getScreenHeight() == null
                || browserInfo.getScreenWidth() == null
                || browserInfo.getTimezoneOffset() == null) {
            throw new ChannelRequestException("MPGS 3DS browser details are incomplete");
        }

        MpgsRequestPayload.BrowserDetails details = new MpgsRequestPayload.BrowserDetails();
        details.setChallengeWindowSize(browserInfo.getChallengeWindowSize());
        details.setAcceptHeaders(browserInfo.getAcceptHeaders());
        details.setColorDepth(browserInfo.getColorDepth());
        details.setJavaEnabled(browserInfo.getJavaEnabled());
        details.setJavaScriptEnabled(browserInfo.getJavaScriptEnabled());
        details.setLanguage(browserInfo.getLanguage());
        details.setScreenHeight(browserInfo.getScreenHeight());
        details.setScreenWidth(browserInfo.getScreenWidth());
        details.setTimeZone(browserInfo.getTimezoneOffset());

        MpgsRequestPayload.Device device = new MpgsRequestPayload.Device();
        device.setBrowser(browserInfo.getUserAgent());
        device.setBrowserDetails(details);
        return device;
    }

    /** 平台浏览器摘要的内部解析模型，不作为 MPGS 或平台公共 DTO 暴露。 */
    @Data
    private static class BrowserInfo {
        private String userAgent;
        private String acceptHeaders;
        private String challengeWindowSize;
        private Integer colorDepth;
        private Boolean javaEnabled;
        private Boolean javaScriptEnabled;
        private String language;
        private Integer screenHeight;
        private Integer screenWidth;
        private Integer timezoneOffset;
    }

    /**
     * 读取并校验撤销或冲正所需的原渠道交易号。
     * <p>
     * targetTransactionId 来自 request.extension，用于 MPGS transaction.targetTransactionId，必须指向待撤销的原授权或支付交易。
     * 缺失时抛出 ChannelRequestException，避免渠道收到无法定位原交易的撤销请求。
     * </p>
     * @param request 平台统一渠道请求，extension 中应包含 targetTransactionId
     * @return 原渠道交易号
     */
    private String requiredTargetTransactionId(ChannelPaymentRequest request) {
        String targetTransactionId = request.getExtension() == null ? null : request.getExtension().get("targetTransactionId");
        requireText(targetTransactionId, "MPGS target transactionId is required for void");
        return targetTransactionId;
    }

    /**
     * 校验并格式化 MPGS 金额字段。
     * <p>
     * 金额单位由 currency 决定，必须大于 0；输出使用十进制普通字符串，至少保留 2 位小数后再去除无意义尾零。
     * RoundingMode.UNNECESSARY 保证这里不发生隐式舍入；如上游金额精度不符合当前数值表达，会由 BigDecimal 抛出异常。
     * </p>
     * @param amount 平台交易金额，单位由同一请求中的 currency 决定
     * @return MPGS amount 字符串
     */
    private String amount(BigDecimal amount) {
        if (amount == null) {
            throw new ChannelRequestException("MPGS transaction amount is required");
        }
        if (amount.signum() <= 0) {
            throw new ChannelRequestException("MPGS transaction amount must be greater than 0");
        }
        return amount.setScale(Math.max(amount.scale(), 2), RoundingMode.UNNECESSARY).stripTrailingZeros().toPlainString();
    }

    /**
     * 校验并标准化 MPGS 币种字段。
     * <p>
     * currency 来源于平台交易币种，格式为 ISO 4217 三位字母；本方法只做必填校验、去除首尾空白和大写转换，不做币种有效性查询。
     * </p>
     * @param currency 平台交易币种代码
     * @return 大写 ISO 4217 币种代码
     */
    private String currency(String currency) {
        requireText(currency, "MPGS transaction currency is required");
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 标准化银行卡有效期月份。
     * <p>
     * month 来源于持卡人卡信息，允许输入 1 位或 2 位月份；1 位月份会左补 0 以满足 MPGS expiry.month 格式。
     * 有效期属于敏感卡数据的一部分，只用于组装渠道请求。
     * </p>
     * @param month 银行卡有效期月份
     * @return 两位月份字符串
     */
    private String normalizeMonth(String month) {
        requireText(month, "MPGS card expiry month is required");
        String normalized = month.trim();
        return normalized.length() == 1 ? "0" + normalized : normalized;
    }

    /**
     * 标准化银行卡有效期年份。
     * <p>
     * year 来源于持卡人卡信息；四位年份会截取后两位以匹配 MPGS expiry.year，二位年份原样返回。
     * 本方法只做格式转换，不判断卡片是否过期。
     * </p>
     * @param year 银行卡有效期年份
     * @return 两位年份字符串
     */
    private String normalizeYear(String year) {
        requireText(year, "MPGS card expiry year is required");
        String normalized = year.trim();
        if (normalized.length() == 4) {
            return normalized.substring(2);
        }
        return normalized;
    }

    /**
     * 标准化平台交易类型。
     * <p>
     * transactionType 来源于 ChannelCapability 编码，去除首尾空白并转为大写，保证后续枚举编码比较稳定。
     * </p>
     * @param transactionType 交易类型编码，取值来自平台交易能力枚举并会映射为渠道操作类型
     * @return 大写交易类型编码
     */
    private String normalizeType(String transactionType) {
        return transactionType.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 校验 MPGS 请求字段是否为空白。
     * <p>
     * value 为空时抛出 ChannelRequestException，message 只包含字段名称和缺失原因，不应拼接卡号、安全码、CAVV 等敏感值。
     * </p>
     * @param value 待校验的渠道请求字段值
     * @param message 字段缺失时抛出的错误消息
     */
    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ChannelRequestException(message);
        }
    }
}
