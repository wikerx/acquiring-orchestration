package com.scott.payment.admin.application.monitor;

import com.scott.payment.admin.client.job.JobSchedulerInternalClient;
import com.scott.payment.admin.converter.ShardingGovernanceConverter;
import com.scott.payment.admin.dto.monitor.ShardingPhysicalTableResponse;
import com.scott.payment.admin.dto.monitor.ShardingRuleResponse;
import com.scott.payment.admin.entity.SysShardingPhysicalTableDO;
import com.scott.payment.admin.mapper.SysShardingPhysicalTableMapper;
import com.scott.payment.admin.mapper.SysShardingTableCreateLogMapper;
import com.scott.payment.component.db.sharding.ShardingAutoIncrementValueCalculator;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarter;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import com.scott.payment.component.db.sharding.RiskAuditShardingProperties;
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
 * 验证 Admin 分表治理只展示正式规则，并暴露当前规则版本和实际节点登记状态。
 */
class AdminShardingGovernanceApplicationServiceTest {

    @Test
    void shouldExcludeLegacyTestRulesAndExposeVerifiedNodeState() {
        TransactionShardingGovernanceProperties governance = new TransactionShardingGovernanceProperties();
        governance.setTables(new LinkedHashMap<>());
        governance.getTables().put("transaction_order", rule("transaction_order"));
        governance.getTables().put("test_transaction", rule("test_transaction"));
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
                governance,
                transaction,
                new RiskAuditShardingProperties(),
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
            assertThat(rule.getPhysicalTableCount()).isEqualTo(8);
        });
    }

    @Test
    void shouldNotMarkRetiredTestTableAsRegisteredByQuarterSuffixAlone() {
        TransactionShardingGovernanceProperties governance = new TransactionShardingGovernanceProperties();
        TransactionShardingProperties transaction = new TransactionShardingProperties();
        transaction.setRuleVersion("2026.08.02-001");
        transaction.setRuleChecksum("sha256:1234567890abcdef1234567890abcdef");
        transaction.setPhysicalNodes(List.of("202603"));
        SysShardingPhysicalTableMapper physicalTableMapper = mock(SysShardingPhysicalTableMapper.class);
        ShardingGovernanceConverter converter = mock(ShardingGovernanceConverter.class);
        SysShardingPhysicalTableDO entity = new SysShardingPhysicalTableDO();
        ShardingPhysicalTableResponse response = new ShardingPhysicalTableResponse();
        response.setLogicalTable("test_transaction");
        response.setQuarterSuffix("202603");
        when(physicalTableMapper.selectById(1L)).thenReturn(entity);
        when(converter.toPhysicalTableResponse(entity)).thenReturn(response);

        AdminShardingGovernanceApplicationService service = new AdminShardingGovernanceApplicationService(
                governance,
                transaction,
                new RiskAuditShardingProperties(),
                mock(ShardingQuarterResolver.class),
                new ShardingPhysicalTableNameResolver(),
                new ShardingAutoIncrementValueCalculator(),
                physicalTableMapper,
                mock(SysShardingTableCreateLogMapper.class),
                mock(JobSchedulerInternalClient.class),
                converter);

        assertThat(service.getPhysicalTable(1L).getNodeRegistered()).isFalse();
    }

    /**
     * 风控审计表展示独立版本、checksum 和已验收节点，不得借用交易 28 表的运行元数据。
     */
    @Test
    void shouldExposeIndependentRiskAuditRuleMetadata() {
        TransactionShardingGovernanceProperties governance = new TransactionShardingGovernanceProperties();
        governance.setTables(new LinkedHashMap<>());
        governance.getTables().put("risk_evaluation_record", rule("risk_evaluation_record"));
        TransactionShardingProperties transaction = new TransactionShardingProperties();
        transaction.setRuleVersion("transaction-version");
        transaction.setPhysicalNodes(List.of("202603"));
        RiskAuditShardingProperties riskAudit = new RiskAuditShardingProperties();
        riskAudit.setRuleVersion("2026.11.08-001");
        riskAudit.setRuleChecksum("sha256:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
        riskAudit.setPhysicalNodes(List.of("202604"));
        ShardingQuarterResolver quarterResolver = mock(ShardingQuarterResolver.class);
        ShardingQuarter currentQuarter = new ShardingQuarter(2026, 4);
        when(quarterResolver.currentQuarter(governance)).thenReturn(currentQuarter);
        when(quarterResolver.inRange(any(), any())).thenReturn(true);

        AdminShardingGovernanceApplicationService service = new AdminShardingGovernanceApplicationService(
                governance,
                transaction,
                riskAudit,
                quarterResolver,
                new ShardingPhysicalTableNameResolver(),
                new ShardingAutoIncrementValueCalculator(),
                mock(SysShardingPhysicalTableMapper.class),
                mock(SysShardingTableCreateLogMapper.class),
                mock(JobSchedulerInternalClient.class),
                mock(ShardingGovernanceConverter.class));

        assertThat(service.listRules()).singleElement().satisfies(response -> {
            assertThat(response.getTableFamily()).isEqualTo("RISK_AUDIT");
            assertThat(response.getRuleVersion()).isEqualTo("2026.11.08-001");
            assertThat(response.getRuleChecksumPrefix()).isEqualTo("sha256:1234567890ab");
            assertThat(response.getVerifiedPhysicalNodes()).containsExactly("202604");
            assertThat(response.getCurrentNodeRegistered()).isTrue();
        });
    }

    private TransactionShardingGovernanceProperties.TableRule rule(String logicalTable) {
        TransactionShardingGovernanceProperties.TableRule rule = new TransactionShardingGovernanceProperties.TableRule();
        rule.setLogicalTable(logicalTable);
        rule.setTemplateTable(logicalTable);
        rule.setStartYear(2026);
        rule.setStartQuarter(3);
        rule.setEndYear(2026);
        rule.setEndQuarter(4);
        return rule;
    }
}
