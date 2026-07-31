package com.scott.payment.admin.application.cache;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户安全缓存失效写入口覆盖契约测试。
 */
class MerchantSecurityCacheInvalidationEntryPointContractTests {

    @Test
    void shouldPrepareInvalidationForEveryMerchantProfileMutation() throws IOException {
        String source = Files.readString(modulePath(
                "src/main/java/com/scott/payment/admin/service/impl/AdminMerchantInfoServiceImpl.java"
        ));

        List.of(
                "public AdminMerchantInfoDTO createMerchant(",
                "public AdminMerchantInfoDTO updateMerchant(",
                "public AdminMerchantInfoDTO updateStatus("
        ).forEach(signature -> assertPreparesInvalidation(
                source,
                signature,
                "cacheInvalidationCoordinator.prepare("
        ));
    }

    @Test
    void shouldPrepareInvalidationForEveryOpenApiAccessMutation() throws IOException {
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
    }

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

    private Path modulePath(String relativePath) {
        Path direct = Path.of(relativePath);
        return Files.exists(direct) ? direct : Path.of("service-admin").resolve(relativePath);
    }
}
