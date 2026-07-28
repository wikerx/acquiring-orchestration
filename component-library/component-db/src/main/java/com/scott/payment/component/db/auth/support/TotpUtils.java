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
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param secret secret 输入值，参与 secret 的查询、校验、转换、写入或日志摘要
     * @param step step 输入值，参与 step 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
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

    /**
     * 整理基础32编码，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param bytes bytes 输入值，参与 bytes 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 整理基础32解码，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 整理基础32digit，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param ch ch 输入值，参与 ch 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private static int base32Digit(char ch) {
        for (int i = 0; i < BASE32_ALPHABET.length; i++) {
            if (BASE32_ALPHABET[i] == ch) {
                return i;
            }
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "totp secret is invalid");
    }

    /**
     * 整理url编码，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 公共组件库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
