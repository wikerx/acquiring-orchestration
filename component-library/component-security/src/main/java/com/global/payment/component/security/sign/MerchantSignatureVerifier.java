package com.global.payment.component.security.sign;

import com.global.payment.component.security.crypto.HmacSha256Signer;

import java.util.Map;

public class MerchantSignatureVerifier implements SignatureVerifier {

    private final HmacSha256Signer signer = new HmacSha256Signer();

    @Override
    public boolean verify(Map<String, String> parameters, String signature, String secret) {
        return signer.sign(parameters, secret).equals(signature);
    }
}

