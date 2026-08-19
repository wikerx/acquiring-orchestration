package com.scott.payment.merchant.service;

import com.scott.payment.merchant.entity.MerchantFinanceEntities.PendingBalanceAggregate;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantPendingBalanceQueryService
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 商户端在途余额只读边界，按认证商户号从交易副本实时聚合未结算成功资金动作。
 * @status : create
 */
public interface MerchantPendingBalanceQueryService {

    /**
     * 按标签币种汇总认证商户的在途资金净额。
     *
     * @param merchantId 认证商户号，不允许为空
     * @return 标签币种维度的在途净额；不同币种不会直接相加
     */
    List<PendingBalanceAggregate> sumPendingBalances(String merchantId);
}
