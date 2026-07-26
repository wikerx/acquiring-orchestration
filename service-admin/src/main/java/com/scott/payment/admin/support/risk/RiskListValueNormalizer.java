package com.scott.payment.admin.support.risk;

import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.net.IDN;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskListValueNormalizer
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 收单风控名单匹配值归一化组件，负责管理端录入值到脱敏值、哈希、区间数值的转换。
 * @status : create
 */
@Component
public class RiskListValueNormalizer {

    /**
     * CARD BIN MIN LENGTH 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int CARD_BIN_MIN_LENGTH = 6;
    /**
     * CARD BIN MAX LENGTH 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int CARD_BIN_MAX_LENGTH = 11;
    /**
     * CARD MIN LENGTH 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int CARD_MIN_LENGTH = 12;
    /**
     * CARD MAX LENGTH 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int CARD_MAX_LENGTH = 19;
    private static final String EMAIL_USERNAME_REGEX = "^(?!\\.)(?!.*\\.\\.)(?!.*\\.$)[A-Z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}$";
    private static final String EMAIL_DOMAIN_REGEX = "^(?=.{1,253}$)(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\\.)+[A-Z]{2,63}$";
    private static final String POSTAL_CODE_REGEX = "^(?=.{2,20}$)[A-Z0-9]+(?:[ -][A-Z0-9]+)*$";

    /**
     * sensitive Value Crypto 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final RiskSensitiveValueCrypto sensitiveValueCrypto;

    /**
     * 创建名单值归一化组件。
     *
     * @param sensitiveValueCrypto 敏感名单值加解密组件
     */
    public RiskListValueNormalizer(RiskSensitiveValueCrypto sensitiveValueCrypto) {
        this.sensitiveValueCrypto = sensitiveValueCrypto;
    }

    /**
     * 按风控功能类型归一化名单值。
     *
     * @param definition 风控功能定义
     * @param request    管理端保存请求
     * @return 可直接写入数据库的字段补充值
     */
    public NormalizedValue normalize(RiskFunctionDefinition definition, RiskDTOs.RiskListSaveRequest request) {
        String code = definition.getFunctionCode();
        if ("cardBin".equals(code)) {
            return normalizeCardBin(request);
        }
        if ("ip".equals(code)) {
            return normalizeIp(definition, request);
        }
        if (isCountryFunction(code)) {
            return normalizeCountry(request);
        }
        if ("cardNo".equals(code) || "card".equals(code)) {
            return normalizeCard(request);
        }
        if ("phone".equals(code)) {
            return normalizePhone(request);
        }
        if ("email".equals(code) && "AML".equalsIgnoreCase(definition.getModuleType())) {
            return normalizeEmailOrDomain(request);
        }
        if ("email".equals(code)) {
            return normalizeEmail(request);
        }
        if ("emailDomain".equals(code)) {
            return normalizeEmailDomain(request);
        }
        if ("emailUsername".equals(code)) {
            return normalizeEmailUsername(request);
        }
        if ("merchant".equals(code)) {
            return normalizeMerchant(request);
        }
        if ("billingAddress".equals(code) || "shippingAddress".equals(code) || "merchantBillingAddress".equals(code)) {
            return normalizeSimple(requiredPlain(request), false);
        }
        if ("billingZip".equals(code) || "shippingZip".equals(code)) {
            return normalizePostalCode(request);
        }
        return normalizeSimple(requiredPlain(request), isSensitiveFunction(code));
    }

    /**
     * 解密编辑态可见明文。
     *
     * @param cipherText 数据库存储的密文
     * @return 编辑态明文
     */
    public String decryptPlain(String cipherText) {
        return sensitiveValueCrypto.decrypt(cipherText);
    }

