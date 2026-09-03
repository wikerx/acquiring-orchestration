package com.scott.payment.admin.config;

import com.scott.payment.admin.constant.SystemConfigKeys;
import com.scott.payment.admin.service.AdminConfigService;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOpenApiSecurityConfig
 * @date : 2026-06-13 17:17
 * @email : scott_x@163.com
 * @description : Admin Open API Security Config 配置类，位于 运营后台服务，注册当前模块运行所需 Bean、拦截器、客户端或配置属性。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(OpenApiMerchantKeyExportProperties.class)
public class AdminOpenApiSecurityConfig {

    /**
     * 注册 OpenAPI 密钥材料工厂，供后台商户管理场景生成与轮换密钥。
     *
     * @return 密钥材料工厂
     */
    @Bean
    public OpenApiKeyMaterialFactory openApiKeyMaterialFactory() {
        return new OpenApiKeyMaterialFactory();
    }

    /**
     * 注册 OpenAPI 报文加密组件，保留给后台内需要解析或生成 OpenAPI 加密报文的场景。
     *
     * @return OpenAPI 报文加密组件
     */
    @Bean
    public OpenApiPayloadCrypto openApiPayloadCrypto() {
        return new OpenApiPayloadCrypto();
    }

    /**
     * 注册 OpenAPI 接入材料导出服务，统一生成 TXT、PEM、properties 和 ZIP 文件。
     *
     * @param exportProperties OpenAPI 商户接入材料导出配置
     * @return OpenAPI 接入材料导出服务
     */
    @Bean
    public OpenApiKeyExportService openApiKeyExportService(OpenApiBaseUrlResolver openApiBaseUrlResolver) {
        return new OpenApiKeyExportService(openApiBaseUrlResolver);
    }

    /**
     * 注册管理端 OpenAPI 对外地址解析器。商户所有 OpenAPI 调用必须走 gateway，对外地址统一取系统参数。
     *
     * @param adminConfigService 系统参数服务
     * @return OpenAPI 对外地址解析器
     */
    @Bean
    public OpenApiBaseUrlResolver openApiBaseUrlResolver(AdminConfigService adminConfigService) {
        return () -> adminConfigService.enabledConfigValues(Set.of(SystemConfigKeys.GATEWAY_BASE_URL))
                .get(SystemConfigKeys.GATEWAY_BASE_URL);
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
     * 注册 OpenAPI 商户密钥材料服务，仅在管理端启用密钥查询、复制、下载和轮换能力。
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
                                                                               OpenApiMerchantKeyExportProperties exportProperties,
                                                                               OpenApiBaseUrlResolver openApiBaseUrlResolver) {
        return new OpenApiMerchantKeyMaterialService(merchantInfoMapper, jwtKeyMapper, platformPayloadKeyMapper,
                responseKeyMapper, keyMaterialFactory, keyExportService, exportProperties, openApiBaseUrlResolver);
    }
}
