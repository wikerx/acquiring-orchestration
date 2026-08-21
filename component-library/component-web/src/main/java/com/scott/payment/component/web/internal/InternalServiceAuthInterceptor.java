package com.scott.payment.component.web.internal;

import com.alibaba.fastjson2.JSON;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalServiceAuthInterceptor
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 内部服务接口签名拦截器，为 /internal/** 接口提供服务间 HMAC 鉴权边界。
 * @status : create
 */
public class InternalServiceAuthInterceptor implements HandlerInterceptor {

    /** 调用方服务标识允许的格式。 */
    private static final Pattern CALLER_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    /** nonce 允许的格式和长度。 */
    private static final Pattern NONCE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{16,128}");

    /**
     * PATH MATCHER，表示接口路径、资源路径或路由匹配路径。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 内部服务签名配置。
     */
    private final InternalServiceAuthProperties properties;

    /** 跨实例 nonce 防重放守卫。 */
    private final InternalRequestReplayGuard replayGuard;

    /**
     * 创建内部服务签名拦截器。
     *
     * @param properties 内部服务签名配置
     * @param replayGuard Redis nonce 防重放守卫
     */
    public InternalServiceAuthInterceptor(InternalServiceAuthProperties properties,
                                          InternalRequestReplayGuard replayGuard) {
        this.properties = properties;
        this.replayGuard = replayGuard;
    }

    /**
     * 请求进入内部接口前校验调用方、时间窗、随机串和签名。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  MVC 处理器
     * @return true 表示放行
     * @throws IOException 写错误响应失败
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!properties.isEnabled() || isWhitelisted(request.getRequestURI())) {
            return true;
        }
        String caller = request.getHeader(InternalServiceSignature.HEADER_CALLER);
        String timestampText = request.getHeader(InternalServiceSignature.HEADER_TIMESTAMP);
        String nonce = request.getHeader(InternalServiceSignature.HEADER_NONCE);
        String signature = request.getHeader(InternalServiceSignature.HEADER_SIGNATURE);
        if (!StringUtils.hasText(caller)
                || !StringUtils.hasText(timestampText)
                || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature)) {
            writeError(response, ApiResultEnum.UNAUTHORIZED.getCode(), "internal service signature headers are required");
            return false;
        }
        if (!CALLER_PATTERN.matcher(caller).matches() || !NONCE_PATTERN.matcher(nonce).matches()) {
            writeError(response, ApiResultEnum.UNAUTHORIZED.getCode(), "internal service signature identity is invalid");
            return false;
        }
        long timestamp = parseTimestamp(timestampText, response);
        if (timestamp < 0) {
            return false;
        }
        if (isExpired(timestamp)) {
            writeError(response, ApiResultEnum.UNAUTHORIZED.getCode(), "internal service signature timestamp is expired");
            return false;
        }
        Object payloadDigestAttribute = request.getAttribute(InternalServiceRequestBodyFilter.BODY_SHA256_ATTRIBUTE);
        if (!(payloadDigestAttribute instanceof String payloadSha256) || !StringUtils.hasText(payloadSha256)) {
            writeError(response, ApiResultEnum.UNAUTHORIZED.getCode(), "internal service payload digest is required");
            return false;
        }
        String expectedSignature = InternalServiceSignature.sign(
                request.getMethod(),
                InternalServiceSignature.requestTarget(request.getRequestURI(), request.getQueryString()),
                timestamp,
                nonce,
                caller,
                payloadSha256,
                properties.getSecret()
        );
        if (!InternalServiceSignature.matches(expectedSignature, signature)) {
            writeError(response, ApiResultEnum.UNAUTHORIZED.getCode(), "internal service signature is invalid");
            return false;
        }
        if (!replayGuard.tryAcquire(caller, nonce, effectiveNonceTtl())) {
            writeError(response, ApiResultEnum.UNAUTHORIZED.getCode(), "internal service request is replayed");
            return false;
        }
        return true;
    }

    /**
     * 判断服务间调用路径是否允许跳过签名验证。
     * <p>
     * 白名单属于认证绕过边界，只应包含健康检查等不读取或修改业务数据的端点。
     * </p>
     *
     * @param requestPath 当前请求路径
     * @return 命中任一配置规则时返回 {@code true}
     */
    private boolean isWhitelisted(String requestPath) {
        return properties.getWhitelist().stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath));
    }

    /**
     * 按允许时钟偏差判断服务签名时间戳是否过期。
     *
     * @param timestamp 调用方发送的 Unix 毫秒时间戳
     * @return 与当前时间差超过配置窗口时返回 {@code true}
     */
    private boolean isExpired(long timestamp) {
        long skewMillis = requirePositiveDuration(
                properties.getAllowedClockSkew(), "internal service allowed clock skew").toMillis();
        return Math.abs(InternalServiceSignature.currentTimeMillis() - timestamp) > skewMillis;
    }

    /**
     * 计算安全的 nonce 占用时间，至少覆盖时间戳从窗口最前端到最末端的完整可重放区间。
     *
     * @return 不小于两倍允许时钟偏差的有效 TTL
     */
    private Duration effectiveNonceTtl() {
        Duration allowedClockSkew = requirePositiveDuration(
                properties.getAllowedClockSkew(), "internal service allowed clock skew");
        Duration configuredNonceTtl = requirePositiveDuration(
                properties.getNonceTtl(), "internal service nonce ttl");
        Duration minimumNonceTtl;
        try {
            minimumNonceTtl = allowedClockSkew.multipliedBy(2);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("internal service allowed clock skew is too large", exception);
        }
        return configuredNonceTtl.compareTo(minimumNonceTtl) >= 0
                ? configuredNonceTtl : minimumNonceTtl;
    }

    /** 校验内部认证时间配置，禁止零值、负值或缺失配置削弱认证边界。 */
    private Duration requirePositiveDuration(Duration value, String propertyName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(propertyName + " must be positive");
        }
        return value;
    }

    /**
     * 解析parsetimestamp，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 公共组件库 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param timestampText 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @return 构造、转换或解析后的业务值
     */
    private long parseTimestamp(String timestampText, HttpServletResponse response) throws IOException {
        try {
            return Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            writeError(response, ApiResultEnum.UNAUTHORIZED.getCode(), "internal service signature timestamp is invalid");
            return -1;
        }
    }

    /**
     * 整理write错误，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void writeError(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(CommonResult.error(code, message)));
    }
}
