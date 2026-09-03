package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.exception.ChannelRequestException;
import com.scott.payment.channel.payment.exception.ChannelResponseException;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayXmlCodec
 * @date : 2026-07-26 00:00
 * @email : scott_x@163.com
 * @description : WorldPay XML 编解码器，位于 payment-channel-worldpay 渠道协议层，负责把 WPGXML 请求对象安全序列化为 XML，并把渠道 XML 响应安全解析为响应对象；不读取 MID 凭据、不执行 HTTP 调用、不决定平台交易终态。
 * @status : create
 */
public class WorldPayXmlCodec {

    /**
     * WPG XML 根节点固定名称。
     */
    private static final String ROOT_PAYMENT_SERVICE = "paymentService";

    /**
     * Worldpay XML Public DTD 标识。
     */
    private static final String DOCTYPE_PUBLIC = "-//WorldPay//DTD WorldPay PaymentService v1//EN";

    /**
     * Worldpay XML DTD 地址，写入 DOCTYPE 供渠道识别，不在本地解析外部实体。
     */
    private static final String DOCTYPE_SYSTEM = "http://dtd.worldpay.com/paymentService_v1.dtd";

    /**
     * 响应解析前剥离 DTD 声明，兼容 Worldpay 官方 DOCTYPE，同时不加载外部实体。
     */
    private static final Pattern DOCTYPE_PATTERN = Pattern.compile("<!DOCTYPE[^>]*>", Pattern.CASE_INSENSITIVE);

    /**
     * 将 Worldpay 请求对象序列化为符合 WPGXML 协议的 XML 文本。
     * @param payload 已完成业务校验的 Worldpay XML 请求模型
     * @return 包含受控 DOCTYPE 的 UTF-8 XML 请求文本
     */
    public String writeRequest(WorldPayXmlRequestPayload payload) {
        if (payload == null) {
            throw new ChannelRequestException("WorldPay XML payload is required");
        }
        try {
            Document document = newDocument();
            Element root = document.createElement(ROOT_PAYMENT_SERVICE);
            attr(root, "version", payload.getVersion());
            attr(root, "merchantCode", payload.getMerchantCode());
            document.appendChild(root);
            appendSubmit(document, root, payload.getSubmit());
            appendModify(document, root, payload.getModify());
            appendInquiry(document, root, payload.getInquiry());
            return transform(document);
        } catch (ChannelRequestException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ChannelRequestException("WorldPay XML request serialize failed", exception);
        }
    }

    /**
     * 将 WPGXML 响应原文解析为响应对象。
     *
     * @param responseXml WPGXML 响应原文
     * @return WPGXML 响应对象
     */
    public WorldPayXmlResponsePayload readResponse(String responseXml) {
        if (!StringUtils.hasText(responseXml)) {
            throw new ChannelResponseException("WorldPay XML response body is empty");
        }
        try {
            Document document = parseDocument(responseXml);
            Element root = document.getDocumentElement();
            WorldPayXmlResponsePayload payload = new WorldPayXmlResponsePayload();
            payload.setMerchantCode(attr(root, "merchantCode"));
            payload.setVersion(attr(root, "version"));
            Element orderStatus = first(root, "orderStatus");
            if (orderStatus != null) {
                payload.setOrderStatus(orderStatus(orderStatus));
            }
            Element ok = first(root, "ok");
            if (ok != null) {
                payload.setOk(ok(ok));
            }
            Element error = first(root, "error");
            if (error != null) {
                payload.setError(error(error));
            }
            return payload;
        } catch (ChannelResponseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ChannelResponseException("WorldPay XML response parse failed", exception);
        }
    }

