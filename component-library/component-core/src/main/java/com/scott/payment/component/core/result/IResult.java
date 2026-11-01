package com.scott.payment.component.core.result;

/**
 * 统一响应码接口
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