    /**
     * 标准化 normalize Card 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 标准化后的业务字段值
     */
    private NormalizedValue normalizeCard(RiskDTOs.RiskListSaveRequest request) {
        String cardNo = digits(requiredPlain(request));
        if (cardNo.length() < CARD_MIN_LENGTH || cardNo.length() > CARD_MAX_LENGTH) {
            throw invalid("卡号必须为 12-19 位纯数字");
        }
        return NormalizedValue.fixed(SensitiveDataMaskUtils.maskPan(cardNo), sha256(cardNo), sensitiveValueCrypto.encrypt(cardNo));
    }

    /**
     * 标准化 normalize Card Bin 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 标准化后的业务字段值
     */
    private NormalizedValue normalizeCardBin(RiskDTOs.RiskListSaveRequest request) {
        String start = digits(defaultIfBlank(request.getMatchValueStart(), request.getMatchValuePlain()));
        String end = digits(defaultIfBlank(request.getMatchValueEnd(), start));
        validateCardBin(start, "card BIN start");
        validateCardBin(end, "card BIN end");
        if (!start.substring(0, CARD_BIN_MIN_LENGTH).equals(end.substring(0, CARD_BIN_MIN_LENGTH))) {
            throw invalid("起始BIN和截止BIN前 6 位必须一致");
        }
        String normalizedStart = rightPad(start, CARD_BIN_MAX_LENGTH, '0');
        String normalizedEnd = rightPad(end, CARD_BIN_MAX_LENGTH, '9');
        BigInteger startNumber = new BigInteger(normalizedStart);
        BigInteger endNumber = new BigInteger(normalizedEnd);
        if (startNumber.compareTo(endNumber) > 0) {
            throw invalid("截止BIN补齐后必须大于等于起始BIN");
        }
        String masked = normalizedStart.equals(normalizedEnd) ? normalizedStart : normalizedStart + "-" + normalizedEnd;
        return NormalizedValue.range(masked, sha256(normalizedStart + "-" + normalizedEnd), normalizedStart, normalizedEnd, startNumber.toString(), endNumber.toString(), null);
    }

    /**
     * 标准化 normalize Ip 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 标准化后的业务字段值
     */
    private NormalizedValue normalizeIp(RiskFunctionDefinition definition, RiskDTOs.RiskListSaveRequest request) {
        String start = defaultIfBlank(request.getMatchValueStart(), request.getMatchValuePlain());
        String end = request.getMatchValueEnd();
        if ("BLACK".equalsIgnoreCase(definition.getModuleType())) {
            if (!StringUtils.hasText(start) || !StringUtils.hasText(end)) {
                throw invalid("起始IP和截止IP必须输入");
            }
        }
        end = defaultIfBlank(end, start);
        rejectMultipleRanges(start);
        rejectMultipleRanges(end);
        if ("WHITE".equalsIgnoreCase(definition.getModuleType()) && StringUtils.hasText(end) && !start.equals(end)) {
            throw invalid("IP白名单仅支持单个IP地址");
        }
        ParsedIp startIp = parseIp(start);
        ParsedIp endIp = parseIp(end);
        if (!startIp.version().equals(endIp.version())) {
            throw invalid("起始IP和截止IP必须为相同 IP 类型");
        }
        if ("BLACK".equalsIgnoreCase(definition.getModuleType()) && differingSegmentCount(startIp.segments(), endIp.segments()) > 1) {
            throw invalid("IP区间最多只能有一个段位不一致");
        }
        if (startIp.number().compareTo(endIp.number()) > 0) {
            throw invalid("起始IP不能大于截止IP");
        }
        String masked = startIp.original().equals(endIp.original()) ? startIp.original() : startIp.original() + "-" + endIp.original();
        return NormalizedValue.range(masked, sha256(startIp.version() + "-" + startIp.number() + "-" + endIp.number()),
                startIp.original(), endIp.original(), startIp.number().toString(), endIp.number().toString(), null, startIp.version());
    }

    /**
     * 标准化 normalize Country 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 标准化后的业务字段值
     */
    private NormalizedValue normalizeCountry(RiskDTOs.RiskListSaveRequest request) {
        String country = upper(defaultIfBlank(request.getCountryAlpha3(), request.getMatchValuePlain()));
        if (!StringUtils.hasText(country) || country.length() != 3) {
            throw invalid("请选择国家/地区");
        }
        return NormalizedValue.fixed(country, sha256(country), null);
    }

