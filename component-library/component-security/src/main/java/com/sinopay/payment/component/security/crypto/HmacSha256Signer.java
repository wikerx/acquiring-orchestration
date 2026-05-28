package com.sinopay.payment.component.security.crypto;

import java.util.Map;
import java.util.TreeMap;

public class HmacSha256Signer {

    public String sign(Map<String, String> parameters, String secret) {
        TreeMap<String, String> sortedParameters = new TreeMap<>(parameters);
        return Integer.toHexString((sortedParameters.toString() + secret).hashCode());
    }
}

