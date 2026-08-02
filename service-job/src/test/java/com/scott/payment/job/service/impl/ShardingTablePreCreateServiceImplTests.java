package com.scott.payment.job.service.impl;

import com.scott.payment.component.db.sharding.PaymentQuarterShardingProperties;
import com.scott.payment.component.db.sharding.ShardingAutoIncrementValueCalculator;
import com.scott.payment.component.db.sharding.ShardingPhysicalTableNameResolver;
import com.scott.payment.component.db.sharding.ShardingQuarter;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import com.scott.payment.component.db.sharding.ShardingTableDdlService;
import com.scott.payment.component.db.sharding.ShardingTableInspectionResult;
import com.scott.payment.component.db.sharding.ShardingTableSchemaInspector;
import com.scott.payment.component.db.sharding.TransactionShardingGovernanceProperties;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateRequest;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateResult;
import com.scott.payment.job.entity.SysShardingTableCreateLogDO;
import com.scott.payment.job.mapper.SysShardingPhysicalTableMapper;
import com.scott.payment.job.mapper.SysShardingTableCreateLogMapper;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 交易季度物理表预建和候选节点发布门禁测试。
 */
class ShardingTablePreCreateServiceImplTests {

    @Test
    void dryRunShouldPlanMissingTablesWithoutDdlOrFutureNodePublication() {
        Fixture fixture = fixture();
        when(fixture.schemaInspector.tableExists(any())).thenReturn(false);
        when(fixture.schemaInspector.inspectTemplate(any())).thenReturn(validInspection(null));
        ShardingTablePreCreateRequest request = nextQuarterRequest(true);

        ShardingTablePreCreateResult result = fixture.service.preCreate(request, null);

        assertThat(result.getTableResults()).hasSize(23);
        assertThat(result.getTableResults()).allMatch(item -> "DRY_RUN".equals(item.getStatus()));
        assertThat(result.getVerifiedPhysicalNodes()).containsExactly("202603");
        assertThat(result.getPublicationReady()).isFalse();
        assertThat(result.getPublicationBlockers()).containsExactly(
                "quarter 2026-Q4 has missing or unverified physical tables");
        assertThat(result.getCandidateRuleChecksum()).startsWith("sha256:");
        verify(fixture.ddlService, never()).createPhysicalTableIfAbsent(any(), any(), any());
        verify(fixture.createLogMapper).insert(any(SysShardingTableCreateLogDO.class));
    }

    @Test
    void verifiedQuarterShouldProduceVersionedCandidateWithoutPublishingIt() {
        Fixture fixture = fixture();
        when(fixture.schemaInspector.tableExists(any())).thenReturn(true);
        when(fixture.ddlService.createPhysicalTableIfAbsent(any(), any(), any()))
                .thenReturn(validInspection(202_604_000_000_000_001L));

        ShardingTablePreCreateResult result = fixture.service.preCreate(nextQuarterRequest(false), null);

        assertThat(result.getTableResults()).hasSize(23);
        assertThat(result.getTableResults()).allMatch(item -> "SKIPPED".equals(item.getStatus()));
        assertThat(result.getTableResults()).allMatch(item -> "MATCHED".equals(item.getAutoIncrementCheckStatus()));
        assertThat(result.getVerifiedPhysicalNodes()).containsExactly("202603", "202604");
        assertThat(result.getPublicationReady()).isTrue();
        assertThat(result.getCandidateRuleVersion()).isEqualTo("2026.08.02-001-candidate-202604");
        assertThat(result.getNextAction()).isEqualTo("REVIEW_AND_PUBLISH_VERSIONED_NACOS_THEN_ROLLING_RESTART");
    }

    @Test
    void invalidQuarterRangeShouldBlockCandidateNode() {
        Fixture fixture = fixture();
        when(fixture.schemaInspector.tableExists(any())).thenReturn(true);
        when(fixture.ddlService.createPhysicalTableIfAbsent(any(), any(), any()))
                .thenReturn(validInspection(1L));

        ShardingTablePreCreateResult result = fixture.service.preCreate(nextQuarterRequest(false), null);

        assertThat(result.getPublicationReady()).isFalse();
        assertThat(result.getVerifiedPhysicalNodes()).containsExactly("202603");
        assertThat(result.getTableResults()).allMatch(item -> "MISMATCHED".equals(item.getStatus()));
        assertThat(result.getTableResults()).allMatch(item -> "MISMATCHED".equals(item.getAutoIncrementCheckStatus()));
    }

