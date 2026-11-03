package com.scott.payment.component.web.auth;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthChecker;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内部管理接口 Cookie 会话与 CSRF 拦截回归测试。
 */
class InternalAuthInterceptorTests {

    /**
     * 每个测试结束后清理认证线程上下文，避免测试之间共享登录态。
     */
    @AfterEach
    void clearAuthenticationContext() {
        InternalAuthContextHolder.clear();
    }

    /**
     * Cookie 会话可以认证只读请求，并按 Bearer 形式传递给既有鉴权器。
     *
     * @throws Exception 拦截器写响应失败时抛出
     */
    @Test
    void shouldAuthenticateCookieSessionForSafeRequest() throws Exception {
        AtomicReference<String> checkedAuthorization = new AtomicReference<>();
        InternalAuthInterceptor interceptor = interceptor((appCode, authorization, method, path, permission) -> {
            checkedAuthorization.set(authorization);
            return account();
        });
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/system/user/list");
        request.setCookies(new Cookie("VEXRA_ADMIN_SESSION", "cookie-session-token"));

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(checkedAuthorization.get()).isEqualTo("Bearer cookie-session-token");
    }

    /**
     * Cookie 会话写请求缺少匹配的 CSRF 请求头时必须在鉴权器之前拒绝。
     *
     * @throws Exception 拦截器写响应失败时抛出
     */
    @Test
    void shouldRejectCookieWriteWithoutMatchingCsrfToken() throws Exception {
        AtomicInteger authCheckCount = new AtomicInteger();
        InternalAuthInterceptor interceptor = interceptor((appCode, authorization, method, path, permission) -> {
            authCheckCount.incrementAndGet();
            return account();
        });
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/system/user");
        request.setCookies(
                new Cookie("VEXRA_ADMIN_SESSION", "cookie-session-token"),
                new Cookie("VEXRA_ADMIN_CSRF", "csrf-token")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("csrf token is invalid");
        assertThat(authCheckCount.get()).isZero();
    }

    /**
     * Cookie 会话写请求提交匹配的 CSRF Cookie 与请求头时可以继续鉴权。
     *
     * @throws Exception 拦截器写响应失败时抛出
     */
    @Test
    void shouldAuthenticateCookieWriteWithMatchingCsrfToken() throws Exception {
        AtomicReference<String> checkedMethod = new AtomicReference<>();
        InternalAuthInterceptor interceptor = interceptor((appCode, authorization, method, path, permission) -> {
            checkedMethod.set(method);
            return account();
        });
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/system/user");
        request.setCookies(
                new Cookie("VEXRA_ADMIN_SESSION", "cookie-session-token"),
                new Cookie("VEXRA_ADMIN_CSRF", "csrf-token")
        );
        request.addHeader(BrowserSessionCookieSupport.CSRF_HEADER, "csrf-token");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(checkedMethod.get()).isEqualTo("POST");
    }

    /**
     * 显式 Bearer 客户端保持原认证契约，写请求不依赖浏览器 CSRF Cookie。
     *
     * @throws Exception 拦截器写响应失败时抛出
     */
    @Test
    void shouldPreserveBearerAuthenticationForWriteRequest() throws Exception {
        AtomicReference<String> checkedAuthorization = new AtomicReference<>();
        InternalAuthInterceptor interceptor = interceptor((appCode, authorization, method, path, permission) -> {
            checkedAuthorization.set(authorization);
            return account();
        });
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/system/user");
        request.addHeader("Authorization", "Bearer api-client-token");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(checkedAuthorization.get()).isEqualTo("Bearer api-client-token");
    }

    /**
     * 创建使用 ADMIN 应用编码的被测拦截器。
     *
     * @param authChecker 认证与权限检查器
     * @return 被测拦截器
     */
    private InternalAuthInterceptor interceptor(InternalAuthChecker authChecker) {
        return new InternalAuthInterceptor("ADMIN", authChecker, List.of());
    }

    /**
     * 创建通过认证后的最小账号上下文。
     *
     * @return 内部账号上下文
     */
    private InternalAuthAccount account() {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAppCode("ADMIN");
        account.setAccountId(1L);
        return account;
    }
}
