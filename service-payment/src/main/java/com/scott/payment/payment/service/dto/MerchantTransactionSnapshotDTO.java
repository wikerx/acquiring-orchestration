package com.scott.payment.payment.service.dto;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionSnapshotDTO
 * @date : 2026-08-14 12:45
 * @email : scott_x@163.com
 * @description : 商户可见交易快照聚合模型，承载首次请求冻结的商品、账单、付款人和收货资料。
 * @status : create
 */
@Data
public class MerchantTransactionSnapshotDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private PaymentCreateCommandDTO.SubMerchantInfoDTO subMerchantInfo;
    private List<PaymentCreateCommandDTO.GoodsInfoDTO> goodsInfo = new ArrayList<>();
    private PaymentCreateCommandDTO.BillingCardHolderInfoDTO billingCardHolderInfo;
    private PaymentCreateCommandDTO.PayerInfoDTO payerInfo;
    private PaymentCreateCommandDTO.ShippingInfoDTO shippingInfo;
}