    @Test
    void governanceConfigShouldRejectAnythingOtherThanFormalTwentyThreeTables() {
        Fixture fixture = fixture();
        fixture.governanceProperties.getTables().remove("transaction_abnormal_event");

        assertThatThrownBy(() -> fixture.service.preCreate(nextQuarterRequest(true), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 23 formal tables");

        verify(fixture.ddlService, never()).createPhysicalTableIfAbsent(any(), any(), any());
    }

    private Fixture fixture() {
        TransactionShardingGovernanceProperties governance = governanceProperties();
        TransactionShardingProperties transaction = new TransactionShardingProperties();
        transaction.setRuleVersion("2026.08.02-001");
        transaction.setPhysicalNodes(List.of("202603"));
        ShardingQuarterResolver quarterResolver = mock(ShardingQuarterResolver.class);
        when(quarterResolver.currentQuarter(any())).thenReturn(new ShardingQuarter(2026, 3));
        when(quarterResolver.inRange(any(), any())).thenReturn(true);
        when(quarterResolver.endQuarter(any())).thenReturn(new ShardingQuarter(2030, 4));
        when(quarterResolver.zoneId(any())).thenReturn(ZoneId.of("Asia/Shanghai"));
        ShardingTableSchemaInspector inspector = mock(ShardingTableSchemaInspector.class);
        ShardingTableDdlService ddlService = mock(ShardingTableDdlService.class);
        SysShardingPhysicalTableMapper physicalMapper = mock(SysShardingPhysicalTableMapper.class);
        SysShardingTableCreateLogMapper createLogMapper = mock(SysShardingTableCreateLogMapper.class);
        ShardingTablePreCreateServiceImpl service = new ShardingTablePreCreateServiceImpl(
                governance,
                transaction,
                quarterResolver,
                new ShardingPhysicalTableNameResolver(),
                new ShardingAutoIncrementValueCalculator(),
                inspector,
                ddlService,
                physicalMapper,
                createLogMapper);
        return new Fixture(governance, inspector, ddlService, createLogMapper, service);
    }

    private TransactionShardingGovernanceProperties governanceProperties() {
        TransactionShardingGovernanceProperties properties = new TransactionShardingGovernanceProperties();
        LinkedHashMap<String, PaymentQuarterShardingProperties.TableRule> rules = new LinkedHashMap<>();
        for (String logicalTable : TransactionShardingProperties.defaultLogicTables()) {
            PaymentQuarterShardingProperties.TableRule rule = new PaymentQuarterShardingProperties.TableRule();
            rule.setEnabled(true);
            rule.setLogicalTable(logicalTable);
            rule.setTemplateTable(logicalTable);
            rule.setShardingColumn("transaction_date_time");
            rule.setActualDataSource("master");
            rule.setStartYear(2026);
            rule.setStartQuarter(1);
            rule.setEndYear(2030);
            rule.setEndQuarter(4);
            rules.put(logicalTable, rule);
        }
        properties.setTables(rules);
        return properties;
    }

    private ShardingTablePreCreateRequest nextQuarterRequest(boolean dryRun) {
        ShardingTablePreCreateRequest request = new ShardingTablePreCreateRequest();
        request.setDryRun(dryRun);
        request.setIncludeCurrentQuarter(false);
        request.setIncludeNextQuarter(true);
        return request;
    }

    private ShardingTableInspectionResult validInspection(Long autoIncrementCurrent) {
        ShardingTableInspectionResult result = new ShardingTableInspectionResult();
        result.setExists(true);
        result.setIdColumnMatched(true);
        result.setShardingColumnExists(true);
        result.setShardingColumnPrecisionMatched(true);
        result.setCharsetMatched(true);
        result.setSchemaMatched(true);
        result.setSchemaCheckStatus("MATCHED");
        result.setAutoIncrementCurrent(autoIncrementCurrent);
        return result;
    }

    private record Fixture(
            TransactionShardingGovernanceProperties governanceProperties,
            ShardingTableSchemaInspector schemaInspector,
            ShardingTableDdlService ddlService,
            SysShardingTableCreateLogMapper createLogMapper,
            ShardingTablePreCreateServiceImpl service) {
    }
}
