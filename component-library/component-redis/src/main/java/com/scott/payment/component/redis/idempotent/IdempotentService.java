package com.scott.payment.component.redis.idempotent;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IdempotentService
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 幂等控制服务接口
 * @status : create
 */
public interface IdempotentService {

    /**
     * 获取幂等处理权。
     * <p>
     * 返回 true 表示当前请求第一次进入，可继续处理；返回 false 表示相同业务键仍在有效期内，应直接拦截或返回已处理结果。
     *
     * @param idempotentKey 幂等业务键
     * @param ttlSeconds    幂等有效期，单位秒
     * @return 是否获取成功
     */
    boolean acquire(String idempotentKey, long ttlSeconds);
}
