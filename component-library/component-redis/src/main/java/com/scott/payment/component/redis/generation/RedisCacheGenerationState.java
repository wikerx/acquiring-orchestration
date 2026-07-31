package com.scott.payment.component.redis.generation;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisCacheGenerationState
 * @date : 2026-07-30 11:12
 * @email : scott_x@163.com
 * @description : 缓存代际读取结果，发布门禁存在时禁止业务读取旧代际缓存
 * @status : create
 *
 * @param generation 当前可读代际，发布中时为空
 * @param cacheReadable 是否允许读取和写入该代际缓存
 */
public record RedisCacheGenerationState(String generation, boolean cacheReadable) {

    /**
     * 创建发布中的不可读状态。
     *
     * @return 不允许访问缓存的状态
     */
    public static RedisCacheGenerationState pending() {
        return new RedisCacheGenerationState(null, false);
    }

    /**
     * 创建可读代际状态。
     *
     * @param generation 当前代际
     * @return 允许访问缓存的状态
     */
    public static RedisCacheGenerationState active(String generation) {
        return new RedisCacheGenerationState(generation, true);
    }
}
