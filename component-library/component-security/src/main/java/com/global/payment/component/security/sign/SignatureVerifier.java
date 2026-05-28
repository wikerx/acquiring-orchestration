package com.global.payment.component.security.sign;

import java.util.Map;

public interface SignatureVerifier {

    boolean verify(Map<String, String> parameters, String signature, String secret);
}

