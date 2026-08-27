package com.scott.payment.settlement.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 锁定结算运营查询和投影失败记录的有界扫描、CAS 与只读边界。 */
class SettlementManagementMapperContractTest {

    @Test
    void managementQueriesShouldUseBusinessDateCursorAndBoundedLimitWithoutWrites() throws IOException {
        String source = read("service-settlement/src/main/java/com/scott/payment/settlement/mapper/"
                + "SettlementManagementMapper.java");
        assertThat(source).contains(
                "business_date BETWEEN #{beginBusinessDate} AND #{endBusinessDate}",
                "AND id &lt; #{cursorId}",
                "ORDER BY id DESC",
                "LIMIT #{limit}",
                "selectOperationalState");
        assertThat(source).doesNotContain("@Update", "@Insert", "@Delete");
    }

    @Test
    void projectionFailureShouldUseStateRetryAndVersionCasAfterRollback() throws IOException {
        String mapper = read("service-settlement/src/main/java/com/scott/payment/settlement/mapper/"
                + "SettlementProjectionMapper.java");
        String service = read("service-settlement/src/main/java/com/scott/payment/settlement/application/"
                + "SettlementProjectionApplicationService.java");
        assertThat(mapper).contains(
                "SET task_status = 'FAILED'",
                "retry_count = retry_count + 1",
                "next_retry_time = #{nextRetryTime}",
                "AND task_status IN ('INIT', 'FAILED')",
                "AND retry_count = #{expectedRetryCount}",
                "AND version = #{expectedVersion}");
        assertThat(service).contains("Propagation.REQUIRES_NEW", "recordFailure(");
    }

    private String read(String relativePath) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IllegalStateException(relativePath + " is missing");
    }
}
