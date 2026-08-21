package com.scott.payment.component.core.security;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalRequestReplayGuard
 * @date : 2026-08-20 23:50
 * @email : scott_x@163.com
 * @description : 内部服务请求 nonce 防重放契约，由 Redis 原子实现保证多实例间同一调用方和 nonce 只能成功一次
 * @status : create
 */
public interface InternalRequestReplayGuard {

    /**
     * 原子占用内部请求 nonce。
     *
     * @param caller 已通过 HMAC 验证的调用方服务标识
     * @param nonce 已通过格式校验的请求随机串
     * @param ttl 防重放记录有效期
     * @return true 表示首次占用，false 表示重复请求
     */
    boolean tryAcquire(String caller, String nonce, Duration ttl);
}
