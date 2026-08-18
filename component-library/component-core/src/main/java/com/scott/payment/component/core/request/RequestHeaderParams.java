package com.scott.payment.component.core.request;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author : scott
 * @version ：v1.0.0
 * @classname : RequestHeaderParams
 * @date : 2026-08-10 15:33
 * @email : scott_x@163.com
 * @description ：外部接口请求参数全信息读取
 * @status : create
 */
public class RequestHeaderParams {

    private RequestHeaderParams(){}

    /**
     * 获取请求中的所有 Header。
     *
     * @param request HTTP 请求
     * @return 请求 Header
     */
    public static Map<String, String> getRequestHeaders(HttpServletRequest request) {
        if (request == null) {
            return Collections.emptyMap();
        }

        Map<String, String> headers = new LinkedHashMap<>();

        Collections.list(request.getHeaderNames())
            .forEach(headerName -> {
                String headerValue = request.getHeader(headerName);
                headers.put(headerName, maskHeader(headerName, headerValue));
            });

        return headers;
    }

    /**
     * 获取请求中的所有请求参数。
     *
     * 支持：
     * 1. URL Query 参数
     * 2. application/x-www-form-urlencoded 参数
     * 3. 部分 Servlet 已解析的 Form 参数
     *
     * 注意：不会读取 application/json 请求 Body。
     *
     * @param request HTTP 请求
     * @return 请求参数
     */
    public static Map<String, Object> getRequestParameters(HttpServletRequest request) {
        if (request == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> parameters = new LinkedHashMap<>();

        request.getParameterMap().forEach((key, values) -> {
            if (values == null) {
                parameters.put(key, null);
            } else if (values.length == 1) {
                parameters.put(key, maskParameter(key, values[0]));
            } else {
                parameters.put(
                    key,
                    Arrays.stream(values)
                        .map(value -> maskParameter(key, value))
                        .toList()
                );
            }
        });

        return parameters;
    }

    /**
     * 获取请求基础信息，方便完整链路日志输出。
     *
     * @param request HTTP 请求
     * @return 请求信息
     */
    public static Map<String, Object> getRequestInfo(HttpServletRequest request) {
        if (request == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> requestInfo = new LinkedHashMap<>();

        requestInfo.put("method", request.getMethod());
        requestInfo.put("requestURI", request.getRequestURI());
        requestInfo.put("queryString", request.getQueryString());
        requestInfo.put("remoteAddr", getClientIp(request));
        requestInfo.put("contentType", request.getContentType());
        requestInfo.put("headers", getRequestHeaders(request));
        requestInfo.put("parameters", getRequestParameters(request));

        return requestInfo;
    }

    /**
     * 获取客户端 IP。
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    private static String getClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");

        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    /**
     * Header 敏感信息脱敏。
     */
    private static String maskHeader(String name, String value) {
        if (name == null || value == null) {
            return value;
        }

        return switch (name.toLowerCase()) {
            case "authorization",
                 "proxy-authorization",
                 "cookie",
                 "set-cookie",
                 "x-api-key",
                 "api-key" -> "******";
            default -> value;
        };
    }

    /**
     * 请求参数敏感信息脱敏。
     */
    private static Object maskParameter(String name, String value) {
        if (name == null || value == null) {
            return value;
        }

        return switch (name.toLowerCase()) {
            case "password",
                 "passwd",
                 "pwd",
                 "token",
                 "access_token",
                 "accesstoken",
                 "refresh_token",
                 "refreshtoken",
                 "secret",
                 "client_secret",
                 "clientsecret",
                 "privatekey",
                 "private_key",
                 "cvv",
                 "cvc",
                 "securitycode" -> "******";
            default -> value;
        };
    }

}
