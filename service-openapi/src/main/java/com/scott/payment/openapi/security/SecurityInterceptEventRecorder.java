package com.scott.payment.openapi.security;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.db.security.entity.SecurityInterceptEventDO;
import com.scott.payment.component.db.security.mapper.SecurityInterceptEventMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SecurityInterceptEventRecorder
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 安全拦截事件记录器，位于 service-openapi 安全层，只记录脱敏排查元数据，禁止保存请求体、JWT、Cookie、密钥或完整密文。
 * @status : create
 */
@Slf4j
@Service
public class SecurityInterceptEventRecorder {

    /**
     * OpenAPI 来源层。
     */
    public static final String SOURCE_OPENAPI = "OPENAPI";

    /**
     * 渠道回调来源层。
     */
    public static final String SOURCE_CHANNEL = "CHANNEL";

    /**
     * 阻断动作。
     */
    public static final String ACTION_BLOCK = "BLOCK";

    /**
     * 中风险。
     */
    public static final String RISK_MEDIUM = "MEDIUM";

    /**
     * 高风险。
     */
    public static final String RISK_HIGH = "HIGH";

    /**
     * 严重风险。
     */
    public static final String RISK_CRITICAL = "CRITICAL";

    /**
     * SERVICE NAME 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String SERVICE_NAME = "service-openapi";
    /**
     * HEADER USER AGENT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String HEADER_USER_AGENT = "User-Agent";
    /**
     * HEADER REQUEST ID 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    /**
     * MAX PATH LENGTH 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int MAX_PATH_LENGTH = 512;
    /**
     * MAX TEXT LENGTH 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int MAX_TEXT_LENGTH = 512;
    /**
     * MAX HEADER SUMMARY LENGTH 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int MAX_HEADER_SUMMARY_LENGTH = 1024;
    /**
     * EVENT TIME FORMATTER 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * event Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SecurityInterceptEventMapper eventMapper;

    /**
     * 创建安全拦截事件记录器。
     *
     * @param eventMapper 安全拦截事件 Mapper
     */
    public SecurityInterceptEventRecorder(SecurityInterceptEventMapper eventMapper) {
        this.eventMapper = eventMapper;
    }

    /**
     * 记录已被安全链路阻断的请求。
     *
     * @param request       HTTP 请求
     * @param sourceLayer   来源层级
     * @param eventType     事件类型
     * @param riskLevel     风险等级
     * @param merchantId    商户号，无法解析时为空
     * @param hitRuleCode   命中规则编码
     * @param reasonCode    原因码
     * @param reasonMessage 原因说明
     */
    public void recordBlocked(HttpServletRequest request,
                              String sourceLayer,
                              String eventType,
                              String riskLevel,
                              String merchantId,
                              String hitRuleCode,
                              String reasonCode,
                              String reasonMessage) {
        try {
            LocalDateTime now = LocalDateTime.now();
            SecurityInterceptEventDO row = new SecurityInterceptEventDO();
            row.setEventNo(generateEventNo(now));
            row.setEventTime(now);
            row.setSourceLayer(defaultIfBlank(sourceLayer, SOURCE_OPENAPI));
            row.setEventType(limit(defaultIfBlank(eventType, "SECURITY_INTERCEPT"), 64));
            row.setRiskLevel(defaultIfBlank(riskLevel, RISK_HIGH));
            row.setAction(ACTION_BLOCK);
            row.setMerchantId(limit(trimToNull(merchantId), 32));
            row.setClientIp(limit(resolveClientIp(request), 45));
            row.setRequestMethod(limit(request == null ? null : request.getMethod(), 16));
            row.setRequestPath(limit(resolveRequestPath(request), MAX_PATH_LENGTH));
            row.setTraceId(limit(resolveTraceId(request), 64));
            row.setRequestId(limit(resolveRequestId(request), 64));
            row.setUserAgent(limit(request == null ? null : request.getHeader(HEADER_USER_AGENT), MAX_TEXT_LENGTH));
            row.setReasonCode(limit(trimToNull(reasonCode), 64));
            row.setReasonMessage(limit(sanitizeText(reasonMessage), MAX_TEXT_LENGTH));
            row.setServiceName(SERVICE_NAME);
            row.setHitRuleCode(limit(trimToNull(hitRuleCode), 64));
            row.setHeaderSummary(limit(buildHeaderSummary(request), MAX_HEADER_SUMMARY_LENGTH));
            row.setProcessStatus(0);
            row.setGmtCreate(now);
            row.setGmtModified(now);
            eventMapper.insert(row);
        } catch (RuntimeException exception) {
            log.warn("安全拦截事件记录失败，不影响原始安全拦截结果，事件类型：{}，错误类型：{}",
                    eventType,
                    exception.getClass().getSimpleName());
        }
    }

