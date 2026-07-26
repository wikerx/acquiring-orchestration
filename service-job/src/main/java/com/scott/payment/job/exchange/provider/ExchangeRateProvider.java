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
 * @description : ExchangeRateProvider Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 调度任务服务层，输入输出边界由所在包和公开方法契约限定。
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
