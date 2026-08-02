package com.scott.payment.admin.application.monitor;

import com.scott.payment.admin.client.job.JobSchedulerInternalClient;
import com.scott.payment.admin.converter.ShardingGovernanceConverter;
import com.scott.payment.admin.dto.monitor.ShardingRuleResponse;
import com.scott.payment.admin.mapper.SysShardingPhysicalTableMapper;
import com.scott.payment.admin.mapper.SysShardingTableCreateLogMapper;
import com.scott.payment.component.db.sharding.PaymentQuarterShardingProperties;
import com.scott.payment.component.db.sharding.ShardingAutoIncrementValueCalculator;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarter;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import com.scott.payment.component.db.sharding.TransactionShardingGovernanceProperties;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminShardingGovernanceApplicationServiceTest
 * @date : 2026-08-02 00:00
 * @description : 验证 Admin 分表治理只展示正式规则，并暴露当前规则版本和实际节点登记状态。
 * @status : create
 */
class AdminShardingGovernanceApplicationServiceTest {

    @Test
    void shouldExcludeLegacyTestRulesAndExposeVerifiedNodeState() {
        PaymentQuarterShardingProperties legacy = new PaymentQuarterShardingProperties();
        legacy.setTables(new LinkedHashMap<>());
        legacy.getTables().put("transaction_order", rule("transaction_order"));
        legacy.getTables().put("test_transaction", rule("test_transaction"));
        TransactionShardingGovernanceProperties governance = new TransactionShardingGovernanceProperties();
        TransactionShardingProperties transaction = new TransactionShardingProperties();
        transaction.setRuleVersion("2026.08.02-001");
        transaction.setRuleChecksum("sha256:1234567890abcdef1234567890abcdef");
        transaction.setPhysicalNodes(List.of("202603"));
        ShardingQuarterResolver quarterResolver = mock(ShardingQuarterResolver.class);
        ShardingQuarter currentQuarter = new ShardingQuarter(2026, 3);
        when(quarterResolver.currentQuarter(governance)).thenReturn(currentQuarter);
        when(quarterResolver.inRange(any(), any())).thenReturn(true);
        when(quarterResolver.quartersInRange(any())).thenReturn(List.of(currentQuarter, currentQuarter.next()));

        AdminShardingGovernanceApplicationService service = new AdminShardingGovernanceApplicationService(
                legacy,
                governance,
                transaction,
                quarterResolver,
                new ShardingPhysicalTableNameResolver(),
                new ShardingAutoIncrementValueCalculator(),
                mock(SysShardingPhysicalTableMapper.class),
                mock(SysShardingTableCreateLogMapper.class),
                mock(JobSchedulerInternalClient.class),
                mock(ShardingGovernanceConverter.class));

        List<ShardingRuleResponse> rules = service.listRules();

        assertThat(rules).singleElement().satisfies(rule -> {
            assertThat(rule.getLogicalTable()).isEqualTo("transaction_order");
            assertThat(rule.getRuleVersion()).isEqualTo("2026.08.02-001");
            assertThat(rule.getRuleChecksumPrefix()).isEqualTo("sha256:1234567890ab");
            assertThat(rule.getVerifiedPhysicalNodes()).containsExactly("202603");
            assertThat(rule.getCurrentNodeRegistered()).isTrue();
            assertThat(rule.getNextNodeRegistered()).isFalse();
        });
    }

    private PaymentQuarterShardingProperties.TableRule rule(String logicalTable) {
        PaymentQuarterShardingProperties.TableRule rule = new PaymentQuarterShardingProperties.TableRule();
        rule.setLogicalTable(logicalTable);
        rule.setTemplateTable(logicalTable);
        rule.setStartYear(2026);
        rule.setStartQuarter(3);
        rule.setEndYear(2026);
        rule.setEndQuarter(4);
        return rule;
    }
}
