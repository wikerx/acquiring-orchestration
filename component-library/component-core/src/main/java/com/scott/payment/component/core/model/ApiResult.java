package com.scott.payment.component.core.model;

import com.scott.payment.component.core.constant.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiResult
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 轻量接口响应模型，仅用于健康检查、简单 ACK 等基础接口；业务接口优先使用 CommonResult 保留业务码语义。
 * @status : create
 */
@Data
public class ApiResult<T> implements Serializable {

    /**
     * 序列化版本号，用于保证接口响应对象在服务间传输或日志落库时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 接口响应码，用于表达调用结果的成功、失败或具体错误类型。
     */
    private String code;

    /**
     * 接口响应描述，面向调用方说明当前响应结果。
     */
    private String message;

    /**
     * 接口响应数据，成功时承载具体业务返回值，失败时通常为空。
     */
    private T data;

    /**
     * 构建成功响应。
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     * @return 成功响应
     */
    public static <T> ApiResult<T> success(T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(ErrorCode.SUCCESS);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    /**
     * 构建失败响应。
     *
     * @param code    错误码
     * @param message 错误说明
     * @param <T>     响应数据类型
     * @return 失败响应
     */
    public static <T> ApiResult<T> fail(String code, String message) {
        ApiResult<T> result = new ApiResult<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
