package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.component.core.iso.IsoCountryResolver;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayXmlRequestMapper
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : WorldPay XML 请求映射器，位于 payment-channel-worldpay 渠道协议层，负责把平台统一渠道请求映射为 WPGXML 请求对象，并委托 XML 编码器生成 paymentService 报文；输入包含交易类型、金额币种、卡数据和 MID 扩展，输出为可发送到 Worldpay XML Direct 的请求 XML。
 * @status : create
 */
public class WorldPayXmlRequestMapper {

    /**
     * XML 编码器，统一负责节点创建、属性写入、CDATA 和 XML 转义。
     */
    private final WorldPayXmlCodec xmlCodec;

    /**
     * 创建 WPGXML 请求映射器。
     */
    public WorldPayXmlRequestMapper() {
        this(new WorldPayXmlCodec());
    }

    /**
     * 创建 WPGXML 请求映射器。
     *
     * @param xmlCodec XML 编码器
     */
    WorldPayXmlRequestMapper(WorldPayXmlCodec xmlCodec) {
        this.xmlCodec = xmlCodec;
    }

    /**
     * 将平台渠道请求映射并序列化为 Worldpay WPGXML 请求。
     *
     * @param request 已完成路由和金额币种校验的平台渠道请求
     * @param merchantCode 当前路由命中的 Worldpay MID 商户代码
     * @return 可直接发送给 Worldpay 的 XML 请求文本
     */
    public String toWorldPayRequest(ChannelPaymentRequest request, String merchantCode) {
        return xmlCodec.writeRequest(toWorldPayPayload(request, merchantCode));
    }

    /**
     * 构造 WPGXML 请求对象。
     *
     * @param request 平台统一渠道请求
     * @param merchantCode Worldpay merchantCode
     * @return WPGXML 请求对象
     */
    public WorldPayXmlRequestPayload toWorldPayPayload(ChannelPaymentRequest request, String merchantCode) {
        validateCommonRequest(request);
        String transactionType = normalizeType(request.getTransactionType());
        WorldPayXmlRequestPayload payload = new WorldPayXmlRequestPayload();
        payload.setVersion(firstText(extensionValue(request, "mid.version"), "1.4"));
        payload.setMerchantCode(requiredText(merchantCode, "WorldPay XML merchantCode is required"));
        if (ChannelCapability.QUERY.getCode().equals(transactionType)) {
            payload.setInquiry(inquiry(request));
        } else if (isFollowUp(transactionType)) {
            payload.setModify(modify(request, transactionType));
        } else {
            payload.setSubmit(submit(request, transactionType));
        }
        return payload;
    }

    /**
     * 构造 WPGXML submit 请求对象。
     * <p>
     * submit 用于支付、授权和预授权首笔交易；orderCode 取平台渠道订单号，金额按币种辅币位转换，卡数据只进入当前渠道请求对象。
     * </p>
     *
     * @param request 平台统一渠道请求，包含金额、币种、卡数据、账单地址和扩展字段
     * @param transactionType 已标准化的平台交易类型
     * @return WPGXML submit 节点对象
     */
    private WorldPayXmlRequestPayload.Submit submit(ChannelPaymentRequest request, String transactionType) {
        WorldPayXmlRequestPayload.Submit submit = new WorldPayXmlRequestPayload.Submit();
        WorldPayXmlRequestPayload.Order order = new WorldPayXmlRequestPayload.Order();
        order.setOrderCode(orderCode(request));
        if (ChannelCapability.PAYMENT.getCode().equals(transactionType)) {
            order.setCaptureDelay(firstText(extensionValue(request, "mid.captureDelay"), extensionValue(request, "captureDelay"), "0"));
        }
        order.setDescription(firstText(
                extensionValue(request, "mid.description"),
                extensionValue(request, "description"),
                request.getMerchantOrderNo(),
                request.getTransactionId(),
                "WorldPay XML order"));
        order.setAmount(amount(request, false));
        order.setOrderContent(firstText(extensionValue(request, "orderContent"), request.getMerchantOrderNo()));
        order.setPaymentDetails(paymentDetails(request));
        order.setShopper(shopper(request));
        order.setStatementNarrative(firstText(
                extensionValue(request, "mid.statementNarrative"),
                extensionValue(request, "mid.narrative"),
                extensionValue(request, "statementNarrative")));
        submit.setOrder(order);
        return submit;
    }

