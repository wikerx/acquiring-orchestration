package com.scott.payment.component.web.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 浏览器 Cookie 会话与 CSRF 防护回归测试。
 */
class BrowserSessionCookieSupportTests {

    /**
     * Cookie 模式登录不得在 JSON 响应中继续暴露会话令牌。
     */
    @Test
    void shouldMoveBrowserLoginTokenIntoHttpOnlyCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/auth/login");
        request.addHeader(BrowserSessionCookieSupport.AUTH_MODE_HEADER,
                BrowserSessionCookieSupport.COOKIE_AUTH_MODE);
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean issued = BrowserSessionCookieSupport.issueBrowserSession(
                "ADMIN", "opaque-login-token", 3600L, request, response);

        assertThat(issued).isTrue();
        List<String> cookies = response.getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(2);
        assertThat(cookies.get(0))
                .contains("VEXRA_ADMIN_SESSION=opaque-login-token")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax");
        assertThat(cookies.get(1))
                .contains("VEXRA_ADMIN_CSRF=")
                .doesNotContain("HttpOnly");
    }

    /**
     * 未声明 Cookie 模式的客户端继续接收原 Bearer 令牌。
     */
    @Test
    void shouldPreserveBearerContractForNonBrowserClients() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean issued = BrowserSessionCookieSupport.issueBrowserSession(
                "ADMIN", "opaque-login-token", 3600L, request, response);

        assertThat(issued).isFalse();
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }

    /**
     * Cookie 会话写请求必须同时提交匹配的 CSRF Cookie 与请求头。
     */
    @Test
    void shouldRejectCookieAuthenticatedWriteWithoutMatchingCsrfToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/system/user");
        request.setCookies(
                new jakarta.servlet.http.Cookie("VEXRA_ADMIN_SESSION", "opaque-login-token"),
                new jakarta.servlet.http.Cookie("VEXRA_ADMIN_CSRF", "csrf-token")
        );

        assertThat(BrowserSessionCookieSupport.isCsrfValid("ADMIN", request)).isFalse();

        request.addHeader(BrowserSessionCookieSupport.CSRF_HEADER, "csrf-token");

        assertThat(BrowserSessionCookieSupport.isCsrfValid("ADMIN", request)).isTrue();
        assertThat(BrowserSessionCookieSupport.resolveAuthorization("ADMIN", request))
                .isEqualTo("Bearer opaque-login-token");
    }

    /**
     * 显式 Bearer 调用保持原有防重放边界，不强制依赖浏览器 CSRF Cookie。
     */
    @Test
    void shouldNotRequireCsrfTokenForBearerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/system/user");
        request.addHeader("Authorization", "Bearer api-client-token");

        assertThat(BrowserSessionCookieSupport.isCsrfValid("ADMIN", request)).isTrue();
        assertThat(BrowserSessionCookieSupport.resolveAuthorization("ADMIN", request))
                .isEqualTo("Bearer api-client-token");
    }
}