    /**
     * 标准化 normalize Phone 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 标准化后的业务字段值
     */
    private NormalizedValue normalizePhone(RiskDTOs.RiskListSaveRequest request) {
        String phone = requiredPlain(request).replaceAll("\\s+", "");
        return NormalizedValue.fixed(SensitiveDataMaskUtils.maskMobile(phone), sha256(phone), sensitiveValueCrypto.encrypt(phone));
    }

    /**
     * 标准化 normalize Email 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 标准化后的业务字段值
     */
    private NormalizedValue normalizeEmail(RiskDTOs.RiskListSaveRequest request) {
        String email = normalizedEmail(requiredPlain(request));
        return NormalizedValue.fixed(SensitiveDataMaskUtils.maskEmail(email), sha256(email), sensitiveValueCrypto.encrypt(email));
    }

    /**
     * 标准化 normalize Email Or Domain 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 标准化后的业务字段值
     */
    private NormalizedValue normalizeEmailOrDomain(RiskDTOs.RiskListSaveRequest request) {
        String value = requiredPlain(request).trim();
        if (value.contains("@")) {
            return normalizeEmail(request);
        }
        return normalizeEmailDomain(request);
    }

    /**
     * 标准化 normalize Email Username 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 标准化后的业务字段值
     */
    private NormalizedValue normalizeEmailUsername(RiskDTOs.RiskListSaveRequest request) {
        String username = requiredPlain(request).trim().toLowerCase(Locale.ROOT);
        if (!username.toUpperCase(Locale.ROOT).matches(EMAIL_USERNAME_REGEX)) {
            throw invalid("邮箱用户名格式不正确");
        }
        return normalizeSimple(username, true);
    }

    /**
     * 标准化 normalized Email 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rawEmail raw Email 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private String normalizedEmail(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex != email.lastIndexOf('@') || atIndex == email.length() - 1) {
            throw invalid("邮箱格式不正确");
        }
        String username = email.substring(0, atIndex);
        String domain = normalizeDomain(email.substring(atIndex + 1));
        if (!username.toUpperCase(Locale.ROOT).matches(EMAIL_USERNAME_REGEX)) {
            throw invalid("邮箱格式不正确");
        }
        return username + "@" + domain;
    }

    /**
     * 标准化 normalize Email Domain 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 标准化后的业务字段值
     */
    private NormalizedValue normalizeEmailDomain(RiskDTOs.RiskListSaveRequest request) {
        String domain = normalizeDomain(requiredPlain(request));
        return NormalizedValue.fixed(domain, sha256(domain), null);
    }

    /**
     * 标准化 normalize Domain 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rawDomain raw Domain 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private String normalizeDomain(String rawDomain) {
        String domain = rawDomain.trim().toLowerCase(Locale.ROOT);
        while (domain.startsWith("@")) {
            domain = domain.substring(1);
        }
        try {
            domain = IDN.toASCII(domain).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw invalid("邮箱域名格式不正确");
        }
        if (!domain.toUpperCase(Locale.ROOT).matches(EMAIL_DOMAIN_REGEX)) {
            throw invalid("邮箱域名格式不正确");
        }
        return domain;
    }

    /**
     * 标准化 normalize Merchant 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 标准化后的业务字段值
     */
    private NormalizedValue normalizeMerchant(RiskDTOs.RiskListSaveRequest request) {
        String merchantId = defaultIfBlank(request.getMatchValuePlain(), request.getMatchValueMasked());
        if (!StringUtils.hasText(merchantId)) {
            throw invalid("请选择白名单商户号");
        }
        return NormalizedValue.fixed(merchantId, sha256(merchantId), null);
    }

