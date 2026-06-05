package com.scott.payment.admin.aspect;

import com.scott.payment.admin.annotation.AdminOperationLog;
import com.scott.payment.admin.dto.SysOperLogRecordRequest;
import com.scott.payment.admin.service.AdminOperLogService;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
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
 * @classname : AdminOperationLogAspect
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理后台操作日志 AOP 自动采集切面
 * @status : create
 */
@Slf4j
@Aspect
@Order(100)
@Component
public class AdminOperationLogAspect {

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
     * 日志字段最大保存长度，避免大对象请求拖慢管理后台日志表。
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
     * 操作日志服务。
     */
    private final AdminOperLogService operLogService;

    /**
     * 创建管理后台操作日志切面。
     *
     * @param operLogService 操作日志服务
     */
    public AdminOperationLogAspect(AdminOperLogService operLogService) {
        this.operLogService = operLogService;
    }

    /**
     * 环绕采集带有 {@link AdminOperationLog} 注解的方法调用。
     *
     * @param point     方法调用切点
     * @param operation 操作日志注解
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常
     */
    @Around("@annotation(operation)")
    public Object around(ProceedingJoinPoint point, AdminOperationLog operation) throws Throwable {
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
     * 写入操作日志，日志写入失败时只打印错误，不影响原业务接口返回。
     *
     * @param point     方法调用切点
     * @param operation 操作日志注解
     * @param result    原方法返回值
     * @param failure   原方法异常
     * @param costTime  方法耗时，单位毫秒
     */
    private void recordOperationLog(ProceedingJoinPoint point,
                                    AdminOperationLog operation,
                                    Object result,
                                    Throwable failure,
                                    long costTime) {
        try {
            HttpServletRequest request = currentRequest();
            SysOperLogRecordRequest logRequest = new SysOperLogRecordRequest();
            logRequest.setTraceId(header(request, HEADER_TRACE_ID));
            logRequest.setRequestId(header(request, HEADER_REQUEST_ID));
            logRequest.setMerchantId(header(request, HEADER_MERCHANT_ID));
            logRequest.setModuleName(operation.moduleName());
            logRequest.setBusinessType(operation.businessType());
            logRequest.setMethodName(methodName(point));
            logRequest.setRequestMethod(request == null ? null : request.getMethod());
            logRequest.setOperatorType(resolveOperatorType(request, operation.operatorType()));
            logRequest.setOperatorId(header(request, HEADER_OPERATOR_ID));
            logRequest.setOperatorName(header(request, HEADER_OPERATOR_NAME));
            logRequest.setOperUrl(request == null ? null : request.getRequestURI());
            logRequest.setOperIp(clientIp(request));
            logRequest.setRequestParam(operation.recordRequest() ? serializeForLog(point.getArgs()) : null);
            logRequest.setResponseResult(operation.recordResponse() ? serializeForLog(result) : null);
            logRequest.setCostTime(costTime);
            logRequest.setStatus(failure == null ? SUCCESS_STATUS : FAILED_STATUS);
            fillFailure(logRequest, failure);
            operLogService.recordOperLog(logRequest);
        } catch (Exception exception) {
            log.warn("管理后台操作日志写入失败，方法：{}，原因：{}", methodName(point), exception.getMessage());
        }
    }

    /**
     * 填充异常信息。
     *
     * @param logRequest 操作日志写入请求
     * @param failure    原方法异常
     */
    private void fillFailure(SysOperLogRecordRequest logRequest, Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof ServiceException serviceException) {
            logRequest.setErrorCode(serviceException.getCode());
            logRequest.setErrorMsg(truncate(serviceException.getMessage()));
            return;
        }
        logRequest.setErrorCode(failure.getClass().getSimpleName());
        logRequest.setErrorMsg(truncate(failure.getMessage()));
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
     * 解析操作人类型，优先使用请求头，缺失或非法时使用注解默认值。
     *
     * @param request         请求对象
     * @param defaultOperator 默认操作人类型
     * @return 操作人类型
     */
    private Integer resolveOperatorType(HttpServletRequest request, int defaultOperator) {
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
        return truncate(SensitiveDataMaskUtils.maskJson(json));
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
