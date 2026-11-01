package com.scott.payment.payment.service.dto;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 商户可见交易快照聚合模型，承载首次请求冻结的商品、账单、付款人和收货资料。
 */
@Data
public class MerchantTransactionSnapshotDTO implements Serializable {

    private static final long serialVersionUID = 1L;


    private PaymentCreateCommandDTO.SubMerchantInfoDTO subMerchantInfo;
    /**
     * {@code goodsInfo}集合，承载 {@code MerchantTransactionSnapshotDTO} 当前请求或响应中的多值数据。
     */
    private List<PaymentCreateCommandDTO.GoodsInfoDTO> goodsInfo = new ArrayList<>();

    private PaymentCreateCommandDTO.BillingCardHolderInfoDTO billingCardHolderInfo;

    private PaymentCreateCommandDTO.PayerInfoDTO payerInfo;

    private PaymentCreateCommandDTO.ShippingInfoDTO shippingInfo;
}
