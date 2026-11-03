package com.scott.payment.component.web.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

/**
 * 浏览器管理端会话 Cookie 与 CSRF 防护支持。
 *
 * <p>浏览器显式声明 Cookie 会话模式后，服务端将登录令牌写入 HttpOnly Cookie，
 * 并通过可读的随机 CSRF Cookie 与请求头执行双提交校验。未声明该模式的调用继续使用
 * 原有 Bearer 响应和请求头契约，供非浏览器客户端兼容使用。</p>
 */
public final class BrowserSessionCookieSupport {

    /**
     * 浏览器请求 Cookie 会话模式的请求头。
     */
    public static final String AUTH_MODE_HEADER = "X-Auth-Mode";

    /**
     * Cookie 会话模式请求头值。
     */
    public static final String COOKIE_AUTH_MODE = "cookie";

    /**
     * CSRF 双提交请求头。
     */
    public static final String CSRF_HEADER = "X-CSRF-Token";

    /**
     * Authorization 请求头。
     */
    private static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;

    /**
     * Bearer 令牌前缀。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 不要求 CSRF 校验的安全 HTTP 方法。
     */
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /**
     * CSRF 随机数生成器。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * CSRF 令牌随机字节数。
     */
    private static final int CSRF_TOKEN_BYTES = 32;

    private BrowserSessionCookieSupport() {
    }

    /**
     * 为浏览器登录响应写入会话与 CSRF Cookie，并从响应体移除会话令牌。
     *
     * @param appCode 认证应用编码
     * @param accessToken 会话令牌
     * @param expiresInSeconds 会话有效期秒数
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @return 已写入浏览器 Cookie 时返回 {@code true}
     */
    public static boolean issueBrowserSession(String appCode,
                                              String accessToken,
                                              Long expiresInSeconds,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        if (!isCookieMode(request)
                || !StringUtils.hasText(accessToken)) {
            return false;
        }
        Duration maxAge = expiresInSeconds == null || expiresInSeconds <= 0
                ? null
                : Duration.ofSeconds(expiresInSeconds);
        addCookie(response, sessionCookieName(appCode), accessToken, true, maxAge, request);
        addCookie(response, csrfCookieName(appCode), generateCsrfToken(), false, maxAge, request);
        return true;
    }

    /**
     * 解析认证凭据，优先使用显式 Bearer 请求头，其次读取对应应用的会话 Cookie。
     *
     * @param appCode 认证应用编码
     * @param request HTTP 请求
     * @return Bearer 认证值；不存在时返回 {@code null}
     */
    public static String resolveAuthorization(String appCode, HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authorization)) {
            return authorization;
        }
        String sessionToken = cookieValue(request, sessionCookieName(appCode));
        return StringUtils.hasText(sessionToken) ? BEARER_PREFIX + sessionToken : null;
    }

    /**
     * 校验 Cookie 会话写请求的 CSRF 双提交令牌。
     *
     * @param appCode 认证应用编码
     * @param request HTTP 请求
     * @return 校验通过或无需校验时返回 {@code true}
     */
    public static boolean isCsrfValid(String appCode, HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase())
                || StringUtils.hasText(request.getHeader(AUTHORIZATION_HEADER))) {
            return true;
        }
        String sessionToken = cookieValue(request, sessionCookieName(appCode));
        if (!StringUtils.hasText(sessionToken)) {
            return true;
        }
        String cookieToken = cookieValue(request, csrfCookieName(appCode));
        String headerToken = request.getHeader(CSRF_HEADER);
        if (!StringUtils.hasText(cookieToken) || !StringUtils.hasText(headerToken)) {
            return false;
        }
        return MessageDigest.isEqual(
                cookieToken.getBytes(StandardCharsets.UTF_8),
                headerToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 清理浏览器会话与 CSRF Cookie。
     *
     * @param appCode 认证应用编码
     * @param request HTTP 请求
     * @param response HTTP 响应
     */
    public static void clearBrowserSession(String appCode,
                                           HttpServletRequest request,
                                           HttpServletResponse response) {
        addCookie(response, sessionCookieName(appCode), "", true, Duration.ZERO, request);
        addCookie(response, csrfCookieName(appCode), "", false, Duration.ZERO, request);
    }

    /**
     * 返回指定应用的 CSRF Cookie 名称。
     *
     * @param appCode 认证应用编码
     * @return CSRF Cookie 名称
     */
    public static String csrfCookieName(String appCode) {
        return cookiePrefix(appCode) + "_CSRF";
    }

    /**
     * 判断请求是否声明使用浏览器 Cookie 会话。
     *
     * @param request HTTP 请求
     * @return Cookie 会话模式返回 {@code true}
     */
    private static boolean isCookieMode(HttpServletRequest request) {
        return COOKIE_AUTH_MODE.equalsIgnoreCase(request.getHeader(AUTH_MODE_HEADER));
    }

    /**
     * 返回指定应用的会话 Cookie 名称。
     *
     * @param appCode 认证应用编码
     * @return 会话 Cookie 名称
     */
    private static String sessionCookieName(String appCode) {
        return cookiePrefix(appCode) + "_SESSION";
    }

    /**
     * 将应用编码映射为固定 Cookie 前缀，避免同域部署时后台与商户会话互相覆盖。
     *
     * @param appCode 认证应用编码
     * @return Cookie 前缀
     */
    private static String cookiePrefix(String appCode) {
        if ("ADMIN".equals(appCode)) {
            return "VEXRA_ADMIN";
        }
        if ("MERCHANT".equals(appCode)) {
            return "VEXRA_MERCHANT";
        }
        throw new IllegalArgumentException("unsupported browser auth app code");
    }

    /**
     * 写入安全属性一致的会话 Cookie。
     *
     * @param response HTTP 响应
     * @param name Cookie 名称
     * @param value Cookie 值
     * @param httpOnly 是否禁止 JavaScript 读取
     * @param maxAge 有效期；{@code null} 表示浏览器会话期
     * @param request HTTP 请求
     */
    private static void addCookie(HttpServletResponse response,
                                  String name,
                                  String value,
                                  boolean httpOnly,
                                  Duration maxAge,
                                  HttpServletRequest request) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/");
        if (maxAge != null) {
            builder.maxAge(maxAge);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    /**
     * 判断当前请求是否经 HTTPS 到达浏览器侧入口。
     *
     * @param request HTTP 请求
     * @return HTTPS 请求返回 {@code true}
     */
    private static boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(forwardedProto);
    }

    /**
     * 读取指定 Cookie 值。
     *
     * @param request HTTP 请求
     * @param name Cookie 名称
     * @return Cookie 值；不存在时返回 {@code null}
     */
    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 生成不可预测的 CSRF 双提交令牌。
     *
     * @return URL 安全的随机令牌
     */
    private static String generateCsrfToken() {
        byte[] randomBytes = new byte[CSRF_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
