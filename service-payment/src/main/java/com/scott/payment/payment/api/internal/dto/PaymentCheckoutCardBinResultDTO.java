package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;

/** 收银台 BIN 品牌识别结果。 */
@Data
public class PaymentCheckoutCardBinResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    private String cardBrand;
    private Boolean recognized;
    private Boolean supported;
}
