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
     * SERVICE NAME，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String SERVICE_NAME = "service-openapi";
    /**
     * HEADER USER AGENT，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String HEADER_USER_AGENT = "User-Agent";
    /**
     * HEADER REQUEST ID，用于定位 Security Intercept Event Recorder 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；不允许为空；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String HEADER_REQUEST_ID = "X-Request-Id";
    /**
     * MAX PATH LENGTH，表示接口路径、资源路径或路由匹配路径。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int MAX_PATH_LENGTH = 512;
    /**
     * MAX TEXT LENGTH，用于保存 Security Intercept Event Recorder 中与 maxtextlength 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int MAX_TEXT_LENGTH = 512;
    /**
     * MAX HEADER SUMMARY LENGTH，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int MAX_HEADER_SUMMARY_LENGTH = 1024;
    /**
     * EVENT TIME FORMATTER，用于保存 Security Intercept Event Recorder 中与 eventtimeformatter 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；不允许为空；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * event Mapper 依赖，用于 Security Intercept Event Recorder 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 创建eventno，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String generateEventNo(LocalDateTime now) {
        return "SIE" + EVENT_TIME_FORMATTER.format(now) + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    /**
     * 解析resolveclientip，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户开放接口服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
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
     * 解析resolve请求path，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户开放接口服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolveRequestPath(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getRequestURI();
    }

    /**
     * 解析resolvetraceID，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户开放接口服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request == null ? null : request.getHeader(TraceContext.TRACE_ID_HEADER);
        if (StringUtils.hasText(traceId)) {
            return traceId.trim();
        }
        return TraceContext.getTraceId();
    }

    /**
     * 解析resolve请求ID，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户开放接口服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    private String resolveRequestId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return trimToNull(request.getHeader(HEADER_REQUEST_ID));
    }

    /**
     * 构造header汇总对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 商户开放接口服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
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
     * 整理present文本，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Boolean presentText(String value) {
        return StringUtils.hasText(value) ? Boolean.TRUE : null;
    }

    /**
     * 整理非空摘要字段，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param summary summary 输入值，参与 汇总数据 的查询、校验、转换、写入或日志摘要
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
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
     * 脱敏文本，返回可安全写入日志或展示的摘要文本。
     * <p>
     * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String sanitizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return SensitiveDataMaskUtils.maskJsonSafely(value.trim());
    }

    /**
     * 整理默认ifblank，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param defaultValue default Value 输入值，参与 默认value 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    /**
     * 规范化trimtonull，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 整理限额，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param maxLength max Length 输入值，参与 maxlength 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
