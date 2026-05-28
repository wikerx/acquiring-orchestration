package com.sinopay.payment.openapi.api.rest.v1.dto.body;

import java.io.Serializable;

public class PaymentCreateRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String merchantOrderNo;
    private String currency;
    private Long amount;

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String merchantOrderNo) {
        this.merchantOrderNo = merchantOrderNo;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Long getAmount() {
        return amount;
    }

    public void setAmount(Long amount) {
        this.amount = amount;
    }
}

