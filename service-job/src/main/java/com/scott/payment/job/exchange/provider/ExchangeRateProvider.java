package com.scott.payment.job.exchange.provider;

import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.RawRateItem;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateSourceDO;

import java.util.List;

/**
 * 汇率源拉取插件接口。
 *
 * <p>不同汇率源通过实现该接口转换为系统统一原始汇率结构，任务编排层不直接写死具体来源逻辑。</p>
 */
public interface ExchangeRateProvider {

    /**
     * 返回汇率源编码。
     *
     * @return 汇率源编码
     */
    String sourceCode();

    /**
     * 拉取汇率源原始报价。
     *
     * @param source 汇率源配置
     * @return 原始报价列表
     */
    List<RawRateItem> fetch(ExchangeRateSourceDO source);
}
