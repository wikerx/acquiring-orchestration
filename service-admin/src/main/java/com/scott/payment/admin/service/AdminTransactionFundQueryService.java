package com.scott.payment.admin.service;

import com.scott.payment.admin.entity.fund.FundAccountEntities.PendingBalanceAggregate;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionFundQueryService
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 管理端交易资金只读查询边界，基于交易副本实时计算在途余额并判断商户是否产生过资金类交易。
 * @status : create
 */
public interface AdminTransactionFundQueryService {

    /**
     * 按标签币种汇总指定商户尚未结算的成功资金动作净额。
     *
     * @param merchantId 商户号，不允许为空
     * @return 标签币种维度的在途净额；不同币种不会直接相加
     */
    List<PendingBalanceAggregate> sumPendingBalances(String merchantId);

    /**
     * 判断商户是否已经产生成功的正向或逆向资金动作。
     *
     * @param merchantId 商户号，不允许为空
     * @return 已产生资金类交易时返回 true
     */
    boolean hasSuccessfulFundTransaction(String merchantId);
}
