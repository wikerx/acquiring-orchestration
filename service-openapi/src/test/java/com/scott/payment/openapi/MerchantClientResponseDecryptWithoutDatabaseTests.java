package com.scott.payment.openapi;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.support.MerchantOpenApiTestSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantClientResponseDecryptWithoutDatabaseTests
 * @date : 2026-06-02 21:45
 * @email : scott_x@163.com
 * @description : 不连接数据库的商户响应解密测试，使用固定商户响应密钥模拟商户解密 OpenAPI 响应 data
 * @status : create
 */
@Slf4j
class MerchantClientResponseDecryptWithoutDatabaseTests {

    /**
     * 固定商户号，用于日志关联当前响应属于哪个商户。
     */
    private static final String MERCHANT_ID = "260001";

    /**
     * 固定商户响应公钥。
     * <p>
     * 平台保存该公钥，并使用它加密响应体 data；商户不需要把响应私钥上传给平台。
     */
    private static final String MERCHANT_RESPONSE_PUBLIC_KEY_X509_BASE64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzf7ZCQDSw42fwW3WhkTXx7mGKLbpBczW7GQx4au5DbvspTX807rwTKJYFYRSUIpps6J65EJu+gjhnuI1dSpMlnzCB+tIr3sYkCAJkfOXynzcaiEY5x8TvKWWbPc/SSwEX1qYeqwmJA05NYtOg5d5FbDjG/i/14Dm7SWT2T28F7dxqq1n+OVm4komwGraSjKUAFpVoOP7h+cqyN6DXXg5BnTtzV2nkSvOvU6zYg0/jiOt1iGV100UA0H211kcgSpZt/jhuxbfni7S88GbbLsV/JZT3ryxNprISgMN8BeeVbp+iAnu/hGAlhg7fPLVP+/ROxNFh7DxBGWBjH257rxoCwIDAQAB";

    /**
     * 固定商户响应私钥。
     * <p>
     * 商户本地保存该私钥，用于解密平台响应中的 data 字段；生产环境严禁把该私钥写入日志或上传平台。
     */
    private static final String MERCHANT_RESPONSE_PRIVATE_KEY_PKCS8_BASE64 = "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDN/tkJANLDjZ/BbdaGRNfHuYYotukFzNbsZDHhq7kNu+ylNfzTuvBMolgVhFJQimmzonrkQm76COGe4jV1KkyWfMIH60ivexiQIAmR85fKfNxqIRjnHxO8pZZs9z9JLARfWph6rCYkDTk1i06Dl3kVsOMb+L/XgObtJZPZPbwXt3GqrWf45WbiSibAatpKMpQAWlWg4/uH5yrI3oNdeDkGdO3NXaeRK869TrNiDT+OI63WIZXXTRQDQfbXWRyBKlm3+OG7Ft+eLtLzwZtsuxX8llPevLE2mshKAw3wF55Vun6ICe7+EYCWGDt88tU/79E7E0WHsPEEZYGMfbnuvGgLAgMBAAECggEAIoxk+yETuC93BTp2OcOvCvS/HvH6Z/oga7osMYSa/0Yu3NCOrDYUmk26BzXPlml4a+PKx6Cquy2lJYAb5iAngy++XRSldqTnDDkLUdqwcQn676PIaO7p4QBGl9Tp3MxQmWt42k4oAXDkUOohy2kqqiwmEulnx217jXd5cfxsIO/atGHXm9Zh0Yq4jCYCXYikCylnKBFnsq+dU6F/jWYr1gs76o5Jgg7WQcvg4W1odGHPWqoEtlUTAZg25/bYXjYTnF855DksD1vSqzIAnsKFVXgmONZlMdnaT0LH+Kl5QEoTFZD7SByWx557RuhoVhtu/P4EHBcKksGMO2loKvOA/QKBgQDxhnQoQdZDmBpvOYqNuIvDLm9up6tGn6ff6s8/QPPItj2L4svLdGVfTcAzg+8BnbDyO9SFifwHe0zeuwyflKjmQfRABddDffK+yzzeE019zwGJqNd2Jo6cZ2njvSM6M7hCzJ5EU84c6hZIv//Lis2KMbxi8hUbd6JqMMqmUAL5NwKBgQDaV0mWaR3vezlea2pC6fhL54haDb08gPISXru6x6/zs/UNKo5pSvDjDzkZlE2YQQEvXrD+02gBdsxzrIJUEhMsb+h3kIpysOVm1AoAd5MdFjiT07XtLzWdEFPYouEnr/JLNJzBYg2pAOhpjuwvTGafNIsoo/PJrSIkFmgINfthzQKBgQCj7F1p9UU3G0TVuHgRN++jySBYOfRFOpb1oqiGhc7vqsCa8JLgw18KD/si+6h7sEsoHPNgrwYfDdBeWxV2Oa9ol9rumQhBBnp6g/YLw44UlSq2A6I4znJ8NLPpnbULC49Dxxyjwz1g4n+9YJJ70vktkhQKE8O/oLLa38KqniNmgQKBgQCmG9AlORWIM0Qi/C9cdunqvVvzvw4f8K25og7Ke8715gvhl2W+3z/CTruPJU+fLJ09L5oSVD2FF59VxYFlelbR8NV32SQrOz9baqetUUs/zr7+YAvBRbBRLLHNV6VZ7zazVnSHfxSLZeBrJkuzdDmCl5PjOFBpN2mI8O72iDMWZQKBgQCllpyogwEYVF4TkDqK8cuRsehdfzFU/R0mQycy3kk2WI9ovpOcmeO9g3vFkd9woJmTXPH7EmB1f/Zr1+OPSgYBGShMXY6BsSKV3H9VOJ8F9hWH11/kDH7cUwwDShsDzUvE2oxnfTt5qQAuWftNb3MsbKperFD4NXo8ewWsbairAQ==";

