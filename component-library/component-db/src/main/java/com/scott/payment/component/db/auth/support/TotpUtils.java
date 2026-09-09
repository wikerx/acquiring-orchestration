package com.scott.payment.component.db.auth.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TotpUtils
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : TOTP Utils 通用函数集合，位于 公共组件库，封装格式化、校验、脱敏、加密、编码或标准化逻辑，调用方以静态方法获取本地计算结果。
 * @status : create
 */
public final class TotpUtils {

    /**
     * Base32 字符表。
     */
    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    /**
     * 默认密钥字节长度。
     */
    private static final int SECRET_BYTES = 20;

    /**
     * 安全随机数生成器。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TotpUtils() {
    }

    /**
     * 生成 Google Authenticator 兼容的 Base32 密钥。
     *
     * @return Base32 密钥，不包含填充符
     */
    public static String generateBase32Secret() {
        byte[] secret = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(secret);
        return base32Encode(secret);
    }

    /**
     * 校验 6 位 TOTP 验证码，并返回命中的时间步。
     *
     * @param base32Secret Base32 密钥
     * @param code         用户输入验证码
     * @param now          当前时间
     * @param period       时间步长，单位秒
     * @param window       前后容忍窗口
     * @return 命中的时间步，未命中返回 null
     */
    public static Long verify(String base32Secret, String code, Instant now, int period, int window) {
        if (base32Secret == null || code == null || !code.matches("\\d{6}")) {
            return null;
        }
        byte[] secret = base32Decode(base32Secret);
        long currentStep = now.getEpochSecond() / period;
        for (long step = currentStep - window; step <= currentStep + window; step++) {
            if (step >= 0 && code.equals(generateCode(secret, step))) {
                return step;
            }
        }
        return null;
    }

    /**
     * 构建验证器扫码使用的 otpauth URI。
     *
     * @param issuer       发行方
     * @param accountLabel 账号标签
     * @param secret       Base32 密钥
     * @return otpauth URI
     */
    public static String buildOtpauthUri(String issuer, String accountLabel, String secret) {
        String label = urlEncode(issuer + ":" + accountLabel);
        return "otpauth://totp/" + label
                + "?secret=" + urlEncode(secret)
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    /**
     * 创建编码，完成必要校验后写入或委托下游服务处理。
     * @param secret 敏感认证或加密材料，只能在当前安全边界内使用，禁止明文日志和异常回显
     * @param step TOTP 时间步长序号，用于计算当前或相邻窗口的一次性验证码
     * @return 当前方法生成或规范化后的文本值
     */
    private static String generateCode(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "totp code can not be calculated");
        }
    }

    private static String base32Encode(byte[] bytes) {
        StringBuilder result = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte item : bytes) {
            buffer = (buffer << 8) | (item & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32_ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1F]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32_ALPHABET[(buffer << (5 - bitsLeft)) & 0x1F]);
        }
        return result.toString();
    }

    private static byte[] base32Decode(String value) {
        String normalized = value.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        int buffer = 0;
        int bitsLeft = 0;
        byte[] output = new byte[normalized.length() * 5 / 8 + 1];
        int count = 0;
        for (char ch : normalized.toCharArray()) {
            int digit = base32Digit(ch);
            buffer = (buffer << 5) | digit;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output[count++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return Arrays.copyOf(output, count);
    }

    private static int base32Digit(char ch) {
        for (int i = 0; i < BASE32_ALPHABET.length; i++) {
            if (BASE32_ALPHABET[i] == ch) {
                return i;
            }
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "totp secret is invalid");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
