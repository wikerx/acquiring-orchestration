package com.scott.payment.job.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scott.payment.component.db.sharding.ShardingQuarter;
import com.scott.payment.component.db.sharding.ShardingQuarterResolver;
import com.scott.payment.component.db.sharding.TransactionShardingGovernanceProperties;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.job.JobApplication;
import com.scott.payment.job.api.internal.dto.ShardingTablePreCreateInternalRequest;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateResult;
import com.scott.payment.job.entity.SysShardingPhysicalTableDO;
import com.scott.payment.job.entity.SysShardingTableCreateLogDO;
import com.scott.payment.job.mapper.SysShardingPhysicalTableMapper;
import com.scott.payment.job.mapper.SysShardingTableCreateLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateDevDryRunAcceptanceTest
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 使用 dev 真实配置和数据库验收季度预建 Dry Run；默认禁用且不执行 DDL 或发布 Nacos。
 * @status : create
 */
@ActiveProfiles("dev")
@EnabledIfSystemProperty(named = "shardingsphere.dev-dry-run.enabled", matches = "true")
@Slf4j
@SpringBootTest(
        classes = JobApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.cloud.nacos.discovery.register-enabled=false",
                "job.scheduler.enabled=false",
                "acquiring.mq.initializer.enabled=false",
                "acquiring.operation-log.mq.enabled=false"
        })
class ShardingTablePreCreateDevDryRunAcceptanceTest {

    /** 隔离本验收入口治理日志的非敏感操作人标识。 */
    private static final String OPERATOR_ID = "codex-shardingsphere-dev-dry-run";
    /** 治理日志中用于识别本验收入口的非敏感操作人名称。 */
    private static final String OPERATOR_NAME = "Codex Dev Dry Run Acceptance";
    /** 必须完整参与季度治理的正式交易逻辑表数量。 */
    private static final int FORMAL_TABLE_COUNT = TransactionShardingProperties.FORMAL_LOGIC_TABLE_COUNT;
    /** 本次 Dry Run 固定覆盖当前季度和下一季度。 */
    private static final int TARGET_QUARTER_COUNT = 2;

    /** 复用生产请求映射、审计上下文和治理服务的应用入口。 */
    private final ShardingTablePreCreateApplicationService applicationService;
    /** 按治理时区解析当前和下一季度。 */
    private final ShardingQuarterResolver quarterResolver;
    /** 从 dev Nacos 绑定的 24 表物理治理规则。 */
    private final TransactionShardingGovernanceProperties governanceProperties;
    /** 从 dev Nacos 绑定的已发布 ShardingSphere 逻辑节点。 */
    private final TransactionShardingProperties transactionShardingProperties;
    /** 只核验本轮目标物理表治理记录，不执行 DDL。 */
    private final SysShardingPhysicalTableMapper physicalTableMapper;
    /** 核验本验收入口每次执行只新增一条 Dry Run 审计日志。 */
    private final SysShardingTableCreateLogMapper createLogMapper;

    /**
     * 创建仅由显式系统属性启用的 dev Dry Run 验收测试。
     *
     * @param applicationService             生产预建应用服务
     * @param quarterResolver                季度解析器
     * @param governanceProperties           dev 治理配置
     * @param transactionShardingProperties 当前已发布的交易逻辑节点
     * @param physicalTableMapper            治理记录 Mapper
     * @param createLogMapper                Dry Run 日志 Mapper
     */
    @Autowired
    ShardingTablePreCreateDevDryRunAcceptanceTest(
            ShardingTablePreCreateApplicationService applicationService,
            ShardingQuarterResolver quarterResolver,
            TransactionShardingGovernanceProperties governanceProperties,
            TransactionShardingProperties transactionShardingProperties,
            SysShardingPhysicalTableMapper physicalTableMapper,
            SysShardingTableCreateLogMapper createLogMapper) {
        this.applicationService = applicationService;
        this.quarterResolver = quarterResolver;
        this.governanceProperties = governanceProperties;
        this.transactionShardingProperties = transactionShardingProperties;
        this.physicalTableMapper = physicalTableMapper;
        this.createLogMapper = createLogMapper;
    }

