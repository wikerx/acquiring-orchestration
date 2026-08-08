package com.scott.payment.openapi.vo.checkout;

import lombok.Data;

import java.io.Serializable;

/** 付款页卡 BIN 品牌识别结果。 */
@Data
public class HostedCheckoutCardBinVO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String cardBrand;
    private Boolean recognized;
    private Boolean supported;
}