    /**
     * 构造 WPGXML modify 请求对象。
     * <p>
     * modify 用于请款、退款、撤销和冲正后续动作，必须通过原 Worldpay orderCode 定位首笔交易。
     * 请款和退款会校验金额和币种；撤销不携带金额。
     * </p>
     *
     * @param request 平台统一渠道请求，包含原交易定位字段和本次动作金额
     * @param transactionType 已标准化的平台交易类型
     * @return WPGXML modify 节点对象
     */
    private WorldPayXmlRequestPayload.Modify modify(ChannelPaymentRequest request, String transactionType) {
        WorldPayXmlRequestPayload.Modify modify = new WorldPayXmlRequestPayload.Modify();
        WorldPayXmlRequestPayload.OrderModification orderModification = new WorldPayXmlRequestPayload.OrderModification();
        orderModification.setOrderCode(sourceOrderCode(request));
        if (ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)) {
            WorldPayXmlRequestPayload.Capture capture = new WorldPayXmlRequestPayload.Capture();
            capture.setDate(WorldPayXmlRequestPayload.DateValue.from(LocalDate.now()));
            capture.setAmount(amount(request, false));
            orderModification.setCapture(capture);
        } else if (ChannelCapability.REFUND.getCode().equals(transactionType)) {
            WorldPayXmlRequestPayload.Refund refund = new WorldPayXmlRequestPayload.Refund();
            refund.setReference(firstText(extensionValue(request, "refundReference"), request.getChannelTransactionId()));
            refund.setAmount(amount(request, true));
            orderModification.setRefund(refund);
        } else if (ChannelCapability.VOID.getCode().equals(transactionType)
                || ChannelCapability.REVERSAL.getCode().equals(transactionType)) {
            orderModification.setCancel(new WorldPayXmlRequestPayload.Cancel());
        } else {
            throw new ChannelRequestException("WorldPay XML unsupported follow-up transaction type: " + transactionType);
        }
        modify.setOrderModification(orderModification);
        return modify;
    }

    /**
     * 构造 WPGXML inquiry 请求对象。
     * <p>
     * 查询只需要原 Worldpay orderCode，不携带金额、卡号、CVC 或 3DS 认证值。
     * </p>
     *
     * @param request 平台统一渠道请求，包含原交易定位字段
     * @return WPGXML inquiry 节点对象
     */
    private WorldPayXmlRequestPayload.Inquiry inquiry(ChannelPaymentRequest request) {
        WorldPayXmlRequestPayload.Inquiry inquiry = new WorldPayXmlRequestPayload.Inquiry();
        WorldPayXmlRequestPayload.OrderInquiry orderInquiry = new WorldPayXmlRequestPayload.OrderInquiry();
        orderInquiry.setOrderCode(sourceOrderCode(request));
        inquiry.setOrderInquiry(orderInquiry);
        return inquiry;
    }

    /**
     * 构造 WPGXML paymentDetails 节点。
     * <p>
     * 当前封装 CARD-SSL、session 和 3DS 信息；支付工具字段来自商户请求解密后的内存对象。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @return paymentDetails 节点对象
     */
    private WorldPayXmlRequestPayload.PaymentDetails paymentDetails(ChannelPaymentRequest request) {
        WorldPayXmlRequestPayload.PaymentDetails paymentDetails = new WorldPayXmlRequestPayload.PaymentDetails();
        paymentDetails.setCardSsl(cardSsl(request));
        paymentDetails.setSession(session(request));
        paymentDetails.setInfo3DSecure(info3DSecure(request));
        return paymentDetails;
    }

    private WorldPayXmlRequestPayload.CardSsl cardSsl(ChannelPaymentRequest request) {
        requiredText(request.getCardNo(), "WorldPay XML card number is required");
        requiredText(request.getExpirationMonth(), "WorldPay XML card expiry month is required");
        requiredText(request.getExpirationYear(), "WorldPay XML card expiry year is required");
        requiredText(request.getSecurityCode(), "WorldPay XML card security code is required");
        WorldPayXmlRequestPayload.CardSsl cardSsl = new WorldPayXmlRequestPayload.CardSsl();
        cardSsl.setCardNumber(request.getCardNo());
        cardSsl.setExpiryDate(expiryDate(request));
        cardSsl.setCardHolderName(firstText(extensionValue(request, "cardHolderName"), billingFullName(request)));
        cardSsl.setCvc(request.getSecurityCode());
        cardSsl.setCardAddress(cardAddress(request));
        return cardSsl;
    }

    /**
     * 构造银行卡有效期节点。
     *
     * @param request 平台统一渠道请求，包含月份和年份
     * @return expiryDate 节点对象，月份为两位，年份为四位
     */
    private WorldPayXmlRequestPayload.ExpiryDate expiryDate(ChannelPaymentRequest request) {
        WorldPayXmlRequestPayload.ExpiryDate expiryDate = new WorldPayXmlRequestPayload.ExpiryDate();
        expiryDate.setMonth(normalizeMonth(request.getExpirationMonth()));
        expiryDate.setYear(normalizeYear(request.getExpirationYear()));
        return expiryDate;
    }

    /**
     * 构造持卡人账单地址节点。
     * <p>
     * 地址字段来自商户交易请求的 billingInfo；所有字段均允许为空，完全为空时不生成 cardAddress。
     * 国家地区会归一为 ISO 3166-1 alpha-2 以满足 WPGXML 常见输入要求。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @return cardAddress 节点对象；账单地址为空时返回 null
     */
    private WorldPayXmlRequestPayload.CardAddress cardAddress(ChannelPaymentRequest request) {
        ChannelPaymentRequest.BillingInfo billing = request.getBillingInfo();
        if (billing == null) {
            return null;
        }
        boolean hasAddress = StringUtils.hasText(billing.getStreet())
                || StringUtils.hasText(billing.getPostal())
                || StringUtils.hasText(billing.getCity())
                || StringUtils.hasText(billing.getCountry());
        if (!hasAddress) {
            return null;
        }
        WorldPayXmlRequestPayload.Address address = new WorldPayXmlRequestPayload.Address();
        address.setAddress1(billing.getStreet());
        address.setPostalCode(billing.getPostal());
        address.setCity(billing.getCity());
        address.setState(billing.getState());
        address.setCountryCode(countryAlpha2(billing.getCountry()));
        WorldPayXmlRequestPayload.CardAddress cardAddress = new WorldPayXmlRequestPayload.CardAddress();
        cardAddress.setAddress(address);
        return cardAddress;
    }

    /**
     * 构造消费者会话节点。
     * <p>
     * sessionId 和 shopperIPAddress 来自渠道扩展字段，用于 Worldpay 风控和交易关联；二者均为空时不输出 session。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @return session 节点对象；无会话字段时返回 null
     */
    private WorldPayXmlRequestPayload.Session session(ChannelPaymentRequest request) {
        String sessionId = extensionValue(request, "sessionId");
        String ip = firstText(extensionValue(request, "shopperIp"), extensionValue(request, "clientIp"));
        if (!StringUtils.hasText(sessionId) && !StringUtils.hasText(ip)) {
            return null;
        }
        WorldPayXmlRequestPayload.Session session = new WorldPayXmlRequestPayload.Session();
        session.setId(sessionId);
        session.setShopperIPAddress(ip);
        return session;
    }

    /**
     * 构造 3DS 认证结果节点。
     * <p>
     * 3DS 信息来自商户侧认证结果或上游认证服务；CAVV 属于敏感认证数据，禁止写入日志或异常消息。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @return info3DSecure 节点对象；没有 3DS 结果时返回 null
     */
    private WorldPayXmlRequestPayload.Info3DSecure info3DSecure(ChannelPaymentRequest request) {
        ChannelPaymentRequest.ThreeDsInfo threeDsInfo = request.getThreeDsInfo();
        if (threeDsInfo == null) {
            return null;
        }
        if (!StringUtils.hasText(threeDsInfo.getEci())
                && !StringUtils.hasText(threeDsInfo.getCavv())
                && !StringUtils.hasText(threeDsInfo.getDsTransactionId())) {
            return null;
        }
        WorldPayXmlRequestPayload.Info3DSecure info3DSecure = new WorldPayXmlRequestPayload.Info3DSecure();
        info3DSecure.setThreeDSVersion(threeDsInfo.getThreeDsVersion());
        info3DSecure.setDsTransactionId(threeDsInfo.getDsTransactionId());
        info3DSecure.setCavv(threeDsInfo.getCavv());
        info3DSecure.setEci(threeDsInfo.getEci());
        return info3DSecure;
    }

    /**
     * 构造 shopper 节点。
     * <p>
     * shopperEmailAddress 来自账单邮箱，authenticatedShopperID 优先取扩展 shopperId，否则取平台商户号；
     * browser 节点由 acceptHeader 和 userAgent 组成，用于渠道侧消费者环境识别。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @return shopper 节点对象；所有 shopper 字段为空时返回 null
     */
    private WorldPayXmlRequestPayload.Shopper shopper(ChannelPaymentRequest request) {
        String email = request.getBillingInfo() == null ? null : request.getBillingInfo().getEmail();
        String shopperId = firstText(extensionValue(request, "shopperId"), request.getMerchantId());
        String userAgent = extensionValue(request, "userAgent");
        String acceptHeader = firstText(extensionValue(request, "acceptHeader"), StringUtils.hasText(userAgent) ? "text/html" : null);
        if (!StringUtils.hasText(email) && !StringUtils.hasText(shopperId)
                && !StringUtils.hasText(acceptHeader) && !StringUtils.hasText(userAgent)) {
            return null;
        }
        WorldPayXmlRequestPayload.Shopper shopper = new WorldPayXmlRequestPayload.Shopper();
        shopper.setShopperEmailAddress(email);
        shopper.setAuthenticatedShopperID(shopperId);
        if (StringUtils.hasText(acceptHeader) || StringUtils.hasText(userAgent)) {
            WorldPayXmlRequestPayload.Browser browser = new WorldPayXmlRequestPayload.Browser();
            browser.setAcceptHeader(acceptHeader);
            browser.setUserAgentHeader(userAgent);
            shopper.setBrowser(browser);
        }
        return shopper;
    }

    /**
     * 构造 WPGXML amount 节点。
     * <p>
     * 主币种金额会依据 currencyExponent 或币种表默认辅币位转换为最小辅币单位；退款金额使用 credit 方向。
     * </p>
     *
     * @param request 平台统一渠道请求，包含金额和币种
     * @param credit true 表示退款方向，输出 debitCreditIndicator=credit
     * @return amount 节点对象
     */
    private WorldPayXmlRequestPayload.Amount amount(ChannelPaymentRequest request, boolean credit) {
        String currency = currency(request.getCurrency());
        int exponent = currencyExponent(request.getExtension(), currency);
        WorldPayXmlRequestPayload.Amount amount = new WorldPayXmlRequestPayload.Amount();
        amount.setValue(minorAmount(request.getAmount(), exponent));
        amount.setCurrencyCode(currency);
        amount.setExponent(exponent);
        if (credit) {
            amount.setDebitCreditIndicator("credit");
        }
        return amount;
    }

    /**
     * 解析首笔交易 orderCode。
     *
     * @param request 平台统一渠道请求
     * @return Worldpay orderCode，优先使用渠道订单号，其次使用平台交易号
     */
    private String orderCode(ChannelPaymentRequest request) {
        return requiredText(firstText(request.getChannelOrderNo(), request.getTransactionId()),
                "WorldPay XML orderCode is required");
    }

    /**
     * 解析后续动作或查询使用的原 Worldpay orderCode。
     * <p>
     * 优先读取显式扩展 sourceOrderCode/worldpayOrderCode，再兼容渠道订单号、原平台交易号和当前交易号。
     * 缺失时抛出请求异常，避免把后续交易发到不可定位的原单。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @return 原 Worldpay orderCode
     */
    private String sourceOrderCode(ChannelPaymentRequest request) {
        return requiredText(firstText(
                extensionValue(request, "sourceOrderCode"),
                extensionValue(request, "worldpayOrderCode"),
                request.getChannelOrderNo(),
                request.getSourceTransactionId(),
                request.getTransactionId()), "WorldPay XML source orderCode is required");
    }

    /**
     * 判断是否为 WPGXML 后续交易。
     *
     * @param transactionType 已标准化的平台交易类型
     * @return true 表示请款、预授权完成、退款、撤销或冲正
     */
    private boolean isFollowUp(String transactionType) {
        return ChannelCapability.CAPTURE.getCode().equals(transactionType)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(transactionType)
                || ChannelCapability.REFUND.getCode().equals(transactionType)
                || ChannelCapability.VOID.getCode().equals(transactionType)
                || ChannelCapability.REVERSAL.getCode().equals(transactionType);
    }

    /**
     * 校验 WPGXML 公共请求字段。
     * <p>
     * 所有请求必须包含交易类型和可生成 orderCode 的标识；首笔交易、金额和卡数据由后续节点构造方法继续校验。
     * </p>
     *
     * @param request 平台统一渠道请求
     */
    private void validateCommonRequest(ChannelPaymentRequest request) {
        if (request == null) {
            throw new ChannelRequestException("WorldPay XML request is required");
        }
        requiredText(request.getTransactionType(), "WorldPay XML transactionType is required");
        requiredText(firstText(request.getChannelOrderNo(), request.getTransactionId()),
                "WorldPay XML channelOrderNo is required");
    }

    /**
     * 将主币种金额转换为最小辅币单位。
     * <p>
     * 金额必须大于 0，小数位不得超过币种辅币位；转换失败时抛出请求异常，调用方可以据此定位商户金额精度问题。
     * </p>
     *
     * @param amount 主币种金额
     * @param exponent 币种辅币位
     * @return 最小辅币单位整数
     */
    private long minorAmount(BigDecimal amount, int exponent) {
        if (amount == null) {
            throw new ChannelRequestException("WorldPay XML transaction amount is required");
        }
        if (amount.signum() <= 0) {
            throw new ChannelRequestException("WorldPay XML transaction amount must be greater than 0");
        }
        try {
            return amount
                    .movePointRight(exponent)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw new ChannelRequestException("WorldPay XML amount fraction digits exceed currency minor unit", exception);
        }
    }

    /**
     * 解析币种辅币位。
     * <p>
     * 优先使用 service-payment 透传的 currencyExponent 或 MID 配置；未传入时读取 ISO 币种解析器。
     * 有效范围限定为 0 到 9，避免错误配置造成金额放大或缩小。
     * </p>
     *
     * @param extension 渠道扩展字段
     * @param currency ISO 4217 三位币种代码
     * @return 币种辅币位
     */
    private int currencyExponent(Map<String, String> extension, String currency) {
        String value = extension == null ? null : firstText(
                extension.get("currencyExponent"),
                extension.get("mid.currencyExponent"));
        if (StringUtils.hasText(value)) {
            try {
                int exponent = Integer.parseInt(value);
                if (exponent < 0 || exponent > 9) {
                    throw new ChannelRequestException("WorldPay XML currencyExponent is out of range");
                }
                return exponent;
            } catch (NumberFormatException exception) {
                throw new ChannelRequestException("WorldPay XML currencyExponent is invalid", exception);
            }
        }
        IsoCurrencyInfo currencyInfo = IsoCurrencyResolver.resolve(currency)
                .orElseThrow(() -> new ChannelRequestException("WorldPay XML transaction currency can not be resolved"));
        return currencyInfo.defaultFractionDigits();
    }

    /**
     * 拼接持卡人姓名。
     * <p>
     * 来源为账单 firstName/lastName，只用于渠道请求 cardHolderName；日志输出必须脱敏。
     * </p>
     *
     * @param request 平台统一渠道请求
     * @return 持卡人姓名；账单姓名为空时返回 null
     */
    private String billingFullName(ChannelPaymentRequest request) {
        ChannelPaymentRequest.BillingInfo billing = request.getBillingInfo();
        if (billing == null) {
            return null;
        }
        return firstText((firstText(billing.getFirstName(), "") + " " + firstText(billing.getLastName(), "")).trim(), null);
    }

    /**
     * 校验并标准化交易币种。
     *
     * @param currency 平台交易币种
     * @return 大写 ISO 4217 三位币种代码
     */
    private String currency(String currency) {
        return requiredText(currency, "WorldPay XML transaction currency is required").toUpperCase(Locale.ROOT);
    }

    /**
     * 将商户侧国家地区输入归一为 WPG XML 常用的 ISO 3166-1 alpha-2。
     *
     * @param country 国家地区代码或名称
     * @return alpha-2 国家地区代码；无法识别时保留原始大写值
     */
    private String countryAlpha2(String country) {
        if (!StringUtils.hasText(country)) {
            return null;
        }
        return IsoCountryResolver.resolve(country)
                .map(info -> info.alpha2().toUpperCase(Locale.ROOT))
                .orElse(country.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * 标准化银行卡有效期月份。
     *
     * @param month 商户请求中的有效期月份
     * @return 两位月份文本
     */
    private String normalizeMonth(String month) {
        String normalized = requiredText(month, "WorldPay XML card expiry month is required");
        return normalized.length() == 1 ? "0" + normalized : normalized;
    }

    /**
     * 标准化银行卡有效期年份。
     *
     * @param year 商户请求中的有效期年份
     * @return 四位年份文本
     */
    private String normalizeYear(String year) {
        String normalized = requiredText(year, "WorldPay XML card expiry year is required");
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
        return requiredText(transactionType, "WorldPay XML transactionType is required").toUpperCase(Locale.ROOT);
    }

    /**
     * 读取渠道扩展字段。
     *
     * @param request 平台统一渠道请求
     * @param key 扩展字段名
     * @return 扩展字段值；请求或扩展为空时返回 null
     */
    private String extensionValue(ChannelPaymentRequest request, String key) {
        return request == null || request.getExtension() == null ? null : request.getExtension().get(key);
    }

    /**
     * 校验并返回必填文本。
     *
     * @param value 原始文本
     * @param message 缺失时抛出的错误消息
     * @return 去除首尾空白后的文本
     */
    private String requiredText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ChannelRequestException(message);
        }
        return value.trim();
    }

    /**
     * 返回首个非空文本。
     *
     * @param values 候选文本
     * @return 首个非空文本；全部为空时返回 null
     */
    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
