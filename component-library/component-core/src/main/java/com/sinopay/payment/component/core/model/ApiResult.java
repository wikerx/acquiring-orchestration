package com.sinopay.payment.component.core.model;

import com.sinopay.payment.component.core.constant.ErrorCode;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiResult
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 统一接口响应结果模型
 * @status : create
 */
public class ApiResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private String code;
    private String message;
    private T data;

    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(ErrorCode.SUCCESS);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> ApiResult<T> fail(String code, String message) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
