package com.scott.payment.channel.payment.worldpay.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 保存可安全记录的 Worldpay 报文元数据，不暴露原始支付数据。
 *
 * @param length UTF-8 报文字节数
 * @param digest 不可逆的 SHA-256 摘要
 */
public record PayloadLogMetadata(int length, String digest) {

    /**
     * 根据报文计算长度和不可逆摘要。
     *
     * @param payload 原始报文，仅在内存中参与摘要计算
     * @return 可安全写入应用日志的报文元数据
     */
    public static PayloadLogMetadata from(String payload) {
        byte[] bytes = (payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return new PayloadLogMetadata(bytes.length, "sha256:" + HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException exception) {
            return new PayloadLogMetadata(bytes.length, "sha256_unavailable");
        }
    }
}
