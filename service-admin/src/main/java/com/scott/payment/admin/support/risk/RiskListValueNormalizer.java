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
     * CARD BIN MIN LENGTH，用于保存 Risk List Value Normalizer 中与 cardbinminlength 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int CARD_BIN_MIN_LENGTH = 6;
    /**
     * CARD BIN MAX LENGTH，用于保存 Risk List Value Normalizer 中与 cardbinmaxlength 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int CARD_BIN_MAX_LENGTH = 11;
    /**
     * CARD MIN LENGTH，用于保存 Risk List Value Normalizer 中与 cardminlength 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int CARD_MIN_LENGTH = 12;
    /**
     * CARD MAX LENGTH，用于保存 Risk List Value Normalizer 中与 cardmaxlength 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int CARD_MAX_LENGTH = 19;
    private static final String EMAIL_USERNAME_REGEX = "^(?!\\.)(?!.*\\.\\.)(?!.*\\.$)[A-Z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}$";
    private static final String EMAIL_DOMAIN_REGEX = "^(?=.{1,253}$)(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\\.)+[A-Z]{2,63}$";
    private static final String POSTAL_CODE_REGEX = "^(?=.{2,20}$)[A-Z0-9]+(?:[ -][A-Z0-9]+)*$";

    /**
     * sensitive Value Crypto，用于保存 Risk List Value Normalizer 中与 sensitivevaluecrypto 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 解析normalizecard，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private NormalizedValue normalizeCard(RiskDTOs.RiskListSaveRequest request) {
        String cardNo = digits(requiredPlain(request));
        if (cardNo.length() < CARD_MIN_LENGTH || cardNo.length() > CARD_MAX_LENGTH) {
            throw invalid("卡号必须为 12-19 位纯数字");
        }
        return NormalizedValue.fixed(SensitiveDataMaskUtils.maskPan(cardNo), sha256(cardNo), sensitiveValueCrypto.encrypt(cardNo));
    }

    /**
     * 解析normalizecardBIN，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
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
     * 解析normalizeip，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param definition definition 输入值，参与 definition 的查询、校验、转换、写入或日志摘要
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
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
     * 解析normalizecountry，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private NormalizedValue normalizeCountry(RiskDTOs.RiskListSaveRequest request) {
        String country = upper(defaultIfBlank(request.getCountryAlpha3(), request.getMatchValuePlain()));
        if (!StringUtils.hasText(country) || country.length() != 3) {
            throw invalid("请选择国家/地区");
        }
        return NormalizedValue.fixed(country, sha256(country), null);
    }

    /**
     * 解析normalizephone，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private NormalizedValue normalizePhone(RiskDTOs.RiskListSaveRequest request) {
        String phone = requiredPlain(request).replaceAll("\\s+", "");
        return NormalizedValue.fixed(SensitiveDataMaskUtils.maskMobile(phone), sha256(phone), sensitiveValueCrypto.encrypt(phone));
    }

    /**
     * 解析normalize邮件，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private NormalizedValue normalizeEmail(RiskDTOs.RiskListSaveRequest request) {
        String email = normalizedEmail(requiredPlain(request));
        return NormalizedValue.fixed(SensitiveDataMaskUtils.maskEmail(email), sha256(email), sensitiveValueCrypto.encrypt(email));
    }

    /**
     * 解析normalize邮件ordomain，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private NormalizedValue normalizeEmailOrDomain(RiskDTOs.RiskListSaveRequest request) {
        String value = requiredPlain(request).trim();
        if (value.contains("@")) {
            return normalizeEmail(request);
        }
        return normalizeEmailDomain(request);
    }

    /**
     * 解析normalize邮件username，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private NormalizedValue normalizeEmailUsername(RiskDTOs.RiskListSaveRequest request) {
        String username = requiredPlain(request).trim().toLowerCase(Locale.ROOT);
        if (!username.toUpperCase(Locale.ROOT).matches(EMAIL_USERNAME_REGEX)) {
            throw invalid("邮箱用户名格式不正确");
        }
        return normalizeSimple(username, true);
    }

    /**
     * 解析normalized邮件，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param rawEmail raw Email 输入值，参与 raw邮件 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 解析normalize邮件domain，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private NormalizedValue normalizeEmailDomain(RiskDTOs.RiskListSaveRequest request) {
        String domain = normalizeDomain(requiredPlain(request));
        return NormalizedValue.fixed(domain, sha256(domain), null);
    }

    /**
     * 解析normalizedomain，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param rawDomain raw Domain 输入值，参与 rawdomain 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 解析normalize商户，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private NormalizedValue normalizeMerchant(RiskDTOs.RiskListSaveRequest request) {
        String merchantId = defaultIfBlank(request.getMatchValuePlain(), request.getMatchValueMasked());
        if (!StringUtils.hasText(merchantId)) {
            throw invalid("请选择白名单商户号");
        }
        return NormalizedValue.fixed(merchantId, sha256(merchantId), null);
    }

    /**
     * 解析normalizepostal编码，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
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
     * 解析normalizesimple，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param sensitive sensitive 输入值，参与 sensitive 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private NormalizedValue normalizeSimple(String value, boolean sensitive) {
        String normalized = value.trim();
        String masked = sensitive && normalized.length() > 8
                ? normalized.substring(0, Math.min(3, normalized.length())) + "***" + normalized.substring(normalized.length() - 3)
                : normalized;
        return NormalizedValue.fixed(masked, sha256(normalized.toLowerCase(Locale.ROOT)), sensitive ? sensitiveValueCrypto.encrypt(normalized) : null);
    }

    /**
     * 判断 is country function 条件是否成立，用于控制 Risk List Value Normalizer 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isCountryFunction(String code) {
        return "country".equals(code) || code.endsWith("Country") || code.contains("Country");
    }

    /**
     * 判断 is sensitive function 条件是否成立，用于控制 Risk List Value Normalizer 的后续分支。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 条件满足时返回 true，否则返回 false
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
     * 校验卡 BIN输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param label label 输入值，参与 label 的查询、校验、转换、写入或日志摘要
     */
    private void validateCardBin(String value, String label) {
        if (value.length() < CARD_BIN_MIN_LENGTH || value.length() > CARD_BIN_MAX_LENGTH) {
            throw invalid("BIN必须为 6-11 位纯数字");
        }
    }

    /**
     * 解析parseip，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param ip IP 输入值，参与 ip 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
     * 解析parseipv4，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
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
     * 解析parseipv6，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
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
     * 整理differingsegment计数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param startSegments start Segments 输入值，参与 startsegments 的查询、校验、转换、写入或日志摘要
     * @param endSegments end Segments 输入值，参与 endsegments 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 整理拒绝multipleranges，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void rejectMultipleRanges(String value) {
        if (value != null && (value.contains(",") || value.contains(";") || value.contains("\n"))) {
            throw invalid("一次只能录入一个IP区间");
        }
    }

    /**
     * 校验requiredplain输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String requiredPlain(RiskDTOs.RiskListSaveRequest request) {
        String plain = defaultIfBlank(request.getMatchValuePlain(), request.getMatchValueMasked());
        if (!StringUtils.hasText(plain)) {
            throw invalid("请输入匹配值");
        }
        return plain;
    }

    /**
     * 规范化digits，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 规范化rightpad，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param length length 输入值，参与 length 的查询、校验、转换、写入或日志摘要
     * @param ch ch 输入值，参与 ch 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String rightPad(String value, int length, char ch) {
        if (value.length() >= length) {
            return value;
        }
        return value + String.valueOf(ch).repeat(length - value.length());
    }

    /**
     * 规范化upper，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    /**
     * 整理默认ifblank，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param fallback fallback 输入值，参与 fallback 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    /**
     * 计算sha256摘要，用不可逆指纹关联原始内容而不暴露明文。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
     * 规范化invalid，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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
