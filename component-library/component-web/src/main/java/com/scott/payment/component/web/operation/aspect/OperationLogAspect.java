package com.scott.payment.component.web.operation.aspect;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.component.web.operation.dto.OperationLogRecord;
import com.scott.payment.component.web.operation.service.OperationLogPublisher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogAspect
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统操作日志 AOP 自动采集切面
 * @status : create
 */
@Slf4j
@Aspect
@Order(100)
@Component
public class OperationLogAspect {

    /**
     * 请求ID请求头。
     */
    private static final String HEADER_REQUEST_ID = "X-Request-Id";

    /**
     * 链路ID请求头。
     */
    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    /**
     * 商户号请求头。
     */
    private static final String HEADER_MERCHANT_ID = "X-Merchant-Id";

    /**
     * 操作人ID请求头。
     */
    private static final String HEADER_OPERATOR_ID = "X-Operator-Id";

    /**
     * 操作人名称请求头。
     */
    private static final String HEADER_OPERATOR_NAME = "X-Operator-Name";

    /**
     * 操作人类型请求头。
     */
    private static final String HEADER_OPERATOR_TYPE = "X-Operator-Type";

    /**
     * 操作地点请求头。
     */
    private static final String HEADER_OPERATOR_LOCATION = "X-Operator-Location";

    /**
     * 店铺号请求头。
     */
    private static final String HEADER_STORE_ID = "X-Store-Id";

    /**
     * 浏览器 User-Agent 请求头。
     */
    private static final String HEADER_USER_AGENT = "User-Agent";

    /**
     * 日志字段最大保存长度，避免大对象请求拖慢日志表。
     */
    private static final int MAX_LOG_TEXT_LENGTH = 4000;

    /**
     * 成功状态。
     */
    private static final int SUCCESS_STATUS = 1;

    /**
     * 失败状态。
     */
    private static final int FAILED_STATUS = 0;

    /**
     * 操作日志记录器提供器。没有业务服务提供记录器时，切面只跳过记录，不影响业务启动。
     */
    private final ObjectProvider<OperationLogPublisher> publisherProvider;

    /**
     * 创建管理类系统操作日志切面。
     *
     * @param publisherProvider 操作日志发布器提供器
     */
    public OperationLogAspect(ObjectProvider<OperationLogPublisher> publisherProvider) {
        this.publisherProvider = publisherProvider;
    }

    /**
     * 环绕采集带有 {@link OperationLog} 注解的方法调用。
     *
     * @param point     方法调用切点
     * @param operation 操作日志注解
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常
     */
    @Around("@annotation(operation)")
    public Object around(ProceedingJoinPoint point, OperationLog operation) throws Throwable {
        long startNanoTime = System.nanoTime();
        Object result = null;
        Throwable failure = null;
        try {
            result = point.proceed();
            return result;
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            long costTime = (System.nanoTime() - startNanoTime) / 1_000_000L;
            recordOperationLog(point, operation, result, failure, costTime);
        }
    }