    /**
     * OpenAPI 报文混合加密工具，响应加密和商户解密都使用同一套公共实现。
     */
    private final OpenApiPayloadCrypto payloadCrypto = new OpenApiPayloadCrypto();

    /**
     * 密钥材料工厂只用于计算日志指纹，避免输出完整密钥和密文。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory = new OpenApiKeyMaterialFactory();

    /**
     * 模拟商户拿到 OpenAPI 响应密文后，使用固定响应私钥解密 data 字段。
     */
    @Test
    void shouldDecryptOpenApiEncryptedResponseWithoutDatabase() {
        String plainResponseData = JsonUtils.toJsonString(Map.of(
                "merchantOrderNo", "202606020001",
                "platformReference", "PAY202606020001",
                "transactionStatus", "SUCCESS",
                "amount", 1238945,
                "currency", "USD"
        ));
        log.info("响应参数明文：{}" , plainResponseData);

        String encryptedResponseData = payloadCrypto.encrypt(
                plainResponseData,
                payloadCrypto.readPublicKey(MERCHANT_RESPONSE_PUBLIC_KEY_X509_BASE64)
        );
        log.info("响应参数密文：{}" , encryptedResponseData);

        String encryptedHttpResponse = JsonUtils.toJsonString(Map.of(
                "code", ApiCoResultEnum.SUCCESS.getCode(),
                "message", ApiCoResultEnum.SUCCESS.getMessage(),
                "data", encryptedResponseData
        ));
        log.info("encryptedHttpResponse：{}" , encryptedHttpResponse);

        Map<String, Object> responseMap = JsonUtils.parseObject(encryptedHttpResponse, new TypeReference<>() {
        });
        log.info("responseMap：{}" , JSON.toJSONString(responseMap));

        String decryptedData = payloadCrypto.decrypt(
                String.valueOf(responseMap.get("data")),
                payloadCrypto.readPrivateKey(MERCHANT_RESPONSE_PRIVATE_KEY_PKCS8_BASE64)
        );
        log.info("decryptedData：{}" , decryptedData);

        assertThat(responseMap.get("code")).isEqualTo(ApiCoResultEnum.SUCCESS.getCode());
        assertThat(MerchantOpenApiTestSupport.compactPartCount(encryptedResponseData)).isEqualTo(5);
        assertThat(decryptedData).isEqualTo(plainResponseData);
        log.info("平台响应加密摘要-商户号：{}，响应公钥指纹：{}，data摘要：{}",
                MERCHANT_ID,
                keyMaterialFactory.fingerprint(MERCHANT_RESPONSE_PUBLIC_KEY_X509_BASE64),
                MerchantOpenApiTestSupport.safeSecretSummary(encryptedResponseData, keyMaterialFactory));
        log.info("商户响应解密成功-响应私钥指纹：{}，解密后data：{}",
                keyMaterialFactory.fingerprint(MERCHANT_RESPONSE_PRIVATE_KEY_PKCS8_BASE64),
                decryptedData);
    }
}
