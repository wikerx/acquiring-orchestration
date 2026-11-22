package com.scott.payment.channel.payment.dto.request;

import java.util.regex.Pattern;

/** 已持久化的渠道请求编号约定。 */
public final class ChannelRequestReference {
    private static final Pattern PREPARED_REQUEST_ID = Pattern.compile("CR[0-9]{21}");

    private ChannelRequestReference() {
    }

    public static boolean isPrepared(String value) {
        return value != null && PREPARED_REQUEST_ID.matcher(value).matches();
    }
}
