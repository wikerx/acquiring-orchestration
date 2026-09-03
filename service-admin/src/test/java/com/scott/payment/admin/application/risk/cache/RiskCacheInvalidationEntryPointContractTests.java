package com.scott.payment.admin.application.risk.cache;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskCacheInvalidationEntryPointContractTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 风控缓存失效写入口接入契约测试。
 * @status : create
 */
class RiskCacheInvalidationEntryPointContractTests {

    @Test
    void shouldPrepareInvalidationForEveryRiskManagementMutation() throws IOException {
        String source = Files.readString(modulePath(
                "src/main/java/com/scott/payment/admin/application/risk/"
                        + "AdminRiskManagementApplicationService.java"
        ));

        List.of(
                "public RiskDTOs.RiskRecordResponse createList(",
                "public RiskDTOs.RiskRecordResponse updateList(",
                "public RiskDTOs.RiskRecordResponse createRegion(",
                "public RiskDTOs.RiskRecordResponse updateRegion(",
                "public void remove(",
                "public void batchRemove(",
                "public RiskDTOs.RiskRecordResponse updateStatus(",
                "public RiskDTOs.RiskRecordResponse createRule(",
                "public RiskDTOs.RiskRecordResponse updateRule(",
                "public List<RiskDTOs.RiskRecordResponse> createSourceUrlRules(",
                "public void createTradeBlack(",
                "public void releaseTradeBlack(",
                "public RiskDTOs.ImportResultResponse importCsv("
        ).forEach(signature -> assertPreparesInvalidation(source, signature));
    }

    @Test
    void shouldPrepareInvalidationForEveryMerchantIpWhitelistMutation() throws IOException {
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
        ).forEach(signature -> assertPreparesInvalidation(source, signature));
    }

    private void assertPreparesInvalidation(String source, String methodSignature) {
        int methodStart = source.indexOf(methodSignature);
        assertThat(methodStart)
                .as("method signature %s", methodSignature)
                .isGreaterThanOrEqualTo(0);
        int bodyStart = source.indexOf('{', methodStart);
        int bodyEnd = matchingBrace(source, bodyStart);
        assertThat(source.substring(bodyStart, bodyEnd))
                .as("method %s", methodSignature)
                .contains("cacheInvalidationCoordinator.prepare();");
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
