package com.scott.payment.payment.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundAndChannelMatchSchemaMigrationContractTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 退款管理和勾兑异常开发环境配置契约。
 * @status : create
 */
class RefundAndChannelMatchSchemaMigrationContractTests {

    @Test
    void devConfigurationShouldEnableQueryAndAutomaticCaseCreationOnly() throws IOException {
        String configuration = readRepositoryFile("docs/deployment/nacos/service-payment-dev.yaml");

        assertThat(configuration).contains(
                "enabled: ${PAYMENT_REFUND_MANAGEMENT_ENABLED:true}",
                "approval-enabled: ${PAYMENT_REFUND_APPROVAL_ENABLED:false}",
                "execution-mq-enabled: ${PAYMENT_REFUND_EXECUTION_MQ_ENABLED:false}",
                "enabled: ${PAYMENT_CHANNEL_MATCH_ABNORMAL_ENABLED:true}",
                "review-required-threshold: ${PAYMENT_CHANNEL_MATCH_ABNORMAL_THRESHOLD:12}"
        );
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path direct = Path.of(relativePath);
        if (Files.exists(direct)) {
            return Files.readString(direct);
        }
        return Files.readString(Path.of("..").resolve(relativePath).normalize());
    }
}
