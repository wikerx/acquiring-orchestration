package com.scott.payment.component.security.openapi;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPemUtils
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : OpenApiPemUtils 通用能力封装，用于提供无状态的格式转换、校验或安全处理函数，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public final class OpenApiPemUtils {

    /**
     * PEM 正文每行固定 64 字符，兼容 OpenSSL、Java、PHP 和 Go 等常见运行时。
     */
    private static final int PEM_LINE_LENGTH = 64;

    /**
     * PUBLIC KEY BEGIN 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";
    /**
     * PUBLIC KEY END 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String PUBLIC_KEY_END = "-----END PUBLIC KEY-----";
    /**
     * PRIVATE KEY BEGIN 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    /**
     * PRIVATE KEY END 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";

    private OpenApiPemUtils() {
    }

    /**
     * 将 X.509 DER Base64 公钥转换成标准 PEM 文本。
     *
     * @param x509Base64 X.509 DER Base64 公钥，也允许传入已有 PEM 文本
     * @return PUBLIC KEY PEM 文本
     */
    public static String toPublicKeyPem(String x509Base64) {
        return toPem(x509Base64, PUBLIC_KEY_BEGIN, PUBLIC_KEY_END);
    }

    /**
     * 将 PKCS#8 DER Base64 私钥转换成标准 PEM 文本。
     *
     * @param pkcs8Base64 PKCS#8 DER Base64 私钥，也允许传入已有 PEM 文本
     * @return PRIVATE KEY PEM 文本
     */
    public static String toPrivateKeyPem(String pkcs8Base64) {
        return toPem(pkcs8Base64, PRIVATE_KEY_BEGIN, PRIVATE_KEY_END);
    }

    /**
     * 归一化 Base64 或 PEM 密钥文本，得到可用于 JCA 解析的 DER Base64 正文。
     *
     * @param pemOrBase64 PEM 或 Base64 密钥文本
     * @return 去掉 PEM 头尾和空白字符后的 Base64 文本
     */
    public static String normalizePem(String pemOrBase64) {
        if (!StringUtils.hasText(pemOrBase64)) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "openapi key can not be blank");
        }
        return pemOrBase64
                .replace(PUBLIC_KEY_BEGIN, "")
                .replace(PUBLIC_KEY_END, "")
                .replace(PRIVATE_KEY_BEGIN, "")
                .replace(PRIVATE_KEY_END, "")
                .replaceAll("\\s", "");
    }

    /**
     * 计算密钥材料的 SHA-256 十六进制指纹，用于页面展示和审计比对。
     *
     * @param pemOrBase64 PEM 或 Base64 密钥文本
     * @return SHA-256 十六进制指纹
     */
    public static String sha256Fingerprint(String pemOrBase64) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalizePem(pemOrBase64).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "openapi key fingerprint can not be calculated");
        }
    }

    /**
     * 转换生成 to Pem 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @param begin begin 输入值，含义由调用方法名称和所属业务对象限定
     * @param end end 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private static String toPem(String value, String begin, String end) {
        String normalizedBase64 = normalizePem(value);
        StringBuilder builder = new StringBuilder(begin).append('\n');
        for (int index = 0; index < normalizedBase64.length(); index += PEM_LINE_LENGTH) {
            builder.append(normalizedBase64, index, Math.min(index + PEM_LINE_LENGTH, normalizedBase64.length())).append('\n');
        }
        return builder.append(end).toString();
    }
}
