package com.scott.payment.component.web.auth;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthChecker;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.method.HandlerMethod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalAuthInterceptor
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 内部管理类接口登录态与权限拦截器
 * @status : create
 */
public class InternalAuthInterceptor implements HandlerInterceptor {

    /**
     * Authorization 请求头名称。
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Bearer token 前缀。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 路径匹配器。
     */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * app Code 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final String appCode;
    /**
     * auth Checker 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final InternalAuthChecker authChecker;
    /**
     * whitelist Patterns 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final List<String> whitelistPatterns;

    /**
     * 创建内部管理接口鉴权拦截器。
     *
     * @param appCode           应用编码
     * @param authChecker       内部鉴权检查器
     * @param whitelistPatterns 白名单路径
     */
    public InternalAuthInterceptor(String appCode, InternalAuthChecker authChecker, List<String> whitelistPatterns) {
        this.appCode = appCode;
        this.authChecker = authChecker;
        this.whitelistPatterns = List.copyOf(whitelistPatterns);
    }

    /**
     * 请求进入控制器前校验登录态和权限。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @return true 表示放行
     * @throws IOException 写响应失败
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (isWhitelisted(request.getRequestURI())) {
            return true;
        }
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authorization)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ApiResultEnum.AUTHORIZATION_HEADER_MISSING);
            return false;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, ApiResultEnum.UNAUTHORIZED);
            return false;
        }
        try {
            InternalAuthAccount account = authChecker.check(
                    appCode,
                    authorization,
                    request.getMethod(),
                    request.getRequestURI(),
                    requiredPermission(handler)
            );
            InternalAuthContextHolder.set(account);
            return true;
        } catch (ServiceException exception) {
            int httpStatus = ApiResultEnum.FORBIDDEN.getCode().equals(exception.getCode())
                    ? HttpServletResponse.SC_FORBIDDEN
                    : HttpServletResponse.SC_UNAUTHORIZED;
            writeError(response, httpStatus, exception.getCode(), exception.getMessage());
            return false;
        }
    }

    /**
     * 请求完成后清理线程上下文。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  处理器
     * @param ex       请求异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        InternalAuthContextHolder.clear();
    }

    /**
     * 判断 is Whitelisted 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param requestPath request Path 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isWhitelisted(String requestPath) {
        return whitelistPatterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath));
    }

    /**
     * 强制校验 required Permission 必填值，缺失时中断当前业务流程。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param handler handler 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String requiredPermission(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return null;
        }
        RequiresPermission methodPermission = handlerMethod.getMethodAnnotation(RequiresPermission.class);
        if (methodPermission != null && StringUtils.hasText(methodPermission.value())) {
            return methodPermission.value();
        }
        RequiresPermission typePermission = handlerMethod.getBeanType().getAnnotation(RequiresPermission.class);
        return typePermission == null ? null : typePermission.value();
    }

    /**
     * 完成 write Error 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param response response 输入值，含义由调用方法名称和所属业务对象限定
     * @param httpStatus 状态编码，取值必须来自对应枚举或数据库受控字典
     * @param result result 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void writeError(HttpServletResponse response, int httpStatus, ApiResultEnum result) throws IOException {
        writeError(response, httpStatus, result.getCode(), result.getMessage());
    }

    /**
     * 完成 write Error 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param response response 输入值，含义由调用方法名称和所属业务对象限定
     * @param httpStatus 状态编码，取值必须来自对应枚举或数据库受控字典
     * @param code code 输入值，含义由调用方法名称和所属业务对象限定
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     */
    private void writeError(HttpServletResponse response, int httpStatus, String code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(CommonResult.error(code, message)));
    }
}
