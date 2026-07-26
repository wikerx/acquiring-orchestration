package com.scott.payment.job.exchange.provider;

import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.RawRateItem;
import com.scott.payment.job.entity.exchange.ExchangeJobEntities.ExchangeRateSourceDO;

import java.util.List;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateProvider
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : Exchange Rate Provider 协作组件，位于 调度任务服务，封装 exchange汇率provider 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
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
