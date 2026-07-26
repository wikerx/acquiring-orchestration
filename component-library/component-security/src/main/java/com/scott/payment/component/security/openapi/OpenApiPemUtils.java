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
 * @description : Open API Pem Utils 通用函数集合，位于 公共组件库，封装格式化、校验、脱敏、加密、编码或标准化逻辑，调用方以静态方法获取本地计算结果。
 * @status : create
 */
public final class OpenApiPemUtils {

    /**
     * PEM 正文每行固定 64 字符，兼容 OpenSSL、Java、PHP 和 Go 等常见运行时。
     */
    private static final int PEM_LINE_LENGTH = 64;

    /**
     * PUBLIC KEY BEGIN，用于保存 Open API Pem Utils 中与 public密钥begin 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";
    /**
     * PUBLIC KEY END，用于保存 Open API Pem Utils 中与 public密钥end 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String PUBLIC_KEY_END = "-----END PUBLIC KEY-----";
    /**
     * PRIVATE KEY BEGIN，用于保存 Open API Pem Utils 中与 private密钥begin 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";
    /**
     * PRIVATE KEY END，用于保存 Open API Pem Utils 中与 private密钥end 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；敏感安全字段，日志只允许记录长度、摘要或掩码。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 构造pem对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 公共组件库 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param begin begin 输入值，参与 begin 的查询、校验、转换、写入或日志摘要
     * @param end end 输入值，参与 end 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
