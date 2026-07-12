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
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiSecurityConfig
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Merchant Open Api Security 配置，位于 service-merchant 的配置层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(OpenApiMerchantKeyExportProperties.class)
public class MerchantOpenApiSecurityConfig {

    /**
     * 注册 OpenAPI 报文加密组件，保留给商户端需要解析或生成 OpenAPI 加密报文的场景。
     *
     * @return OpenAPI 报文加密组件
     */
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param exportProperties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param merchantInfoMapper 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param jwtKeyMapper 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param platformPayloadKeyMapper 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param responseKeyMapper 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param keyMaterialFactory 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param keyExportService 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param exportProperties 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
