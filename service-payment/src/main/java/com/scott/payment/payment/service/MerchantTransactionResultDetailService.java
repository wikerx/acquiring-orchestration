package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.MerchantTransactionResultDetailDTO;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionResultDetailService
 * @date : 2026-08-14 13:45
 * @email : scott_x@163.com
 * @description : 聚合商户响应所需的认证安全结果和真实财务事实，禁止从缺失记录推导结算字段。
 * @status : create
 */
public interface MerchantTransactionResultDetailService {

    /** 按交易分片键读取响应详情。 */
    MerchantTransactionResultDetailDTO load(String transactionId, LocalDateTime transactionDateTime);
}
