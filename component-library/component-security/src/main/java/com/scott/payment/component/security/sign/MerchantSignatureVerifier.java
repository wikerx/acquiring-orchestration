package com.scott.payment.component.security.sign;

import com.scott.payment.component.security.crypto.HmacSha256Signer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSignatureVerifier
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 商户普通参数签名验签实现，OpenAPI 支付接口默认不使用该类做 JWT 鉴权
 * @status : create
 */
public class MerchantSignatureVerifier implements SignatureVerifier {

    /**
     * HMAC-SHA256 签名器，负责生成期望签名，当前类只负责常量时间比较和验签编排。
     * <p>
     * OpenAPI 支付接口已经切换为标准 JWT HS256；该字段仅服务旧式参数签名场景。
     */
    private final HmacSha256Signer signer = new HmacSha256Signer();

    /**
     * 校验旧式参数签名，使用常量时间比较降低签名猜测侧信道风险。
     *
     * @param parameters 待签名参数
     * @param signature  调用方传入的签名
     * @param secret     签名密钥
     * @return true 表示签名一致
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param Map<String 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param parameters 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param signature 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param secret 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public boolean verify(Map<String, String> parameters, String signature, String secret) {
        if (signature == null || secret == null) {
            return false;
        }
        String expectedSignature = signer.sign(parameters, secret);
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