    /**
     * 标准化 normalize Postal Code 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 标准化后的业务字段值
     */
    private NormalizedValue normalizePostalCode(RiskDTOs.RiskListSaveRequest request) {
        String displayValue = requiredPlain(request).trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (!displayValue.matches(POSTAL_CODE_REGEX)) {
            throw invalid("邮编格式不正确，仅支持字母、数字、空格和短横线，长度 2-20 位");
        }
        String lookupValue = displayValue.replaceAll("[\\s-]", "");
        return NormalizedValue.fixed(displayValue, sha256(lookupValue), null);
    }

    /**
     * 标准化 normalize Simple 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param sensitive sensitive 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private NormalizedValue normalizeSimple(String value, boolean sensitive) {
        String normalized = value.trim();
        String masked = sensitive && normalized.length() > 8
                ? normalized.substring(0, Math.min(3, normalized.length())) + "***" + normalized.substring(normalized.length() - 3)
                : normalized;
        return NormalizedValue.fixed(masked, sha256(normalized.toLowerCase(Locale.ROOT)), sensitive ? sensitiveValueCrypto.encrypt(normalized) : null);
    }

    /**
     * 判断 is Country Function 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param code code 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isCountryFunction(String code) {
        return "country".equals(code) || code.endsWith("Country") || code.contains("Country");
    }

    /**
     * 判断 is Sensitive Function 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param code code 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isSensitiveFunction(String code) {
        return code.contains("Fingerprint")
                || code.contains("Name")
                || code.contains("Address")
                || code.contains("Person")
                || code.contains("enterprise")
                || code.contains("customer")
                || code.contains("Customer");
    }

    /**
     * 校验 validate Card Bin 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param label label 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void validateCardBin(String value, String label) {
        if (value.length() < CARD_BIN_MIN_LENGTH || value.length() > CARD_BIN_MAX_LENGTH) {
            throw invalid("BIN必须为 6-11 位纯数字");
        }
    }

    /**
     * 解析 parse Ip 输入文本并转换为内部可校验的数据结构。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param ip ip 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析后的内部数据结构或业务值
     */
    private ParsedIp parseIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            throw invalid("请输入IP地址");
        }
        String value = ip.trim();
        if (value.contains(":")) {
            return parseIpv6(value);
        }
        return parseIpv4(value);
    }

    /**
     * 解析 parse Ipv4 输入文本并转换为内部可校验的数据结构。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 解析后的内部数据结构或业务值
     */
    private ParsedIp parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            throw invalid("IP地址格式不正确");
        }
        int[] segments = new int[4];
        BigInteger number = BigInteger.ZERO;
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (!part.matches("\\d{1,3}")) {
                throw invalid("IP地址格式不正确");
            }
            int segment = Integer.parseInt(part);
            if (segment < 0 || segment > 255) {
                throw invalid("IP地址格式不正确");
            }
            segments[index] = segment;
            number = number.shiftLeft(8).add(BigInteger.valueOf(segment));
        }
        return new ParsedIp(value, "IPV4", number, segments);
    }

    /**
     * 解析 parse Ipv6 输入文本并转换为内部可校验的数据结构。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 解析后的内部数据结构或业务值
     */
    private ParsedIp parseIpv6(String value) {
        if (!value.matches("[0-9a-fA-F:]+")) {
            throw invalid("IP地址格式不正确");
        }
        try {
            byte[] bytes = InetAddress.getByName(value).getAddress();
            if (bytes.length != 16) {
                throw invalid("IP地址格式不正确");
            }
            int[] segments = new int[8];
            for (int index = 0; index < segments.length; index++) {
                segments[index] = ((bytes[index * 2] & 0xff) << 8) | (bytes[index * 2 + 1] & 0xff);
            }
            return new ParsedIp(value, "IPV6", new BigInteger(1, bytes), segments);
        } catch (UnknownHostException exception) {
            throw invalid("IP地址格式不正确");
        }
    }

    /**
     * 完成 differing Segment Count 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param startSegments start Segments 输入值，含义由调用方法名称和所属业务对象限定
     * @param endSegments end Segments 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private int differingSegmentCount(int[] startSegments, int[] endSegments) {
        int count = 0;
        for (int index = 0; index < startSegments.length; index++) {
            if (startSegments[index] != endSegments[index]) {
                count++;
            }
        }
        return count;
    }

    /**
     * 完成 reject Multiple Ranges 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     */
    private void rejectMultipleRanges(String value) {
        if (value != null && (value.contains(",") || value.contains(";") || value.contains("\n"))) {
            throw invalid("一次只能录入一个IP区间");
        }
    }

    /**
     * 强制校验 required Plain 必填值，缺失时中断当前业务流程。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    private String requiredPlain(RiskDTOs.RiskListSaveRequest request) {
        String plain = defaultIfBlank(request.getMatchValuePlain(), request.getMatchValueMasked());
        if (!StringUtils.hasText(plain)) {
            throw invalid("请输入匹配值");
        }
        return plain;
    }

    /**
     * 完成 digits 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private String digits(String value) {
        if (!StringUtils.hasText(value)) {
            throw invalid("请输入数字值");
        }
        String digits = value.replaceAll("\\D", "");
        if (!digits.equals(value.replaceAll("\\s+", ""))) {
            throw invalid("只允许输入数字");
        }
        return digits;
    }

    /**
     * 完成 right Pad 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param length length 输入值，含义由调用方法名称和所属业务对象限定
     * @param ch ch 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String rightPad(String value, int length, char ch) {
        if (value.length() >= length) {
            return value;
        }
        return value + String.valueOf(ch).repeat(length - value.length());
    }

    /**
     * 完成 upper 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    /**
     * 完成 default If Blank 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param fallback fallback 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    /**
     * 完成 sha256 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "风控名单值哈希计算失败");
        }
    }

    /**
     * 完成 invalid 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     * @return 当前方法计算或转换后的业务结果
     */
    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    /**
     * 归一化后的名单值。
     */
    public record NormalizedValue(String matchValueMasked,
                                  String matchValueHash,
                                  String matchValueStart,
                                  String matchValueEnd,
                                  String matchValueStartNumber,
                                  String matchValueEndNumber,
                                  String matchValueCipher,
                                  String ipVersion) {

        /**
         * 创建固定值名单归一化结果。
         *
         * @param masked 脱敏展示值
         * @param hash   归一化哈希
         * @param cipher 敏感值密文，非敏感名单为空
         * @return 固定值归一化结果
         */
        public static NormalizedValue fixed(String masked, String hash, String cipher) {
            return new NormalizedValue(masked, hash, null, null, null, null, cipher, null);
        }

        /**
         * 创建不区分 IP 版本的区间归一化结果。
         *
         * @param masked      区间展示值
         * @param hash        区间归一化哈希
         * @param start       区间起始展示值
         * @param end         区间截止展示值
         * @param startNumber 区间起始数值
         * @param endNumber   区间截止数值
         * @param cipher      敏感值密文，非敏感名单为空
         * @return 区间归一化结果
         */
        public static NormalizedValue range(String masked, String hash, String start, String end, String startNumber, String endNumber, String cipher) {
            return range(masked, hash, start, end, startNumber, endNumber, cipher, null);
        }

        /**
         * 创建带 IP 版本的区间归一化结果。
         *
         * @param masked      区间展示值
         * @param hash        区间归一化哈希
         * @param start       区间起始展示值
         * @param end         区间截止展示值
         * @param startNumber 区间起始数值
         * @param endNumber   区间截止数值
         * @param cipher      敏感值密文，非敏感名单为空
         * @param ipVersion   IP 版本：IPV4、IPV6
         * @return 区间归一化结果
         */
        public static NormalizedValue range(String masked, String hash, String start, String end, String startNumber, String endNumber, String cipher, String ipVersion) {
            return new NormalizedValue(masked, hash, start, end, startNumber, endNumber, cipher, ipVersion);
        }

    }

    private record ParsedIp(String original, String version, BigInteger number, int[] segments) {
        private ParsedIp {
            segments = Arrays.copyOf(segments, segments.length);
        }
    }
}
