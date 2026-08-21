package com.scott.payment.merchant.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.CurrentFeeResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.DetailQuery;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundAccountResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundLedgerResponse;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFinanceService
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端当前费率和资金账户只读服务契约，所有查询必须显式限定当前商户号。
 * @status : create
 */
public interface MerchantFinanceService {
    /**
     * 查询指定商户当前已生效费用配置，不返回平台模板库来源信息。
     *
     * @param merchantId 认证商户号
     * @return 当前生效费用配置；未配置时返回 null
     */
    CurrentFeeResponse getCurrentFee(String merchantId);
    /**
     * 查询指定商户资金账户，并实时汇总在途余额和保证金余额。
     *
     * @param merchantId 认证商户号
     * @return 单结算币种账户和只读能力快照
     */
    FundAccountResponse getFundAccount(String merchantId);
    /**
     * 分页查询指定商户不可变余额流水。
     *
     * @param merchantId 认证商户号
     * @param query 流水筛选和分页条件
     * @return 当前商户余额流水分页结果
     */
    PageResult<FundLedgerResponse> pageLedgers(String merchantId, DetailQuery query);
}
