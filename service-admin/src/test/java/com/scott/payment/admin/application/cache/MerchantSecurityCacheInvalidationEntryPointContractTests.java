package com.scott.payment.admin.application.cache;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSecurityCacheInvalidationEntryPointContractTests
 * @date : 2026-07-30 22:30
 * @email : scott_x@163.com
 * @description : 商户完整资料、密钥元数据、路由和 OpenAPI IP 策略的可靠缓存失效入口契约测试
 * @status : update
 */
@Slf4j
class MerchantSecurityCacheInvalidationEntryPointContractTests {

    /** 商户主表写入口必须先调用完整资料缓存失效 helper。 */
    @Test
    void shouldPrepareInvalidationForEveryMerchantProfileMutation() throws IOException {
        log.info("测试商户资料失效入口，关键输入: 新增、编辑、启停和删除四类主表变更");
        String source = Files.readString(modulePath(
                "src/main/java/com/scott/payment/admin/service/impl/AdminMerchantInfoServiceImpl.java"
        ));

        List.of(
                "public AdminMerchantInfoDTO createMerchant(",
                "public AdminMerchantInfoDTO updateMerchant(",
                "public AdminMerchantInfoDTO updateStatus(",
                "public void deleteMerchant("
        ).forEach(signature -> assertPreparesInvalidation(
                source,
                signature,
                "prepareRuntimeProfileInvalidation("
        ));
        assertThat(source)
                .contains("private void prepareRuntimeProfileInvalidation(String merchantId)")
                .contains("cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, merchantId)");
        log.info("商户资料失效入口验证完成，结果: 四类主表变更均登记 merchant:info Outbox");
    }

    /** 密钥写入口必须先调用非敏感密钥元数据缓存失效 helper。 */
    @Test
    void shouldPrepareInvalidationForEveryMerchantKeyMutation() throws IOException {
        log.info("测试密钥元数据失效入口，关键输入: 初始化、三类轮换、响应密钥更新和商户删除");
        String source = Files.readString(modulePath(
                "src/main/java/com/scott/payment/admin/service/impl/AdminMerchantInfoServiceImpl.java"
        ));

        List.of(
                "public AdminMerchantSecurityMaterialDTO provisionSecurityMaterial(",
                "public AdminMerchantSecurityMaterialDTO rotateJwtKey(",
                "public AdminMerchantSecurityMaterialDTO rotatePlatformPayloadKey(",
                "public AdminMerchantSecurityMaterialDTO rotateMerchantResponseKey(",
                "public AdminMerchantInfoDTO updateMerchantResponseKey(",
                "public void deleteMerchant("
        ).forEach(signature -> assertPreparesInvalidation(
                source,
                signature,
                "prepareKeyMetadataInvalidation("
        ));
        assertThat(source)
                .contains("private void prepareKeyMetadataInvalidation(String merchantId)")
                .contains("cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_KEY_METADATA, merchantId)");
        log.info("密钥元数据失效入口验证完成，结果: 所有密钥变更均登记 merchant:keyMeta Outbox");
    }

    /** 删除商户时必须同时失效支付路由永久快照。 */
    @Test
    void shouldPrepareRouteInvalidationWhenMerchantIsDeleted() throws IOException {
        log.info("测试删除商户路由失效，关键输入: deleteMerchant");
        String source = Files.readString(modulePath(
                "src/main/java/com/scott/payment/admin/service/impl/AdminMerchantInfoServiceImpl.java"
        ));

        assertPreparesInvalidation(
                source,
                "public void deleteMerchant(",
                "cacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_ROUTE"
        );
        log.info("删除商户路由失效验证完成，结果: merchant:route Outbox 已登记");
    }

    /** OpenAPI IP 策略写入口必须登记聚合策略缓存失效。 */
    @Test
    void shouldPrepareInvalidationForEveryOpenApiAccessMutation() throws IOException {
        log.info("测试 OpenAPI IP 策略失效入口，关键输入: 新增、修改、启停、删除和总开关变更");
        String source = Files.readString(modulePath(
                "src/main/java/com/scott/payment/admin/service/impl/"
                        + "AdminMerchantIpWhitelistServiceImpl.java"
        ));

        List.of(
                "public List<MerchantIpWhitelistResponse> createWhitelists(",
                "public MerchantIpWhitelistResponse updateWhitelist(",
                "public MerchantIpWhitelistResponse updateWhitelistStatus(",
                "public void deleteWhitelist(",
                "public MerchantIpWhitelistResponse updateConfig("
        ).forEach(signature -> assertPreparesInvalidation(
                source,
                signature,
                "securityCacheInvalidationCoordinator.prepare("
        ));
        log.info("OpenAPI IP 策略失效入口验证完成，结果: 五类变更均登记可靠失效");
    }

    /** 从指定方法正文中验证可靠失效调用存在。 */
    private void assertPreparesInvalidation(String source,
                                            String methodSignature,
                                            String expectedCall) {
        int methodStart = source.indexOf(methodSignature);
        assertThat(methodStart)
                .as("method signature %s", methodSignature)
                .isGreaterThanOrEqualTo(0);
        int bodyStart = source.indexOf('{', methodStart);
        int bodyEnd = matchingBrace(source, bodyStart);
        assertThat(source.substring(bodyStart, bodyEnd))
                .as("method %s", methodSignature)
                .contains(expectedCall);
    }

    /** 按 Java 花括号深度定位方法正文结束位置。 */
    private int matchingBrace(String source, int bodyStart) {
        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return index;
            }
        }
        throw new IllegalArgumentException("method body is not balanced");
    }

    /** 兼容从仓库根目录或 service-admin 模块目录运行测试。 */
    private Path modulePath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("service-admin").resolve(relativePath);
    }
}
