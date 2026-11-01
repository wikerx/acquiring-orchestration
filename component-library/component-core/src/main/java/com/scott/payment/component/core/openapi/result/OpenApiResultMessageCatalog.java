package com.scott.payment.component.core.openapi.result;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenAPI V1.3 分层文案目录。
 *
 * <p>同一结果码按平台人员、商户和付款人三个受众选择不同模板。动态参数只能来自服务端受控字段，
 * 任何缺失、超长、控制字符、HTML 或疑似敏感内容都会触发静态安全文案回退。</p>
 */
public final class OpenApiResultMessageCatalog {

    /**
     * 模板占位符，仅允许 lowerCamelCase ASCII 字段名。
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9]*)}");
    /**
     * 对外参数字段路径白名单，禁止通过字段名注入标记或控制字符。
     */
    private static final Pattern FIELD_PATH = Pattern.compile("[A-Za-z][A-Za-z0-9_.\\[\\]-]{0,127}");
    /**
     * 诊断值中的连续长数字可能是未脱敏 PAN，不允许进入管理端文案。
     */
    private static final Pattern PAN_LIKE_DIGITS = Pattern.compile("(?<!\\d)\\d{12,19}(?!\\d)");
    /**
     * 诊断值中带赋值符号的认证或卡敏感字段必须拒绝。
     */
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)(?:pan|card\\s*number|cardnumber|cvv|cvc|authorization|bearer|api\\s*key|apikey|"
                    + "private\\s*key|secret|token)\\s*[:=]");
    /**
     * {@code MESSAGES}常量，统一 {@code OpenApiResultMessageCatalog} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：集合元素必须沿用所属模型的主键、币种、状态和数据范围口径。
     * </p>
     */
    private static final Map<OpenApiResultCode, MessageSet> MESSAGES = buildMessages();

    private OpenApiResultMessageCatalog() {
    }

    /**
     * 获取不带动态参数的已发布安全文案；参数化模板自动回退到同对象静态文案。
     *
     * @param code 返回码
     * @param audience 可信展示对象
     * @param locale 已发布语言
     * @return 不含未展开占位符的安全文案
     */
    public static String message(OpenApiResultCode code,
                                 OpenApiMessageAudience audience,
                                 OpenApiMessageLocale locale) {
        return message(code, audience, locale, Map.of());
    }

    /**
     * 使用受控参数渲染已发布文案。
     * <p>
     * 缺参、越权参数、超长值、控制字符或 HTML 标记都会触发同对象静态回退，禁止透传异常原文和请求值。
     * </p>
     *
     * @param code 返回码
     * @param audience 可信展示对象
     * @param locale 已发布语言
     * @param parameters 服务端生成的受控参数
     * @return 已渲染文案或同对象静态回退文案
     */
    public static String message(OpenApiResultCode code,
                                 OpenApiMessageAudience audience,
                                 OpenApiMessageLocale locale,
                                 Map<String, String> parameters) {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(locale, "locale");
        MessageSet messageSet = MESSAGES.get(code);
        if (messageSet == null) {
            return code.getMessage();
        }
        String template = template(code, messageSet, audience, locale);
        Matcher matcher = PLACEHOLDER.matcher(template);
        if (!matcher.find()) {
            return template;
        }
        Map<String, String> safeParameters = parameters == null ? Map.of() : parameters;
        matcher.reset();
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = safeParameter(key, safeParameters.get(key), audience);
            if (value == null) {
                return fallback(code, audience, locale, template);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    /**
     * 登记全部公开码的分层文案，并在启动期阻止缺失定义进入运行时。
     *
     * @return 公开码到三类受众文案的不可变索引
     */
    private static Map<OpenApiResultCode, MessageSet> buildMessages() {
        EnumMap<OpenApiResultCode, MessageSet> messages = new EnumMap<>(OpenApiResultCode.class);
        register(messages, OpenApiResultCode.REQUEST_SUCCESS,
                "本次 API 已完成约定接入及业务响应组装", "请求处理成功", "请求已处理，请查看业务结果",
                "Your request has been processed. Check the result.");
        register(messages, OpenApiResultCode.REQUEST_ACCEPTED,
                "本次 API 请求已进入有持久化保障的受理流程", "请求已受理，请按约定查询后续结果", "请求已受理，请稍后查看结果",
                "Your request has been accepted. Check the result later.");
        register(messages, OpenApiResultCode.REQUEST_PARTIALLY_ACCEPTED,
                "按批量契约返回逐项受理结果，不表示全部成功", "部分请求已处理，请检查逐项结果", "部分操作已处理，请查看详情",
                "Some operations were processed. Check the details.");
        register(messages, OpenApiResultCode.PARAM_INVALID,
                "参数 {field} 未通过已登记约束：{internalReason}", "参数 {field} 不合法，请按接口要求修改", "请检查填写的信息后再提交",
                "Invalid request parameter: {field}. Check the API requirements.",
                "Check the information you entered and submit again.");
        register(messages, OpenApiResultCode.PARAM_MISSING,
                "缺少必填字段 {field}", "缺少必填参数 {field}", "请补充必要信息后再提交",
                "Required request parameter is missing: {field}.",
                "Complete the required information and submit again.");
        register(messages, OpenApiResultCode.ENCRYPTED_DATA_INVALID,
                "请求载荷缺失、格式或加密协议校验失败；内部原因 {internalReason}", "请求数据校验未通过，请检查报文及加密协议", "提交信息未能通过验证，请刷新页面后重试",
                "Your information could not be verified. Refresh the page and try again.");
        register(messages, OpenApiResultCode.CALLBACK_URL_NOT_ALLOWED,
                "回调目标未通过既有协议、主机或网络安全策略", "回调地址不符合接口要求，请检查后提交", "提交信息有误，请联系商户处理",
                "The submitted information needs attention. Contact the merchant.");
        register(messages, OpenApiResultCode.BAD_REQUEST,
                "请求报文解析或协议结构校验失败；原因 {internalReason}", "请求格式不正确，请检查接口协议", "暂时无法提交，请刷新页面后重试",
                "Your request could not be submitted. Refresh the page and try again.");
        register(messages, OpenApiResultCode.METHOD_NOT_ALLOWED,
                "目标接口不支持本次 HTTP 请求方法", "请求方法不支持，请按接口文档调用", "暂时无法提交，请刷新页面后重试",
                "Your request could not be submitted. Refresh the page and try again.");
        register(messages, OpenApiResultCode.CONTENT_TYPE_NOT_SUPPORTED,
                "请求内容类型不在接口允许范围", "请求内容类型不支持，请检查 Content-Type", "暂时无法提交，请刷新页面后重试",
                "Your request could not be submitted. Refresh the page and try again.");
        register(messages, OpenApiResultCode.REQUEST_TOO_LARGE,
                "请求内容超过已配置的接口大小上限", "请求内容过大，请调整后提交", "提交内容过大，请调整后重试",
                "The submitted content is too large. Adjust it and try again.");
        register(messages, OpenApiResultCode.AUTHORIZATION_MISSING,
                "未取得协议要求的认证信息", "缺少认证信息，请检查请求头", "验证信息不足，请刷新页面后重试",
                "Verification information is missing. Refresh the page and try again.");
        register(messages, OpenApiResultCode.AUTHENTICATION_FAILED,
                "认证校验未通过；内部原因 {internalReason}", "认证未通过，请检查访问凭证和安全协议", "验证未通过，请刷新页面后重试",
                "Verification could not be completed. Refresh the page and try again.");
        register(messages, OpenApiResultCode.AUTHORIZATION_TIME_INVALID,
                "认证 exp/iat 等时间条件无效；内部原因 {internalReason}", "认证信息已过期或时间设置不正确，请校准时间后重新认证", "验证信息已失效，请刷新页面后重试",
                "Your verification has expired. Refresh the page and try again.");
        register(messages, OpenApiResultCode.MERCHANT_INVALID,
                "认证商户不存在、不可用或与可信请求身份不匹配", "商户身份无效或不可用，请检查接入信息", "暂时无法处理，请联系商户",
                "This operation is unavailable. Contact the merchant.");
        register(messages, OpenApiResultCode.MERCHANT_ACCESS_BLOCKED,
                "商户因冻结等访问状态被拒绝；内部原因 {internalReason}", "商户当前无法使用该接口，请联系平台支持", "该商户暂时无法提供此服务，请联系商户",
                "This service is temporarily unavailable for this merchant. Contact the merchant.");
        register(messages, OpenApiResultCode.SECURITY_REPLAY_REJECTED,
                "既有安全防重放规则判定请求不应被接受", "请求安全校验未通过，请核对原请求及安全协议", "请求未能通过验证，请查看原操作结果",
                "Your request could not be verified. Check the original operation.");
        register(messages, OpenApiResultCode.FORBIDDEN,
                "调用主体无权执行该接口或操作", "当前账号无权执行此操作", "暂时无法进行此操作，请联系商户",
                "This operation is unavailable. Contact the merchant.");
        register(messages, OpenApiResultCode.API_NOT_FOUND,
                "请求路由未匹配；不表示具体业务订单不存在", "接口不存在，请核对请求地址和版本", "未找到相关页面，请返回后重新操作",
                "The requested page could not be found. Go back and try again.");
        register(messages, OpenApiResultCode.SOURCE_NOT_ALLOWED,
                "来源 IP 或其他访问控制条件未满足", "当前访问来源未获允许，请检查访问配置", "暂时无法进行此操作，请联系商户",
                "This operation is unavailable. Contact the merchant.");
        register(messages, OpenApiResultCode.TOO_MANY_REQUESTS,
                "命中既有 API 请求频率配额；内部摘要 {internalReason}", "请求过于频繁，请按约定等待后重试", "操作较频繁，请稍后再试",
                "You are submitting requests too quickly. Try again later.");
        register(messages, OpenApiResultCode.MERCHANT_CONFIG_UNAVAILABLE,
                "业务配置不满足条件；内部原因 {internalReason}", "商户业务配置不可用，请联系平台支持", "该服务暂不可用，请联系商户",
                "This service is unavailable. Contact the merchant.");
        register(messages, OpenApiResultCode.PRODUCT_NOT_ENABLED,
                "商户未开通目标业务产品", "商户未开通该产品，请联系平台开通", "该商户暂不支持此服务，请联系商户",
                "This service is not available for this merchant. Contact the merchant.");
        register(messages, OpenApiResultCode.CURRENCY_NOT_ENABLED,
                "平台支持该币种，但商户未启用", "商户未启用该币种，请调整币种或联系平台", "暂不支持使用该币种，请选择其他可用币种",
                "This currency is unavailable. Choose another available currency.");
        register(messages, OpenApiResultCode.PAYMENT_METHOD_NOT_ENABLED,
                "平台支持该支付方式，但商户未启用", "商户未启用该支付方式，请调整或联系平台", "该支付方式暂不可用，请选择其他可用方式",
                "This payment method is unavailable. Choose another available method.");
        register(messages, OpenApiResultCode.MERCHANT_SIGNING_MATERIAL_MISSING,
                "商户应完成的签名材料未配置；读取故障不使用此码", "商户安全配置未完成，请联系平台支持", "暂时无法完成验证，请联系商户",
                "Verification is unavailable. Contact the merchant.");
        register(messages, OpenApiResultCode.MERCHANT_RESPONSE_KEY_MISSING,
                "商户响应公钥未配置；加密实现或存储异常使用 F2000", "商户响应安全配置未完成，请联系平台支持", "暂时无法处理，请联系商户",
                "This operation is unavailable. Contact the merchant.");
        register(messages, OpenApiResultCode.TRANSACTION_SUCCESS,
                "当前交易动作成功，不代表整个资金生命周期全部完成", "Success", "操作成功",
                "Operation successful.");
        register(messages, OpenApiResultCode.TRANSACTION_PROCESSING,
                "当前状态 PROCESSING；具体内部阶段按记录查看", "Processing", "正在处理，请稍后查看结果，避免重复提交",
                "Your operation is being processed. Check again later and avoid submitting it again.");
        register(messages, OpenApiResultCode.TRANSACTION_PENDING,
                "当前状态 PENDING；按已有等待原因选择文案", "Pending", "订单待完成，请按当前页面提示处理",
                "Your operation is pending. Follow the instructions shown for your order.");
        register(messages, OpenApiResultCode.TRANSACTION_FAILED,
                "已确认 FAILED，无可靠的可公开具体原因；内部记录实际依据", "交易未完成，请查询详情或联系平台支持", "本次操作未完成，请联系商户",
                "This operation did not complete. Contact the merchant.");
        register(messages, OpenApiResultCode.ORIGINAL_ACTION_NOT_ALLOWED,
                "原交易状态或类型不满足当前关联动作要求，包括退款场景", "原交易当前不支持此操作，请查询最新状态", "当前订单暂不支持此操作，请联系商户",
                "This operation is unavailable for the order. Contact the merchant.");
        register(messages, OpenApiResultCode.REFUND_AMOUNT_EXCEEDED,
                "退款金额超过现有规则计算的剩余可退金额", "退款金额超过可退金额，请核对后提交", "退款申请暂时无法提交，请联系商户",
                "The refund request could not be submitted. Contact the merchant.");
        register(messages, OpenApiResultCode.REFUND_APPROVAL_CONFLICT,
                "退款审批已处理或版本不一致；内部接口仍遵循原权限边界", "退款审批状态已变化，请刷新后查看", "退款申请状态已更新，请查看最新结果",
                "The refund request has been updated. Check its latest status.");
        register(messages, OpenApiResultCode.AVAILABLE_BALANCE_INSUFFICIENT,
                "本平台计算的目标账户可用余额不足；渠道返回同类原因使用 F1000", "可用余额不足，请补充余额后再提交", "暂时无法完成此操作，请联系商户",
                "This operation could not be completed. Contact the merchant.");
        register(messages, OpenApiResultCode.QUOTE_EXPIRED,
                "所引用报价超过有效期或不再适用", "报价已失效，请获取并确认最新报价", "报价已更新，请确认最新报价后继续",
                "The quote has changed. Review and confirm the latest quote.");
        register(messages, OpenApiResultCode.BUSINESS_RESOURCE_NOT_FOUND,
                "订单、交易等业务记录不存在或对当前调用方不可见", "未找到相关业务记录，请核对业务编号", "未找到相关订单，请返回后重新操作",
                "The requested order could not be found. Go back and try again.");
        register(messages, OpenApiResultCode.BUSINESS_IDENTIFIER_CONFLICT,
                "业务标识冲突；已排除可正常重放的相同请求", "业务编号已被使用，请核对原请求和订单", "该操作已存在，请查看原订单状态",
                "This operation already exists. Check the original order status.");
        register(messages, OpenApiResultCode.IDEMPOTENCY_CONFLICT,
                "同一既有幂等范围及标识对应不同业务请求内容", "幂等标识与原请求不一致，请核对原请求", "操作信息不一致，请查看原订单",
                "The operation details do not match. Check the original order.");
        register(messages, OpenApiResultCode.PLATFORM_RISK_REJECTED,
                "平台业务安全/合规明确拒绝；具体原因按权限查看", "本次操作未通过安全校验，请联系平台支持", "暂时无法完成此操作，请联系商户",
                "This operation could not be completed. Contact the merchant.");
        register(messages, OpenApiResultCode.SINGLE_AMOUNT_LIMIT_EXCEEDED,
                "超过适用的公开业务单笔限额；隐藏风控策略使用 T3001", "本次金额超过业务限额，请核对后提交", "本次金额超出可用范围，请调整或联系商户",
                "This amount is outside the available range. Adjust it or contact the merchant.");
        register(messages, OpenApiResultCode.CUMULATIVE_LIMIT_EXCEEDED,
                "超过日、月等适用累计业务限额", "已达到业务累计限额，请按额度规则处理", "当前可用额度不足，请稍后查看或联系商户",
                "The available allowance is insufficient. Check again later or contact the merchant.");
        register(messages, OpenApiResultCode.CARD_NOT_SUPPORTED,
                "平台能力明确不支持该卡；商户配置、非法字段、渠道返回分别另归类", "当前业务不支持该卡类型或品牌，请选择其他可用方式", "暂不支持使用此卡，请选择其他支付方式",
                "This card is not supported. Choose another payment method.");
        register(messages, OpenApiResultCode.CURRENCY_NOT_SUPPORTED,
                "请求币种有效，但平台目标业务能力不支持", "当前业务不支持该币种，请调整后提交", "暂不支持使用该币种，请选择其他可用币种",
                "This currency is not supported. Choose another available currency.");
        register(messages, OpenApiResultCode.TRANSACTION_TYPE_NOT_SUPPORTED,
                "请求交易类型有效，但目标平台业务能力不支持", "当前业务不支持该交易类型，请检查后提交", "暂时无法进行此操作，请联系商户",
                "This operation is unavailable. Contact the merchant.");
        register(messages, OpenApiResultCode.CHANNEL_RETURNED_ERROR,
                "渠道 {channelName} 返回问题；原始码 {channelResponseCode}；参考号 {channelReference}；按权限查看真实原因", "渠道处理未能正常完成，请查询原订单或联系平台支持", "暂未完成本次操作，请查看订单结果或联系商户",
                "Channel processing failed (code: {channelResponseCode}, message: {channelResponseMessage}). "
                        + "Please check the original order or contact platform support.",
                "The transaction was declined by the issuer bank. Check the original order, "
                        + "then ask the payer to contact their bank or use another payment method when applicable.",
                "The transaction was declined. Please contact your card issuer or try a different payment method.");
        register(messages, OpenApiResultCode.SYSTEM_ERROR,
                "平台内部异常；原因 {internalReason}；关联记录见受控日志", "暂时无法完成处理，请核对原订单或联系平台支持", "暂时无法完成处理，请查看原订单或联系商户",
                "Processing could not be completed. Check the original order or contact the merchant.");
        register(messages, OpenApiResultCode.CHANNEL_CALL_UNCONFIRMED,
                "渠道调用未取得确认；发送阶段及 outcomeUncertain 见内部记录", "渠道处理结果尚未确认，请查询原订单，避免重复提交", "正在确认处理结果，请稍后查看，避免重复提交",
                "We are confirming the result. Check again later and avoid submitting the operation again.");

        if (messages.size() != OpenApiResultCode.values().length) {
            throw new IllegalStateException("Incomplete OpenAPI result message catalog");
        }
        return Collections.unmodifiableMap(messages);
    }

    /**
     * 登记使用公开码默认英文商户文案、且仅自定义付款人英文文案的普通结果码。
     */
    private static void register(EnumMap<OpenApiResultCode, MessageSet> messages,
                                 OpenApiResultCode code,
                                 String internalZh,
                                 String merchantZh,
                                 String payerZh,
                                 String payerEn) {
        register(messages, code, internalZh, merchantZh, payerZh, null, null, payerEn);
    }

    /**
     * 登记需要自定义商户英文模板的结果码；内部英文仍使用公开码安全默认文案。
     */
    private static void register(EnumMap<OpenApiResultCode, MessageSet> messages,
                                 OpenApiResultCode code,
                                 String internalZh,
                                 String merchantZh,
                                 String payerZh,
                                 String merchantEnTemplate,
                                 String payerEn) {
        register(messages, code, internalZh, merchantZh, payerZh, null, merchantEnTemplate, payerEn);
    }

    /**
     * 登记三个可信受众的完整文案集合，并拒绝重复登记同一公开码。
     */
    private static void register(EnumMap<OpenApiResultCode, MessageSet> messages,
                                 OpenApiResultCode code,
                                 String internalZh,
                                 String merchantZh,
                                 String payerZh,
                                 String internalEnTemplate,
                                 String merchantEnTemplate,
                                 String payerEn) {
        MessageSet previous = messages.put(code,
                new MessageSet(internalZh, merchantZh, payerZh,
                        internalEnTemplate, merchantEnTemplate, payerEn));
        if (previous != null) {
            throw new IllegalStateException("Duplicate OpenAPI result message: " + code.getCode());
        }
    }

    /**
     * 根据服务端可信受众和已发布语言选择模板；没有专用英文模板时使用公开码安全默认文案。
     */
    private static String template(OpenApiResultCode code,
                                   MessageSet messageSet,
                                   OpenApiMessageAudience audience,
                                   OpenApiMessageLocale locale) {
        if (audience == OpenApiMessageAudience.INTERNAL) {
            if (locale == OpenApiMessageLocale.ZH_CN) {
                return messageSet.internalZh();
            }
            return messageSet.internalEnTemplate() == null
                    ? code.getMessage() : messageSet.internalEnTemplate();
        }
        if (audience == OpenApiMessageAudience.MERCHANT) {
            if (locale == OpenApiMessageLocale.ZH_CN) {
                return messageSet.merchantZh();
            }
            return messageSet.merchantEnTemplate() == null
                    ? code.getMessage() : messageSet.merchantEnTemplate();
        }
        return locale == OpenApiMessageLocale.ZH_CN ? messageSet.payerZh() : messageSet.payerEn();
    }

    /**
     * 按参数白名单、受众权限和字段格式过滤模板值，任何越权或非法值均返回 {@code null} 触发安全回退。
     */
    private static String safeParameter(String key, String value, OpenApiMessageAudience audience) {
        if ("field".equals(key)) {
            return audience != OpenApiMessageAudience.PAYER && value != null && FIELD_PATH.matcher(value).matches()
                    ? value : null;
        }
        if (audience != OpenApiMessageAudience.INTERNAL) {
            return null;
        }
        int maxLength = switch (key) {
            case "internalReason" -> 256;
            case "channelName" -> 64;
            case "channelResponseCode", "channelReference" -> 128;
            case "channelResponseMessage" -> 256;
            default -> 0;
        };
        return maxLength == 0 ? null : safeDiagnosticValue(value, maxLength);
    }

    /**
     * 校验内部诊断摘要的长度、控制字符、标记字符和敏感数据特征，禁止原始报文进入管理端文案。
     */
    private static String safeDiagnosticValue(String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isISOControl(character) || character == '<' || character == '>'
                    || character == '{' || character == '}') {
                return null;
            }
        }
        String trimmedValue = value.trim();
        if (PAN_LIKE_DIGITS.matcher(trimmedValue).find()
                || SENSITIVE_ASSIGNMENT.matcher(trimmedValue).find()) {
            return null;
        }
        return trimmedValue;
    }

    /**
     * 当模板参数缺失或不安全时返回同一受众的静态文案，避免残留占位符或降低信息隔离等级。
     */
    private static String fallback(OpenApiResultCode code,
                                   OpenApiMessageAudience audience,
                                   OpenApiMessageLocale locale,
                                   String template) {
        if (audience == OpenApiMessageAudience.MERCHANT && locale == OpenApiMessageLocale.EN_US) {
            return code.getMessage();
        }
        if (audience == OpenApiMessageAudience.MERCHANT) {
            return switch (code) {
                case PARAM_INVALID -> "请求参数不合法，请按接口要求修改";
                case PARAM_MISSING -> "缺少必填参数";
                default -> template;
            };
        }
        if (audience == OpenApiMessageAudience.INTERNAL) {
            if (locale == OpenApiMessageLocale.EN_US) {
                return switch (code) {
                    case CHANNEL_RETURNED_ERROR ->
                            "Channel processing failed. Please check the original order or contact platform support.";
                    default -> code.getMessage();
                };
            }
            return switch (code) {
                case PARAM_INVALID -> "请求参数未通过已登记约束";
                case PARAM_MISSING -> "缺少必填字段";
                case ENCRYPTED_DATA_INVALID -> "请求载荷缺失、格式或加密协议校验失败";
                case BAD_REQUEST -> "请求报文解析或协议结构校验失败";
                case AUTHENTICATION_FAILED -> "认证校验未通过";
                case AUTHORIZATION_TIME_INVALID -> "认证 exp/iat 等时间条件无效";
                case MERCHANT_ACCESS_BLOCKED -> "商户因冻结等访问状态被拒绝";
                case TOO_MANY_REQUESTS -> "命中既有 API 请求频率配额";
                case MERCHANT_CONFIG_UNAVAILABLE -> "业务配置不满足条件";
                case CHANNEL_RETURNED_ERROR -> "渠道返回问题；按权限查看真实原因";
                case SYSTEM_ERROR -> "平台内部异常；关联记录见受控日志";
                default -> template;
            };
        }
        return template;
    }

    /**
     * 单个公开码在三类受众和两种语言下的受控模板集合。
     */
    private record MessageSet(String internalZh,
                              String merchantZh,
                              String payerZh,
                              String internalEnTemplate,
                              String merchantEnTemplate,
                              String payerEn) {
    }
}