    /**
     * 写入操作日志，日志写入失败时只打印警告，不影响原业务接口返回。
     *
     * @param point     方法调用切点
     * @param operation 操作日志注解
     * @param result    原方法返回值
     * @param failure   原方法异常
     * @param costTime  方法耗时，单位毫秒
     */
    private void recordOperationLog(ProceedingJoinPoint point,
                                    OperationLog operation,
                                    Object result,
                                    Throwable failure,
                                    long costTime) {
        OperationLogPublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            return;
        }
        try {
            HttpServletRequest request = currentRequest();
            InternalAuthAccount account = InternalAuthContextHolder.get();
            OperationLogRecord record = new OperationLogRecord();
            record.setTraceId(header(request, HEADER_TRACE_ID));
            record.setRequestId(header(request, HEADER_REQUEST_ID));
            record.setMerchantId(resolveMerchantId(request, account));
            record.setModuleName(operation.moduleName());
            record.setOperationName(operation.operation());
            record.setBusinessType(operation.businessType());
            record.setMethodName(methodName(point));
            record.setRequestMethod(request == null ? null : request.getMethod());
            record.setOperatorType(resolveOperatorType(request, account, operation.operatorType()));
            record.setOperatorId(resolveOperatorId(request, account));
            record.setOperatorName(resolveOperatorName(request, account));
            record.setOperUrl(request == null ? null : request.getRequestURI());
            record.setOperIp(clientIp(request));
            record.setOperLocation(header(request, HEADER_OPERATOR_LOCATION));
            record.setStoreId(resolveStoreId(request));
            record.setUserAgent(header(request, HEADER_USER_AGENT));
            record.setRequestParam(operation.recordRequest() ? serializeForLog(point.getArgs()) : null);
            record.setResponseResult(operation.recordResponse() ? serializeForLog(result) : null);
            record.setCostTime(costTime);
            record.setStatus(failure == null ? SUCCESS_STATUS : FAILED_STATUS);
            fillFailure(record, failure);
            publisher.publish(record);
            logStructuredOperation(record);
        } catch (RuntimeException exception) {
            log.warn("管理类系统操作日志采集失败，方法：{}，异常类型：{}",
                    methodName(point), exception.getClass().getSimpleName());
        }
    }

    /**
     * 记录后台和商户端操作审计摘要。
     * <p>
     * 写操作记录操作类型、操作者、路径、状态和耗时；查询操作只记录已脱敏的查询条件摘要，
     * 不输出完整查询结果。日志不修改审计记录，也不影响 MQ 投递结果。
     * </p>
     * @param record 操作日志采集记录，包含操作者、模块、方法、路径、请求摘要和执行状态
     */
    private void logStructuredOperation(OperationLogRecord record) {
        if (record == null) {
            return;
        }
        String event = isQuery(record.getBusinessType()) ? "ADMIN_QUERY_ACCESS" : "ADMIN_WRITE_OPERATION";
        log.info("event: {} traceId: {} module: {} operation: {} businessType: {} method: {} path: {} requestMethod: {} merchantId: {} operatorId: {} operatorType: {} querySummary: {} resultRecorded: {} status: {} durationMs: {}",
                event,
                record.getTraceId(),
                record.getModuleName(),
                record.getOperationName(),
                record.getBusinessType(),
                record.getMethodName(),
                record.getOperUrl(),
                record.getRequestMethod(),
                record.getMerchantId(),
                record.getOperatorId(),
                record.getOperatorType(),
                isQuery(record.getBusinessType()) ? record.getRequestParam() : null,
                record.getResponseResult() != null,
                record.getStatus(),
                record.getCostTime());
    }

    /**
     * 判断操作类型是否为查询。
     * <p>
     * 查询操作只允许写入条件摘要、分页信息和耗时，避免在业务日志中输出完整列表数据。
     * </p>
     * @param businessType 操作类型编码，来源于 OperationLog 注解或业务枚举
     * @return true 表示查询操作；false 表示新增、编辑、删除、审核、启停或配置变更等写操作
     */
    private boolean isQuery(Integer businessType) {
        return businessType != null && businessType == OperationTypeConstants.QUERY;
    }

    /**
     * 填充异常信息。
     *
     * @param record  操作日志采集记录
     * @param failure 原方法异常
     */
    private void fillFailure(OperationLogRecord record, Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof ServiceException serviceException) {
            record.setErrorCode(serviceException.getCode());
            record.setErrorMsg(truncate(serviceException.getMessage()));
            return;
        }
        record.setErrorCode(failure.getClass().getSimpleName());
        record.setErrorMsg(truncate(failure.getMessage()));
    }

    /**
     * 获取当前 Servlet 请求。
     *
     * @return 当前请求，不存在时返回 null
     */
    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    /**
     * 获取请求头。
     *
     * @param request 请求对象
     * @param name    请求头名称
     * @return 请求头值
     */
    private String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    /**
     * 解析商户号，认证上下文优先，请求头只作为无登录上下文的兼容兜底。
     *
     * @param request 请求对象
     * @param account 当前登录账号上下文
     * @return 商户号
     */
    private String resolveMerchantId(HttpServletRequest request, InternalAuthAccount account) {
        if (account != null && account.getMerchantId() != null && !account.getMerchantId().isBlank()) {
            return account.getMerchantId();
        }
        return header(request, HEADER_MERCHANT_ID);
    }

    /**
     * 解析操作人类型，认证上下文优先，请求头只作为无登录上下文的兼容兜底。
     *
     * @param request         请求对象
     * @param account         当前登录账号上下文
     * @param defaultOperator 默认操作人类型
     * @return 操作人类型
     */
    private Integer resolveOperatorType(HttpServletRequest request, InternalAuthAccount account, int defaultOperator) {
        if (account != null) {
            return account.getMerchantId() != null && !account.getMerchantId().isBlank() ? 2 : defaultOperator;
        }
        String operatorType = header(request, HEADER_OPERATOR_TYPE);
        if (operatorType == null || operatorType.isBlank()) {
            return defaultOperator;
        }
        try {
            return Integer.parseInt(operatorType);
        } catch (NumberFormatException exception) {
            return defaultOperator;
        }
    }

    /**
     * 解析操作人ID，认证上下文优先，请求头只作为无登录上下文的兼容兜底。
     *
     * @param request 请求对象
     * @param account 当前登录账号上下文
     * @return 操作人ID
     */
    private String resolveOperatorId(HttpServletRequest request, InternalAuthAccount account) {
        if (account != null && account.getAccountId() != null) {
            return String.valueOf(account.getAccountId());
        }
        return header(request, HEADER_OPERATOR_ID);
    }

    /**
     * 解析操作人名称，认证上下文优先，请求头只作为无登录上下文的兼容兜底。
     *
     * @param request 请求对象
     * @param account 当前登录账号上下文
     * @return 操作人名称
     */
    private String resolveOperatorName(HttpServletRequest request, InternalAuthAccount account) {
        if (account != null) {
            if (account.getRealName() != null && !account.getRealName().isBlank()) {
                return account.getRealName();
            }
            return account.getLoginAccount();
        }
        return header(request, HEADER_OPERATOR_NAME);
    }

    /**
     * 解析店铺号，优先使用请求头，缺失时继续沿用空值，避免误把账号信息写入店铺字段。
     *
     * @param request 请求对象
     * @return 店铺号
     */
    private String resolveStoreId(HttpServletRequest request) {
        String storeId = header(request, HEADER_STORE_ID);
        if (storeId != null && !storeId.isBlank()) {
            return storeId;
        }
        return null;
    }

    /**
     * 获取客户端 IP。
     *
     * @param request 请求对象
     * @return 客户端 IP
     */
    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 获取方法全名。
     *
     * @param point 方法调用切点
     * @return 方法全名
     */
    private String methodName(ProceedingJoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    /**
     * 序列化对象并做脱敏、截断处理。
     *
     * @param value 待记录对象
     * @return 脱敏后的日志文本
     */
    private String serializeForLog(Object value) {
        Object safeValue = value instanceof Object[] args
                ? Arrays.stream(args).filter(this::loggableArgument).toList()
                : value;
        String json = JsonUtils.toJsonString(safeValue);
        return truncate(SensitiveDataMaskUtils.maskJsonSafely(json));
    }

    /**
     * 判断方法参数是否适合写入日志。
     *
     * @param argument 方法参数
     * @return true 表示可以记录
     */
    private boolean loggableArgument(Object argument) {
        return Objects.nonNull(argument)
                && !(argument instanceof HttpServletRequest)
                && !(argument instanceof MultipartFile);
    }

    /**
     * 截断日志文本。
     *
     * @param value 原始文本
     * @return 截断后的文本
     */
    private String truncate(String value) {
        if (value == null || value.length() <= MAX_LOG_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LOG_TEXT_LENGTH);
    }
}
