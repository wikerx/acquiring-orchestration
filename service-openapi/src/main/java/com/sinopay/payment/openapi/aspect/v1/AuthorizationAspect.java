package com.sinopay.payment.openapi.aspect.v1;

import org.springframework.stereotype.Component;

@Component
public class AuthorizationAspect {

    public void verifyMerchantRequest() {
        // The real aspect will orchestrate signature, decrypt, merchant validation, and replay checks.
    }
}