    private Document newDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        harden(factory);
        return factory.newDocumentBuilder().newDocument();
    }

    /**
     * 解析 Worldpay XML 响应。
     * <p>
     * 响应可能携带 Worldpay 官方 DOCTYPE，本方法先剥离 DOCTYPE 再用禁用外部实体的解析器读取，避免 XXE。
     * </p>
     *
     * @param xml Worldpay XML 响应原文
     * @return DOM 文档
     * @throws Exception XML 解析失败时抛出，由上层转换为渠道响应异常
     */
    private Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        harden(factory);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(stripDoctype(xml))));
    }

    /**
     * 加固 XML DocumentBuilderFactory。
     * <p>
     * 禁用 DOCTYPE、外部通用实体、外部参数实体、XInclude 和实体展开，确保渠道响应 XML 不会读取本地文件或外部网络资源。
     * </p>
     *
     * @param factory 待加固的 XML 工厂
     * @throws Exception 当前 JAXP 实现不支持安全特性时抛出，由调用方统一转换异常
     */
    private void harden(DocumentBuilderFactory factory) throws Exception {
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
    }

    /**
     * 将 DOM 文档输出为 WPGXML 请求原文。
     * <p>
     * Transformer 负责 XML 声明、DTD 声明、字符转义和节点输出，避免手写字符串拼接遗漏转义。
     * </p>
     *
     * @param document WPGXML 请求 DOM
     * @return 可发送给 Worldpay XML Direct 的 XML 原文
     * @throws Exception XML 输出失败时抛出，由上层转换为渠道请求异常
     */
    private String transform(Document document) throws Exception {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, DOCTYPE_PUBLIC);
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, DOCTYPE_SYSTEM);
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * 剥离 XML DOCTYPE，避免合法 Worldpay DTD 声明触发禁用 DOCTYPE 解析，同时阻断外部实体。
     *
     * @param xml XML 原文
     * @return 去除 DOCTYPE 后的 XML
     */
    private String stripDoctype(String xml) {
        return xml == null ? null : DOCTYPE_PATTERN.matcher(xml).replaceFirst("");
    }

    /**
     * 追加 submit 节点。
     * <p>
     * submit 用于支付、授权和预授权首笔交易；节点缺失时直接跳过，避免生成空 submit。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param root paymentService 根节点
     * @param submit 首笔交易请求对象
     */
    private void appendSubmit(Document document, Element root, WorldPayXmlRequestPayload.Submit submit) {
        if (submit == null) {
            return;
        }
        Element submitElement = child(document, root, "submit");
        appendOrder(document, submitElement, submit.getOrder());
    }

    /**
     * 追加 order 节点。
     * <p>
     * order 承载 Worldpay orderCode、金额、卡支付明细、消费者信息和账单叙述；PAN/CVC 仅通过后续 paymentDetails 节点进入当前请求。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param submit submit 父节点
     * @param order 渠道订单请求对象
     */
    private void appendOrder(Document document, Element submit, WorldPayXmlRequestPayload.Order order) {
        if (order == null) {
            return;
        }
        Element orderElement = child(document, submit, "order");
        attr(orderElement, "orderCode", order.getOrderCode());
        attr(orderElement, "captureDelay", order.getCaptureDelay());
        textChild(document, orderElement, "description", order.getDescription());
        appendAmount(document, orderElement, order.getAmount());
        cdataChild(document, orderElement, "orderContent", order.getOrderContent());
        appendPaymentDetails(document, orderElement, order.getPaymentDetails());
        appendShopper(document, orderElement, order.getShopper());
        textChild(document, orderElement, "statementNarrative", order.getStatementNarrative());
    }

    /**
     * 追加 paymentDetails 节点。
     * <p>
     * 当前支持 CARD-SSL、session 和 info3DSecure；不在本层保存卡数据或认证值。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param orderElement order 父节点
     * @param paymentDetails 支付明细对象
     */
    private void appendPaymentDetails(Document document,
                                      Element orderElement,
                                      WorldPayXmlRequestPayload.PaymentDetails paymentDetails) {
        if (paymentDetails == null) {
            return;
        }
        Element paymentDetailsElement = child(document, orderElement, "paymentDetails");
        appendCardSsl(document, paymentDetailsElement, paymentDetails.getCardSsl());
        appendSession(document, paymentDetailsElement, paymentDetails.getSession());
        appendThreeDS(document, paymentDetailsElement, paymentDetails.getInfo3DSecure());
    }

    /**
     * 追加 CARD-SSL 明文卡节点。
     * <p>
     * cardNumber 和 cvc 属于高敏感认证数据，只允许进入当前渠道请求 XML；日志和审计字段必须使用外层脱敏方法。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param paymentDetails paymentDetails 父节点
     * @param cardSsl 明文卡支付对象
     */
    private void appendCardSsl(Document document, Element paymentDetails, WorldPayXmlRequestPayload.CardSsl cardSsl) {
        if (cardSsl == null) {
            return;
        }
        Element cardElement = child(document, paymentDetails, "CARD-SSL");
        textChild(document, cardElement, "cardNumber", cardSsl.getCardNumber());
        appendExpiryDate(document, cardElement, cardSsl.getExpiryDate());
        textChild(document, cardElement, "cardHolderName", cardSsl.getCardHolderName());
        textChild(document, cardElement, "cvc", cardSsl.getCvc());
        appendCardAddress(document, cardElement, cardSsl.getCardAddress());
    }

    /**
     * 追加银行卡有效期节点。
     *
     * @param document WPGXML DOM 文档
     * @param cardElement CARD-SSL 父节点
     * @param expiryDate 卡有效期，month 为两位，year 为四位
     */
    private void appendExpiryDate(Document document, Element cardElement, WorldPayXmlRequestPayload.ExpiryDate expiryDate) {
        if (expiryDate == null) {
            return;
        }
        Element expiryDateElement = child(document, cardElement, "expiryDate");
        Element dateElement = child(document, expiryDateElement, "date");
        attr(dateElement, "month", expiryDate.getMonth());
        attr(dateElement, "year", expiryDate.getYear());
    }

    /**
     * 追加持卡人账单地址节点。
     * <p>
     * 地址属于个人信息，XML 原文只用于渠道请求，日志和落库审计必须脱敏。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param cardElement CARD-SSL 父节点
     * @param cardAddress 持卡人账单地址对象
     */
    private void appendCardAddress(Document document, Element cardElement, WorldPayXmlRequestPayload.CardAddress cardAddress) {
        if (cardAddress == null || cardAddress.getAddress() == null) {
            return;
        }
        Element cardAddressElement = child(document, cardElement, "cardAddress");
        Element addressElement = child(document, cardAddressElement, "address");
        WorldPayXmlRequestPayload.Address address = cardAddress.getAddress();
        textChild(document, addressElement, "address1", address.getAddress1());
        textChild(document, addressElement, "postalCode", address.getPostalCode());
        textChild(document, addressElement, "city", address.getCity());
        textChild(document, addressElement, "state", address.getState());
        textChild(document, addressElement, "countryCode", address.getCountryCode());
    }

    /**
     * 追加 shopper session 节点。
     * <p>
     * shopperIPAddress 可用于渠道风控和争议排查，不作为平台内部风控 IP 库完整记录输出。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param paymentDetails paymentDetails 父节点
     * @param session 消费者会话对象
     */
    private void appendSession(Document document, Element paymentDetails, WorldPayXmlRequestPayload.Session session) {
        if (session == null) {
            return;
        }
        Element sessionElement = child(document, paymentDetails, "session");
        attr(sessionElement, "shopperIPAddress", session.getShopperIPAddress());
        attr(sessionElement, "id", session.getId());
    }

    /**
     * 追加 3DS 认证结果节点。
     * <p>
     * CAVV 属于敏感认证数据，只允许在渠道请求内短暂使用，禁止明文日志输出。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param paymentDetails paymentDetails 父节点
     * @param info3DSecure 3DS 认证结果对象
     */
    private void appendThreeDS(Document document, Element paymentDetails, WorldPayXmlRequestPayload.Info3DSecure info3DSecure) {
        if (info3DSecure == null) {
            return;
        }
        Element threeDSElement = child(document, paymentDetails, "info3DSecure");
        textChild(document, threeDSElement, "threeDSVersion", info3DSecure.getThreeDSVersion());
        textChild(document, threeDSElement, "dsTransactionId", info3DSecure.getDsTransactionId());
        textChild(document, threeDSElement, "cavv", info3DSecure.getCavv());
        textChild(document, threeDSElement, "eci", info3DSecure.getEci());
    }

    /**
     * 追加 shopper 节点。
     * <p>
     * shopperEmailAddress 属于个人信息，authenticatedShopperID 来源于商户或平台标识；两者仅用于渠道关联和风控。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param orderElement order 父节点
     * @param shopper 消费者摘要对象
     */
    private void appendShopper(Document document, Element orderElement, WorldPayXmlRequestPayload.Shopper shopper) {
        if (shopper == null) {
            return;
        }
        Element shopperElement = child(document, orderElement, "shopper");
        textChild(document, shopperElement, "shopperEmailAddress", shopper.getShopperEmailAddress());
        textChild(document, shopperElement, "authenticatedShopperID", shopper.getAuthenticatedShopperID());
        appendBrowser(document, shopperElement, shopper.getBrowser());
    }

    /**
     * 追加浏览器头节点。
     * <p>
     * acceptHeader 和 userAgentHeader 用于渠道识别消费者环境，日志中只输出摘要或脱敏 XML。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param shopperElement shopper 父节点
     * @param browser 浏览器头对象
     */
    private void appendBrowser(Document document, Element shopperElement, WorldPayXmlRequestPayload.Browser browser) {
        if (browser == null) {
            return;
        }
        Element browserElement = child(document, shopperElement, "browser");
        textChild(document, browserElement, "acceptHeader", browser.getAcceptHeader());
        textChild(document, browserElement, "userAgentHeader", browser.getUserAgentHeader());
    }

    /**
     * 追加 modify 节点。
     * <p>
     * modify 用于请款、退款、撤销和冲正等后续动作，必须通过 orderModification 定位原 Worldpay orderCode。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param root paymentService 根节点
     * @param modify 后续动作请求对象
     */
    private void appendModify(Document document, Element root, WorldPayXmlRequestPayload.Modify modify) {
        if (modify == null) {
            return;
        }
        Element modifyElement = child(document, root, "modify");
        appendOrderModification(document, modifyElement, modify.getOrderModification());
    }

    /**
     * 追加 orderModification 节点。
     * <p>
     * 同一个 orderModification 只表达一种后续动作，避免 capture、refund、cancel 同时出现导致渠道语义不清。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param modify modify 父节点
     * @param orderModification 订单修改对象
     */
    private void appendOrderModification(Document document,
                                         Element modify,
                                         WorldPayXmlRequestPayload.OrderModification orderModification) {
        if (orderModification == null) {
            return;
        }
        Element orderModificationElement = child(document, modify, "orderModification");
        attr(orderModificationElement, "orderCode", orderModification.getOrderCode());
        appendCapture(document, orderModificationElement, orderModification.getCapture());
        appendRefund(document, orderModificationElement, orderModification.getRefund());
        appendCancel(document, orderModificationElement, orderModification.getCancel());
    }

    /**
     * 追加请款节点。
     * <p>
     * capture 必须包含金额和日期；金额使用最小辅币单位并与 currencyCode、exponent 同时输出。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param orderModification orderModification 父节点
     * @param capture 请款对象
     */
    private void appendCapture(Document document, Element orderModification, WorldPayXmlRequestPayload.Capture capture) {
        if (capture == null) {
            return;
        }
        Element captureElement = child(document, orderModification, "capture");
        appendDate(document, captureElement, capture.getDate());
        appendAmount(document, captureElement, capture.getAmount());
    }

    /**
     * 追加退款节点。
     * <p>
     * refund.reference 用于渠道侧退款识别，金额方向由 debitCreditIndicator=credit 表达。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param orderModification orderModification 父节点
     * @param refund 退款对象
     */
    private void appendRefund(Document document, Element orderModification, WorldPayXmlRequestPayload.Refund refund) {
        if (refund == null) {
            return;
        }
        Element refundElement = child(document, orderModification, "refund");
        attr(refundElement, "reference", refund.getReference());
        appendAmount(document, refundElement, refund.getAmount());
    }

    /**
     * 追加撤销节点。
     * <p>
     * cancel 节点不携带金额，表示撤销或冲正原 Worldpay orderCode。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param orderModification orderModification 父节点
     * @param cancel 撤销对象
     */
    private void appendCancel(Document document, Element orderModification, WorldPayXmlRequestPayload.Cancel cancel) {
        if (cancel != null) {
            child(document, orderModification, "cancel");
        }
    }

    /**
     * 追加订单查询节点。
     * <p>
     * inquiry 只携带原 Worldpay orderCode，不包含卡数据、金额或认证值。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param root paymentService 根节点
     * @param inquiry 查询对象
     */
    private void appendInquiry(Document document, Element root, WorldPayXmlRequestPayload.Inquiry inquiry) {
        if (inquiry == null) {
            return;
        }
        Element inquiryElement = child(document, root, "inquiry");
        Element orderInquiryElement = child(document, inquiryElement, "orderInquiry");
        attr(orderInquiryElement, "orderCode", inquiry.getOrderInquiry() == null ? null : inquiry.getOrderInquiry().getOrderCode());
    }

    /**
     * 追加 amount 节点。
     * <p>
     * value 为最小辅币单位，必须和 currencyCode、exponent 一起输出；退款时 debitCreditIndicator 使用 credit。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param parent 金额父节点
     * @param amount 金额对象
     */
    private void appendAmount(Document document, Element parent, WorldPayXmlRequestPayload.Amount amount) {
        if (amount == null) {
            return;
        }
        Element amountElement = child(document, parent, "amount");
        attr(amountElement, "value", String.valueOf(amount.getValue()));
        attr(amountElement, "currencyCode", amount.getCurrencyCode());
        attr(amountElement, "exponent", String.valueOf(amount.getExponent()));
        attr(amountElement, "debitCreditIndicator", amount.getDebitCreditIndicator());
    }

    /**
     * 追加 WPGXML date 节点。
     *
     * @param document WPGXML DOM 文档
     * @param parent 日期父节点
     * @param date 请款业务日期对象
     */
    private void appendDate(Document document, Element parent, WorldPayXmlRequestPayload.DateValue date) {
        if (date == null) {
            return;
        }
        Element dateElement = child(document, parent, "date");
        attr(dateElement, "dayOfMonth", String.valueOf(date.getDayOfMonth()));
        attr(dateElement, "month", String.valueOf(date.getMonth()));
        attr(dateElement, "year", String.valueOf(date.getYear()));
    }

    /**
     * 解析 orderStatus 响应节点。
     * <p>
     * orderStatus 汇总订单号、payment、journal 和 amount，后续 mapper 依据这些字段生成统一渠道响应。
     * </p>
     *
     * @param element orderStatus XML 节点
     * @return WPGXML orderStatus 响应对象
     */
    private WorldPayXmlResponsePayload.OrderStatus orderStatus(Element element) {
        WorldPayXmlResponsePayload.OrderStatus orderStatus = new WorldPayXmlResponsePayload.OrderStatus();
        orderStatus.setOrderCode(attr(element, "orderCode"));
        Element payment = first(element, "payment");
        if (payment != null) {
            orderStatus.setPayment(payment(payment));
        }
        Element journal = first(element, "journal");
        if (journal != null) {
            orderStatus.setJournal(journal(journal));
        }
        Element amount = first(element, "amount");
        if (amount != null) {
            orderStatus.setAmount(amount(amount));
        }
        return orderStatus;
    }

    /**
     * 解析 payment 响应节点。
     * <p>
     * 提取渠道交易号、lastEvent、ISO8583 响应码、授权码、RRN、收单参考和 CVC 校验结果；不在此处推进平台状态。
     * </p>
     *
     * @param element payment XML 节点
     * @return WPGXML payment 响应对象
     */
    private WorldPayXmlResponsePayload.Payment payment(Element element) {
        WorldPayXmlResponsePayload.Payment payment = new WorldPayXmlResponsePayload.Payment();
        payment.setId(attr(element, "id"));
        payment.setLastEvent(normalizeStatus(firstText(attr(element, "lastEvent"), childText(element, "lastEvent"))));
        Element iso8583 = first(element, "ISO8583ReturnCode");
        Element authorisationId = first(element, "AuthorisationId");
        payment.setResponseCode(firstText(attr(element, "responseCode"), attr(iso8583, "code"), childText(element, "ISO8583ReturnCode")));
        payment.setMessage(firstText(attr(element, "message"), attr(iso8583, "description"),
                childText(element, "refusalReason"), childText(element, "refusalReasonCode")));
        payment.setAuthorisationCode(firstText(attr(element, "authorisationCode"), attr(authorisationId, "id"),
                childText(element, "AuthorisationId")));
        payment.setReference(firstText(attr(element, "reference"), childText(element, "reference")));
        payment.setAcquirerReference(firstText(attr(element, "acquirerReference"), childText(element, "acquirerReference")));
        payment.setAcquirerCode(firstText(attr(element, "acquirerCode"), childText(element, "ISO8583ReturnCode")));
        payment.setStan(firstText(attr(element, "stan"), childText(element, "stan")));
        payment.setCvcResultCode(childText(element, "CVCResultCode"));
        return payment;
    }

    /**
     * 解析 journal 响应节点。
     * <p>
     * journal 通常表示请款、退款、撤销等修改类交易的处理事件，字段用于日志、对账和统一响应扩展。
     * </p>
     *
     * @param element journal XML 节点
     * @return WPGXML journal 响应对象
     */
    private WorldPayXmlResponsePayload.Journal journal(Element element) {
        WorldPayXmlResponsePayload.Journal journal = new WorldPayXmlResponsePayload.Journal();
        journal.setId(attr(element, "id"));
        journal.setJournalType(attr(element, "journalType"));
        journal.setType(attr(element, "type"));
        Element iso8583 = first(element, "ISO8583ReturnCode");
        journal.setResponseCode(firstText(attr(element, "responseCode"), attr(iso8583, "code"), childText(element, "ISO8583ReturnCode")));
        journal.setMessage(firstText(attr(element, "message"), attr(iso8583, "description"), childText(element, "message")));
        journal.setReference(firstText(attr(element, "reference"), childText(element, "reference")));
        journal.setAcquirerReference(firstText(attr(element, "acquirerReference"), childText(element, "acquirerReference")));
        journal.setAcquirerCode(firstText(attr(element, "acquirerCode"), childText(element, "ISO8583ReturnCode")));
        return journal;
    }

    /**
     * 解析 amount 响应节点。
     * <p>
     * value 为最小辅币单位文本，currencyCode 和 exponent 用于还原主币种金额或排查金额精度问题。
     * </p>
     *
     * @param element amount XML 节点
     * @return WPGXML amount 响应对象
     */
    private WorldPayXmlResponsePayload.Amount amount(Element element) {
        WorldPayXmlResponsePayload.Amount amount = new WorldPayXmlResponsePayload.Amount();
        amount.setValue(attr(element, "value"));
        amount.setCurrencyCode(attr(element, "currencyCode"));
        amount.setExponent(attr(element, "exponent"));
        amount.setDebitCreditIndicator(attr(element, "debitCreditIndicator"));
        return amount;
    }

    /**
     * 解析 ok 响应节点。
     * <p>
     * ok 表示修改类请求已被 Worldpay 接收，具体状态由内部子节点归一化为平台可识别文本。
     * </p>
     *
     * @param element ok XML 节点
     * @return WPGXML ok 响应对象
     */
    private WorldPayXmlResponsePayload.Ok ok(Element element) {
        WorldPayXmlResponsePayload.Ok ok = new WorldPayXmlResponsePayload.Ok();
        ok.setStatus(okStatus(element));
        return ok;
    }

    /**
     * 解析 error 响应节点。
     * <p>
     * error.code 和文本内容用于渠道失败映射；不得拼接原请求卡号、CVC 或 Basic Auth 信息。
     * </p>
     *
     * @param element error XML 节点
     * @return WPGXML error 响应对象
     */
    private WorldPayXmlResponsePayload.Error error(Element element) {
        WorldPayXmlResponsePayload.Error error = new WorldPayXmlResponsePayload.Error();
        error.setCode(attr(element, "code"));
        error.setMessage(element.getTextContent());
        return error;
    }

    /**
     * 归一化 ok 子节点状态。
     *
     * @param ok ok XML 节点
     * @return CAPTURE_REQUESTED、REFUND_REQUESTED、CANCEL_REQUESTED 或 PROCESSING
     */
    private String okStatus(Element ok) {
        if (first(ok, "captureReceived") != null) {
            return "CAPTURE_REQUESTED";
        }
        if (first(ok, "refundReceived") != null) {
            return "REFUND_REQUESTED";
        }
        if (first(ok, "cancelReceived") != null || first(ok, "voidReceived") != null) {
            return "CANCEL_REQUESTED";
        }
        return "PROCESSING";
    }

    /**
     * 创建并挂载子节点。
     *
     * @param document WPGXML DOM 文档
     * @param parent 父节点
     * @param name 子节点名称
     * @return 已挂载的子节点
     */
    private Element child(Document document, Element parent, String name) {
        Element element = document.createElement(name);
        parent.appendChild(element);
        return element;
    }

    /**
     * 追加普通文本子节点。
     * <p>
     * DOM setTextContent 会处理 XML 转义，避免商户订单描述、地址或浏览器头破坏 XML 结构。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param parent 父节点
     * @param name 子节点名称
     * @param value 子节点文本
     */
    private void textChild(Document document, Element parent, String name, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        Element element = child(document, parent, name);
        element.setTextContent(value);
    }

    /**
     * 追加 CDATA 子节点。
     * <p>
     * orderContent 可能包含 HTML 或特殊字符，使用 CDATA 保存；内部的 CDATA 结束符会被拆分，防止生成非法 XML。
     * </p>
     *
     * @param document WPGXML DOM 文档
     * @param parent 父节点
     * @param name 子节点名称
     * @param value CDATA 文本
     */
    private void cdataChild(Document document, Element parent, String name, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        Element element = child(document, parent, name);
        element.appendChild(document.createCDATASection(value.replace("]]>", "]]]]><![CDATA[>")));
    }

    /**
     * 写入非空 XML 属性。
     *
     * @param element XML 节点
     * @param name 属性名称
     * @param value 属性值
     */
    private void attr(Element element, String name, String value) {
        if (element != null && StringUtils.hasText(value)) {
            element.setAttribute(name, value.trim());
        }
    }

    /**
     * 查找第一个指定名称的子孙节点。
     *
     * @param root 查询起点节点
     * @param tagName XML 节点名
     * @return 第一个匹配节点；未命中时返回 null
     */
    private Element first(Element root, String tagName) {
        if (root == null || !StringUtils.hasText(tagName) || root.getElementsByTagName(tagName).getLength() == 0) {
            return null;
        }
        return (Element) root.getElementsByTagName(tagName).item(0);
    }

    /**
     * 读取第一个指定子孙节点文本。
     *
     * @param root 查询起点节点
     * @param tagName XML 节点名
     * @return 节点文本；未命中时返回 null
     */
    private String childText(Element root, String tagName) {
        Element element = first(root, tagName);
        return element == null ? null : element.getTextContent();
    }

    /**
     * 读取 XML 属性值。
     *
     * @param element XML 节点
     * @param name 属性名称
     * @return 属性值；节点或属性不存在时返回 null
     */
    private String attr(Element element, String name) {
        return element == null || !element.hasAttribute(name) ? null : element.getAttribute(name);
    }

    /**
     * 标准化 Worldpay XML 原始状态。
     *
     * @param status lastEvent、journalType 或 ok 派生状态
     * @return 大写下划线状态文本
     */
    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return status.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
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
