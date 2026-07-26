package com.scott.payment.merchant.config;

import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.openapi.OpenApiKeyAuditService;
import com.scott.payment.component.security.openapi.OpenApiBaseUrlResolver;
import com.scott.payment.component.security.openapi.OpenApiKeyExportService;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyExportProperties;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialService;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.mapper.BasePlatformPayloadKeyMapper;
import com.scott.payment.merchant.service.MerchantConfigService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenApiMerchantKeyExportProperties.class)
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiSecurityConfig
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : MerchantOpenApiSecurityConfig Spring 配置类，用于注册当前模块所需 Bean、客户端和拦截器，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class MerchantOpenApiSecurityConfig {

    /**
     * 系统参数中维护的网关对外基础地址键名，商户导出的 OpenAPI 调用地址必须统一走 gateway。
     */
    private static final String GATEWAY_BASE_URL_CONFIG_KEY = "platform.gateway.base-url";

    /**
     * 注册 OpenAPI 报文加密组件，保留给商户端需要解析或生成 OpenAPI 加密报文的场景。
     *
     * @return OpenAPI 报文加密组件
     */
    @Bean
    public OpenApiPayloadCrypto openApiPayloadCrypto() {
        return new OpenApiPayloadCrypto();
    }

    /**
     * 注册 OpenAPI 密钥材料工厂，供商户侧展示密钥指纹。
     *
     * @return OpenAPI 密钥材料工厂
     */
    @Bean
    public OpenApiKeyMaterialFactory openApiKeyMaterialFactory() {
        return new OpenApiKeyMaterialFactory();
    }

    /**
     * 注册 OpenAPI 接入材料导出服务，统一生成 TXT、PEM、properties 和 ZIP 文件。
     *
     * @param openApiBaseUrlResolver 商户 OpenAPI 对外地址解析器
     * @return OpenAPI 接入材料导出服务
     */
    @Bean
    public OpenApiKeyExportService openApiKeyExportService(OpenApiBaseUrlResolver openApiBaseUrlResolver) {
        return new OpenApiKeyExportService(openApiBaseUrlResolver);
    }

    /**
     * 注册商户端 OpenAPI 对外地址解析器。地址统一从系统参数读取，不再由商户服务 yml 单独维护。
     *
     * @param merchantConfigService 商户端只读系统参数服务
     * @return OpenAPI 对外地址解析器
     */
    @Bean
    public OpenApiBaseUrlResolver openApiBaseUrlResolver(MerchantConfigService merchantConfigService) {
        return () -> merchantConfigService.enabledConfigValue(GATEWAY_BASE_URL_CONFIG_KEY).orElse(null);
    }

    /**
     * 注册 OpenAPI 密钥审计辅助服务，统一判断敏感材料范围。
     *
     * @return OpenAPI 密钥审计辅助服务
     */
    @Bean
    public OpenApiKeyAuditService openApiKeyAuditService() {
        return new OpenApiKeyAuditService();
    }

    /**
     * 注册商户端 OpenAPI 密钥材料服务，仅用于当前商户查看和下载自己的接入材料。
     *
     * @param merchantInfoMapper       商户基础资料 Mapper
     * @param jwtKeyMapper             商户 JWT 密钥 Mapper
     * @param platformPayloadKeyMapper 平台请求加密密钥 Mapper
     * @param responseKeyMapper        商户响应密钥 Mapper
     * @param keyMaterialFactory       OpenAPI 密钥材料工厂
     * @param keyExportService         OpenAPI 接入材料导出服务
     * @param exportProperties         OpenAPI 商户接入材料导出配置
     * @param openApiBaseUrlResolver   商户 OpenAPI 对外地址解析器
     * @return OpenAPI 商户密钥材料服务
     */
    @Bean
    public OpenApiMerchantKeyMaterialService openApiMerchantKeyMaterialService(BaseMerchantInfoMapper merchantInfoMapper,
                                                                               BaseMerchantJwtKeyMapper jwtKeyMapper,
                                                                               BasePlatformPayloadKeyMapper platformPayloadKeyMapper,
                                                                               BaseMerchantResponseKeyMapper responseKeyMapper,
                                                                               OpenApiKeyMaterialFactory keyMaterialFactory,
                                                                               OpenApiKeyExportService keyExportService,
                                                                               OpenApiMerchantKeyExportProperties exportProperties,
                                                                               OpenApiBaseUrlResolver openApiBaseUrlResolver) {
        return new OpenApiMerchantKeyMaterialService(merchantInfoMapper, jwtKeyMapper, platformPayloadKeyMapper,
                responseKeyMapper, keyMaterialFactory, keyExportService, exportProperties, openApiBaseUrlResolver);
    }
}
