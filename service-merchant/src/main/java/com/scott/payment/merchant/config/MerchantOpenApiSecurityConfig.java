package com.scott.payment.merchant.config;

import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.openapi.OpenApiKeyAuditService;
import com.scott.payment.component.security.openapi.OpenApiKeyExportService;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyExportProperties;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialService;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.mapper.BasePlatformPayloadKeyMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商户系统 OpenAPI 密钥材料配置，提供当前商户接入材料展示、下载和轮换所需组件。
 */
@Configuration
@EnableConfigurationProperties(OpenApiMerchantKeyExportProperties.class)
public class MerchantOpenApiSecurityConfig {

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
     * @param exportProperties OpenAPI 商户接入材料导出配置
     * @return OpenAPI 接入材料导出服务
     */
    @Bean
    public OpenApiKeyExportService openApiKeyExportService(OpenApiMerchantKeyExportProperties exportProperties) {
        return new OpenApiKeyExportService(exportProperties);
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
     * @return OpenAPI 商户密钥材料服务
     */
    @Bean
    public OpenApiMerchantKeyMaterialService openApiMerchantKeyMaterialService(BaseMerchantInfoMapper merchantInfoMapper,
                                                                               BaseMerchantJwtKeyMapper jwtKeyMapper,
                                                                               BasePlatformPayloadKeyMapper platformPayloadKeyMapper,
                                                                               BaseMerchantResponseKeyMapper responseKeyMapper,
                                                                               OpenApiKeyMaterialFactory keyMaterialFactory,
                                                                               OpenApiKeyExportService keyExportService,
                                                                               OpenApiMerchantKeyExportProperties exportProperties) {
        return new OpenApiMerchantKeyMaterialService(merchantInfoMapper, jwtKeyMapper, platformPayloadKeyMapper,
                responseKeyMapper, keyMaterialFactory, keyExportService, exportProperties);
    }
}