    @Test
    void shouldValidateCurrentAndNextQuarterWithoutExecutingDdlOrPublishingConfiguration() {
        ShardingQuarter currentQuarter = quarterResolver.currentQuarter(governanceProperties);
        ShardingQuarter nextQuarter = currentQuarter.next();
        List<String> expectedTargetQuarters = List.of(currentQuarter.displayName(), nextQuarter.displayName());
        LinkedHashSet<String> expectedPhysicalNodes = new LinkedHashSet<>(
                transactionShardingProperties.getPhysicalNodes());
        expectedPhysicalNodes.add(currentQuarter.suffix());
        expectedPhysicalNodes.add(nextQuarter.suffix());
        long logCountBefore = countAcceptanceLogs();

        ShardingTablePreCreateInternalRequest request = new ShardingTablePreCreateInternalRequest();
        request.setDryRun(Boolean.TRUE);
        request.setIncludeCurrentQuarter(Boolean.TRUE);
        request.setIncludeNextQuarter(Boolean.TRUE);
        request.setCompareSchemaIfExists(Boolean.TRUE);
        request.setOperatorId(OPERATOR_ID);
        request.setOperatorName(OPERATOR_NAME);

        ShardingTablePreCreateResult result;
        try {
            result = applicationService.preCreate(request, true);
        } catch (RuntimeException exception) {
            long logCountAfter = countAcceptanceLogs();
            log.info(
                    "SHARDING_DEV_DRY_RUN_BLOCKED failureType={}, governanceRuleCount={}, acceptanceLogDelta={}",
                    exception.getClass().getSimpleName(),
                    governanceProperties.getTables().size(),
                    logCountAfter - logCountBefore);
            assertThat(logCountAfter)
                    .as("the production failure path must persist exactly one Dry Run audit log")
                    .isEqualTo(logCountBefore + 1L);
            throw exception;
        }
        printNonSensitiveSummary(result);

        assertThat(result.getDryRun()).isTrue();
        assertThat(result.getTimezone()).isEqualTo(TransactionShardingProperties.REQUIRED_ZONE_ID);
        assertThat(result.getTargetQuarters()).containsExactlyElementsOf(expectedTargetQuarters);
        assertThat(result.getTableResults()).hasSize(FORMAL_TABLE_COUNT * TARGET_QUARTER_COUNT);
        assertThat(result.getCreatedTables()).isEmpty();
        assertThat(result.getSkippedTables()).hasSize(FORMAL_TABLE_COUNT * TARGET_QUARTER_COUNT);
        assertThat(result.getFailedTables()).isEmpty();
        assertThat(result.getSchemaMismatchTables()).isEmpty();
        assertThat(result.getTableResults()).allSatisfy(table -> {
            assertThat(table.getStatus()).isEqualTo("SKIPPED");
            assertThat(table.getSchemaCheckStatus()).isEqualTo("MATCHED");
            assertThat(table.getShardingTimeCheckStatus()).isEqualTo("MATCHED");
            assertThat(table.getCharsetCheckStatus()).isEqualTo("MATCHED");
            assertThat(table.getAutoIncrementCheckStatus()).isEqualTo("MATCHED");
        });
        assertThat(result.getVerifiedPhysicalNodes()).containsExactlyElementsOf(expectedPhysicalNodes);
        assertThat(result.getCandidateRuleVersion()).isNotBlank();
        assertThat(result.getCandidateRuleChecksum()).startsWith("sha256:");
        assertThat(result.getPublicationReady()).isTrue();
        assertThat(result.getPublicationBlockers()).isEmpty();
        assertThat(result.getNextAction()).isEqualTo("REVIEW_AND_PUBLISH_VERSIONED_NACOS_THEN_ROLLING_RESTART");

        List<String> physicalTables = result.getTableResults().stream()
                .map(table -> table.getPhysicalTable())
                .toList();
        List<SysShardingPhysicalTableDO> governanceRecords = physicalTableMapper.selectList(
                new LambdaQueryWrapper<SysShardingPhysicalTableDO>()
                        .in(SysShardingPhysicalTableDO::getPhysicalTable, physicalTables));
        assertThat(governanceRecords).hasSize(FORMAL_TABLE_COUNT * TARGET_QUARTER_COUNT);
        assertThat(governanceRecords)
                .extracting(SysShardingPhysicalTableDO::getPhysicalTable)
                .containsExactlyInAnyOrderElementsOf(physicalTables);
        assertThat(countAcceptanceLogs()).isEqualTo(logCountBefore + 1L);
    }

    /**
     * 统计该显式验收入口写入的 Dry Run 日志，避免把其他人工任务计入断言。
     *
     * @return 当前验收操作人对应的 Dry Run 日志数
     */
    private long countAcceptanceLogs() {
        return createLogMapper.selectCount(new LambdaQueryWrapper<SysShardingTableCreateLogDO>()
                .eq(SysShardingTableCreateLogDO::getDryRun, 1)
                .eq(SysShardingTableCreateLogDO::getOperatorId, OPERATOR_ID));
    }

    /** 输出不含 JDBC、Nacos 地址或凭证的验收摘要，供 CI 和人工验收留证。 */
    private void printNonSensitiveSummary(ShardingTablePreCreateResult result) {
        log.info(
                "SHARDING_DEV_DRY_RUN_RESULT targetQuarters={}, planned={}, created={}, skipped={}, failed={}, "
                        + "mismatched={}, verifiedNodes={}, candidateVersion={}, candidateChecksum={}, publicationReady={}",
                result.getTargetQuarters(),
                result.getTableResults().size(),
                result.getCreatedTables().size(),
                result.getSkippedTables().size(),
                result.getFailedTables().size(),
                result.getSchemaMismatchTables().size(),
                result.getVerifiedPhysicalNodes(),
                result.getCandidateRuleVersion(),
                result.getCandidateRuleChecksum(),
                result.getPublicationReady());
        result.getTableResults().stream()
                .filter(table -> "MISMATCHED".equals(table.getStatus()))
                .forEach(table -> log.info(
                        "SHARDING_DEV_DRY_RUN_MISMATCH physicalTable={}, schema={}, shardingTime={}, charset={}, autoIncrement={}",
                        table.getPhysicalTable(),
                        table.getSchemaCheckStatus(),
                        table.getShardingTimeCheckStatus(),
                        table.getCharsetCheckStatus(),
                        table.getAutoIncrementCheckStatus()));
    }
}