    /**
     * 从业务异常中提取响应码作为原因码。
     *
     * @param exception 业务异常
     * @return 原因码
     */
    public String reasonCode(Throwable exception) {
        if (exception instanceof ServiceException serviceException) {
            return serviceException.getCode();
        }
        return exception == null ? null : exception.getClass().getSimpleName();
    }

    /**
     * 从异常中提取脱敏后的原因说明。
     *
     * @param exception 异常
     * @return 脱敏原因说明
     */
    public String reasonMessage(Throwable exception) {
        return exception == null ? null : exception.getMessage();
    }

    /**
     * 完成 generate Event No 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 SecurityInterceptEventRecorder 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param now now 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String generateEventNo(LocalDateTime now) {
        return "SIE" + EVENT_TIME_FORMATTER.format(now) + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /**
     * 解析 resolve Client Ip 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 SecurityInterceptEventRecorder 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 解析或查询得到的业务值
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String gatewayClientIp = request.getHeader(MerchantIpWhitelistAccessService.HEADER_GATEWAY_CLIENT_IP);
        if (StringUtils.hasText(gatewayClientIp)) {
            return gatewayClientIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 解析 resolve Request Path 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 SecurityInterceptEventRecorder 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 解析或查询得到的业务值
     */
    private String resolveRequestPath(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getRequestURI();
    }

    /**
     * 解析 resolve Trace Id 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 SecurityInterceptEventRecorder 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 解析或查询得到的业务值
     */
    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request == null ? null : request.getHeader(TraceContext.TRACE_ID_HEADER);
        if (StringUtils.hasText(traceId)) {
            return traceId.trim();
        }
        return TraceContext.getTraceId();
    }

    /**
     * 解析 resolve Request Id 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 SecurityInterceptEventRecorder 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 解析或查询得到的业务值
     */
    private String resolveRequestId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return trimToNull(request.getHeader(HEADER_REQUEST_ID));
    }

    /**
     * 构建 build Header Summary 对应的领域对象、请求对象或日志对象。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 SecurityInterceptEventRecorder 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 转换或构建后的目标对象
     */
    private String buildHeaderSummary(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("authorizationPresent", StringUtils.hasText(request.getHeader("Authorization")) || StringUtils.hasText(request.getHeader("authorization")));
        putIfPresent(summary, "contentType", request.getContentType());
        putIfPresent(summary, "gatewayClientIp", request.getHeader(MerchantIpWhitelistAccessService.HEADER_GATEWAY_CLIENT_IP));
        putIfPresent(summary, "traceId", request.getHeader(TraceContext.TRACE_ID_HEADER));
        putIfPresent(summary, "requestId", request.getHeader(HEADER_REQUEST_ID));
        putIfPresent(summary, "userAgent", request.getHeader(HEADER_USER_AGENT));
        putIfPresent(summary, "channelTimestampPresent", presentText(request.getHeader("X-Channel-Timestamp")));
        putIfPresent(summary, "channelNoncePresent", presentText(request.getHeader("X-Channel-Nonce")));
        putIfPresent(summary, "channelSignaturePresent", presentText(request.getHeader("X-Channel-Signature")));
        return sanitizeText(JsonUtils.toJsonString(summary));
    }

    /**
     * 完成 present Text 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 SecurityInterceptEventRecorder 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private Boolean presentText(String value) {
        return StringUtils.hasText(value) ? Boolean.TRUE : null;
    }

    /**
     * 完成 put If Present 的本地校验、字段转换或状态更新。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 SecurityInterceptEventRecorder 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param summary summary 输入值，含义由调用方法名称和所属业务对象限定
     * @param key key 输入值，含义由调用方法名称和所属业务对象限定
     * @param value 待校验或转换的原始值
     */
    private void putIfPresent(Map<String, Object> summary, String key, Object value) {
        if (value instanceof String text && !StringUtils.hasText(text)) {
            return;
        }
        if (value != null) {
            summary.put(key, value);
        }
    }

    /**
     * 完成 sanitize Text 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 SecurityInterceptEventRecorder 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String sanitizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return SensitiveDataMaskUtils.maskJsonSafely(value.trim());
    }

    /**
     * 完成 default If Blank 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 SecurityInterceptEventRecorder 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param defaultValue default Value 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    /**
     * 完成 trim To Null 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 SecurityInterceptEventRecorder 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 完成 limit 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 SecurityInterceptEventRecorder 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param maxLength max Length 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
