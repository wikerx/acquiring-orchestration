package com.sinopay.payment.component.http;

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
public class HttpResponseResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private int status;
    private String body;

    public HttpResponseResult() {
    }

    public HttpResponseResult(int status, String body) {
        this.status = status;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
