package com.sinopay.payment.component.core.result;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IResult
 * @date : 2026-05-28 18:16
 * @email : scott_x@163.com
 * @description : 统一响应码接口
 * @status : create
 */
public interface IResult {

    /**
     * 获取业务响应码。
     *
     * @return 业务响应码
     */
    String getCode();

    /**
     * 获取业务响应消息。
     *
     * @return 业务响应消息
     */
    String getMessage();
}
