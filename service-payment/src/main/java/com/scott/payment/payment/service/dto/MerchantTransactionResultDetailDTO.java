package com.scott.payment.payment.service.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionResultDetailDTO
 * @date : 2026-08-14 13:45
 * @email : scott_x@163.com
 * @description : 商户响应所需的平台生成详情，只包含 3DS 安全子集和已落库财务结果。
 * @status : create
 */
@Data
public class MerchantTransactionResultDetailDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private ThreeDsInfoDTO threeDsInfo;
    private BigDecimal settlementRate;
    private BigDecimal settlementAmount;
    private String settlementCurrency;
    private BigDecimal settlementFeeAmount;
    private List<FeeItemDTO> feeItems = new ArrayList<>();

    /** 允许商户接收的 3DS 安全结果，不含 CAVV 和协议原文。 */
    @Data
    public static class ThreeDsInfoDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private String eci;
        private String dsTransactionId;
        private String threeDsVersion;
        private String status;
        private Boolean liabilityShifted;
    }

    /** 已形成的费用明细。 */
    @Data
    public static class FeeItemDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private String categories;
        private BigDecimal amount;
        private String currency;
        private BigDecimal rate;
    }
}
