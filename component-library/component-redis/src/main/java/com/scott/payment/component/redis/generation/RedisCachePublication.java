package com.scott.payment.component.redis.generation;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisCachePublication
 * @date : 2026-07-30 11:13
 * @email : scott_x@163.com
 * @description : 单次缓存代际发布凭证，token 用于防止非持有者提交或释放发布门禁
 * @status : create
 *
 * @param namespace 受控缓存命名空间
 * @param token 发布门禁持有者 token
 * @param generation 待切换的新代际
 */
public record RedisCachePublication(String namespace, String token, String generation) {
}
