package com.scott.payment.component.security.replay;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NonceValidator
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 请求 Nonce 防重放校验接口
 * @status : create
 */
public interface NonceValidator {

    /**
     * 校验 nonce 的唯一性及请求时间窗口，阻止同一签名请求被重放。
     *
     * @param nonce 请求唯一随机值，不允许为空
     * @param timestamp 请求时间戳，单位由具体实现协议约定
     * @return nonce 未使用且时间戳有效时返回 true，否则返回 false
     */
    boolean validate(String nonce, long timestamp);
}
