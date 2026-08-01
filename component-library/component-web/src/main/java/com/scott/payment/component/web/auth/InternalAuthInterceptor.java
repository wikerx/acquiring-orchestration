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
     * app Code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final String appCode;
    /**
     * auth Checker，用于保存 Internal Auth Interceptor 中与 authchecker 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final InternalAuthChecker authChecker;
    /**
     * whitelist Patterns，用于保存 Internal Auth Interceptor 中与 whitelistpatterns 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 判断内部管理接口路径是否配置为免认证。
     * <p>
     * 命中后将跳过 token 和权限校验，因此白名单只能配置健康检查等无业务数据入口。
     * </p>
     *
     * @param requestPath 当前请求路径
     * @return 命中任一 Ant 路径规则时返回 {@code true}
     */
    private boolean isWhitelisted(String requestPath) {
        return whitelistPatterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, requestPath));
    }

    /**
     * 解析处理器声明的权限编码，方法级声明优先于类级声明。
     *
     * @param handler Spring MVC 处理器
     * @return 权限编码；非控制器方法或未声明权限时返回 {@code null}
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
     * 整理write错误，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @param httpStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param result 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     */
    private void writeError(HttpServletResponse response, int httpStatus, ApiResultEnum result) throws IOException {
        writeError(response, httpStatus, result.getCode(), result.getMessage());
    }

    /**
     * 整理write错误，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @param httpStatus 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @param code 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    private void writeError(HttpServletResponse response, int httpStatus, String code, String message) throws IOException {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JSON.toJSONString(CommonResult.error(code, message)));
    }
}
