package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.openapi.dto.body.OpenApiEncryptedRequestDTO;
import com.scott.payment.openapi.dto.body.iso.IsoCountryQueryRequestDTO;
import com.scott.payment.openapi.dto.body.iso.IsoCurrencyQueryRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.OpenApiPayloadKeyProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPayloadDecoder
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : Open API Payload Decoder 解码组件，位于 商户开放接口服务，解析加密或外部协议报文，转换为内部 DTO 并保持异常边界清晰。
 * @status : create
 */
@Component
public class OpenApiPayloadDecoder {

    /** 国家查询对外开放的业务字段，其它字段必须拒绝，避免未知条件被忽略。 */
    private static final Set<String> COUNTRY_QUERY_FIELDS = Set.of("alpha2", "alpha3", "numeric");

    /** 币种查询对外开放的业务字段，其它字段必须拒绝，避免未知条件被忽略。 */
    private static final Set<String> CURRENCY_QUERY_FIELDS = Set.of("alphabeticCode", "numericCode");

    /**
     * OpenAPI 报文混合加密工具，负责解析 data compact 密文并执行 RSA-OAEP/AES-GCM 解密。
     */
    private final OpenApiPayloadCrypto payloadCrypto;

    /**
     * 平台 RSA 私钥提供器，生产环境通过 merchantId 从数据库、KMS 或 HSM 获取当前商户独立私钥。
     */
    private final OpenApiPayloadKeyProvider payloadKeyProvider;

    /**
     * 创建开放接口密文解码器。
     *
     * @param payloadCrypto       OpenAPI 报文加解密工具
     * @param payloadKeyProvider  平台私钥提供器
     */
    public OpenApiPayloadDecoder(OpenApiPayloadCrypto payloadCrypto, OpenApiPayloadKeyProvider payloadKeyProvider) {
        this.payloadCrypto = payloadCrypto;
        this.payloadKeyProvider = payloadKeyProvider;
    }

    /**
     * 解密并转换商户密文请求体。
     *
     * @param requestBody  商户原始请求体
     * @param dataReceiver 解密后接收 DTO 类型
     * @param headerDTO    已通过验证的请求头信息
     * @return 解密后的 DTO 对象
     */
    public Object decode(String requestBody, Class<?> dataReceiver, OpenApiRequestHeaderDTO headerDTO) {
        if (!StringUtils.hasText(requestBody)) {
            throw new ApiException(ApiResultEnum.PARAM_MISSING, "data");
        }
        String cipherText = extractCipherText(requestBody);
        String plainText = decrypt(cipherText, headerDTO);
        Object data = parsePlainText(plainText, dataReceiver);
        if (data == null) {
            throw new ApiException(ApiResultEnum.ENCRYPTED_DATA_INVALID);
        }
        return data;
    }

    /**
     * 从请求体中提取 data 密文，兼容只提交 compact 密文的本地测试形态。
     *
     * @param requestBody 商户提交的原始请求体
     * @return compact 密文
     */
    private String extractCipherText(String requestBody) {
        String trimmedBody = requestBody.trim();
        if (!trimmedBody.startsWith("{")) {
            return trimmedBody;
        }
        OpenApiEncryptedRequestDTO encryptedRequestDTO = JsonUtils.parseObject(trimmedBody, OpenApiEncryptedRequestDTO.class);
        if (encryptedRequestDTO != null && StringUtils.hasText(encryptedRequestDTO.getData())) {
            return encryptedRequestDTO.getData();
        }
        return trimmedBody;
    }

    /**
     * 将解密后的 JSON 明文转换为控制器声明的 DTO。
     * ISO 代码查询使用严格字段契约，解析失败按参数错误返回；其它接口保留既有错误码。
     *
     * @param plainText    解密后的业务 JSON 明文
     * @param dataReceiver 目标 DTO 类型
     * @return 业务 DTO
     */
    private Object parsePlainText(String plainText, Class<?> dataReceiver) {
        try {
            if (IsoCountryQueryRequestDTO.class.equals(dataReceiver)) {
                return parseCountryQuery(plainText);
            }
            if (IsoCurrencyQueryRequestDTO.class.equals(dataReceiver)) {
                return parseCurrencyQuery(plainText);
            }
            return JsonUtils.parseObject(plainText, dataReceiver);
        } catch (RuntimeException exception) {
            if (IsoCountryQueryRequestDTO.class.equals(dataReceiver)) {
                throw new ApiException(ApiResultEnum.PARAM_INVALID,
                        "country query only supports alpha2, alpha3 and numeric as ISO code strings");
            }
            if (IsoCurrencyQueryRequestDTO.class.equals(dataReceiver)) {
                throw new ApiException(ApiResultEnum.PARAM_INVALID,
                        "currency query only supports alphabeticCode and numericCode as ISO code strings");
            }
            throw new ApiException(ApiResultEnum.ENCRYPTED_DATA_INVALID);
        }
    }

    /**
     * 在国家查询 DTO 转换前检查字段集合和 JSON 类型，避免反序列化忽略条件或强制转换数字代码。
     *
     * @param plainText 已解密的国家查询 JSON
     * @return 只包含三个可选字符串代码的查询条件
     */
    private IsoCountryQueryRequestDTO parseCountryQuery(String plainText) {
        Object parsed = JsonUtils.parseObject(plainText, Object.class);
        if (!(parsed instanceof Map<?, ?> values)
                || !COUNTRY_QUERY_FIELDS.containsAll(values.keySet())
                || values.values().stream().anyMatch(value -> value != null && !(value instanceof String))) {
            throw new IllegalArgumentException("invalid country query fields or value types");
        }
        IsoCountryQueryRequestDTO request = new IsoCountryQueryRequestDTO();
        request.setAlpha2((String) values.get("alpha2"));
        request.setAlpha3((String) values.get("alpha3"));
        request.setNumeric((String) values.get("numeric"));
        return request;
    }

    /**
     * 在币种查询 DTO 转换前检查字段集合和 JSON 类型，避免未知条件被忽略或数字代码失去前导零。
     *
     * @param plainText 已解密的币种查询 JSON
     * @return 只包含两个可选字符串代码的查询条件
     */
    private IsoCurrencyQueryRequestDTO parseCurrencyQuery(String plainText) {
        Object parsed = JsonUtils.parseObject(plainText, Object.class);
        if (!(parsed instanceof Map<?, ?> values)
                || !CURRENCY_QUERY_FIELDS.containsAll(values.keySet())
                || values.values().stream().anyMatch(value -> value != null && !(value instanceof String))) {
            throw new IllegalArgumentException("invalid currency query fields or value types");
        }
        IsoCurrencyQueryRequestDTO request = new IsoCurrencyQueryRequestDTO();
        request.setAlphabeticCode((String) values.get("alphabeticCode"));
        request.setNumericCode((String) values.get("numericCode"));
        return request;
    }

    /**
     * 使用平台私钥解密商户 data 密文。
     * <p>
     * 当前 headerDTO 已经由拦截器完成 JWT 验签，这里再次检查 merchantId，避免绕过拦截器直接进入请求体解析流程。
     *
     * @param encryptedData 商户提交的 compact 密文
     * @param headerDTO     已验签的请求头上下文
     * @return 解密后的业务 JSON 明文
     */
    private String decrypt(String encryptedData, OpenApiRequestHeaderDTO headerDTO) {
        if (headerDTO == null || !StringUtils.hasText(headerDTO.getMerchantId())) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED);
        }
        return payloadCrypto.decrypt(encryptedData.trim(), payloadKeyProvider.getPlatformPrivateKey(headerDTO.getMerchantId()));
    }
}
