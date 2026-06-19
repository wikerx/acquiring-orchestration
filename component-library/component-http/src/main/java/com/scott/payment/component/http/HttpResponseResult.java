package com.scott.payment.component.http;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : HttpResponseResult
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : HTTP 响应结果模型
 * @status : create
 */
@Data
public class HttpResponseResult implements Serializable {

    /**
     * 序列化版本号，用于保证 HTTP 响应对象在日志、缓存或服务间传递时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * HTTP 状态码，例如 200、400、500，用于判断远程调用协议层是否成功。
     */
    private int status;

    /**
     * HTTP 响应体原文，调用方根据业务接口协议自行解析为 JSON、XML 或纯文本。
     */
    private String body;

    /**
     * 创建空的 HTTP 响应结果对象。
     */
    public HttpResponseResult() {
    }

    /**
     * 创建 HTTP 响应结果对象。
     *
     * @param status HTTP 状态码
     * @param body   响应体原文
     */
    public HttpResponseResult(int status, String body) {
        this.status = status;
        this.body = body;
    }
}
