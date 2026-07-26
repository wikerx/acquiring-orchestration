package com.scott.payment.component.core.id;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LocalGlobalIdGenerator
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : LocalGlobalIdGenerator Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class LocalGlobalIdGenerator implements GlobalIdGenerator {

    /**
     * 最近一次生成编号使用的毫秒时间戳。
     */
    private long lastMillis;

    /**
     * 当前毫秒内递增序列。
     */
    private long sequence;

    /**
     * 生成全系统统一唯一标识。
     *
     * @return 22 位纯数字唯一标识
     */
    @Override
    public synchronized String nextId() {
        long effectiveMillis = resolveEffectiveMillis(System.currentTimeMillis());
        String id = buildId(effectiveMillis, sequence);
        if (!GlobalIdValidator.isValid(id)) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "生成的全局唯一标识格式非法");
        }
        return id;
    }

    /**
     * 根据当前 JVM 时间和本地序列计算本次编号使用的有效毫秒。
     *
     * @param currentMillis 当前 JVM 毫秒时间戳
     * @return 有效毫秒时间戳
     */
    private long resolveEffectiveMillis(long currentMillis) {
        if (currentMillis > lastMillis) {
            lastMillis = currentMillis;
            sequence = 1L;
            return lastMillis;
        }
        if (currentMillis < lastMillis) {
            sequence++;
        } else {
            sequence++;
        }
        if (sequence > GlobalIdConstants.DEFAULT_MAX_SEQUENCE) {
            waitNextMillis();
            lastMillis = Math.max(System.currentTimeMillis(), lastMillis + 1L);
            sequence = 1L;
        }
        return lastMillis;
    }

    /**
     * 短暂等待下一毫秒，避免本地毫秒内序列溢出后继续生成重复编号。
     */
    private void waitNextMillis() {
        try {
            TimeUnit.MILLISECONDS.sleep(1L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "全局唯一标识生成失败", exception);
        }
    }

    /**
     * 根据毫秒时间和序列构造完整编号。
     *
     * @param epochMillis 毫秒时间戳
     * @param currentSequence 当前毫秒内序列
     * @return 22 位纯数字编号
     */
    private String buildId(long epochMillis, long currentSequence) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochMillis),
                GlobalIdConstants.DEFAULT_ZONE_ID
        );
        String body = dateTime.format(GlobalIdConstants.TIME_FORMATTER)
                + String.format(Locale.ROOT, "%0" + GlobalIdConstants.SEQUENCE_LENGTH + "d", currentSequence);
        return body + LuhnMod10Utils.calculateCheckDigit(body);
    }
}
