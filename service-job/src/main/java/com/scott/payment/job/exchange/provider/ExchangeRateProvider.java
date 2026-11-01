package com.scott.payment.job.exchange.provider;

import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.RawRateItem;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateSourceDO;

import java.util.List;


/**
 * 外部汇率源适配器，按统一模型返回可审计的原始报价。
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
