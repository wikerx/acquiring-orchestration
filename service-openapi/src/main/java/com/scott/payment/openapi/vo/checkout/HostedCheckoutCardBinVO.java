package com.scott.payment.openapi.vo.checkout;

import lombok.Data;

import java.io.Serializable;

/**
 * 付款页卡 BIN 品牌识别结果。
 */
@Data
public class HostedCheckoutCardBinVO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
     */
    private String cardBrand;

    private Boolean recognized;

    private Boolean supported;
}
