package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisServerTimeProvider
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : RedisServerTimeProvider Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class RedisServerTimeProvider {

    /**
     * Spring 字符串 Redis 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建 Redis Server Time 提供器。
     *
     * @param stringRedisTemplate Spring 字符串 Redis 模板
     */
    public RedisServerTimeProvider(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 获取 Redis 服务端毫秒时间。
     *
     * @return Redis 服务端 epochMillis
     */
    public long currentTimeMillis() {
        try {
            Long currentMillis = stringRedisTemplate.execute(
                    (RedisCallback<Long>) connection -> connection.serverCommands().time(TimeUnit.MILLISECONDS)
            );
            if (currentMillis == null || currentMillis <= 0L) {
                throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "Redis TIME 获取失败");
            }
            return currentMillis;
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "Redis TIME 获取失败", exception);
        }
    }
}
