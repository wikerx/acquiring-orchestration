package com.scott.payment.clearing.support;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.clearing.entity.ClearingReserveStateDO;
import com.scott.payment.clearing.mapper.ClearingReserveMapper;
import com.scott.payment.clearing.service.ReserveReleaseService;
import com.scott.payment.clearing.service.ReserveReleaseService.ReserveReleaseOutcome;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReserveReleaseScanService
 * @date : 2026-08-26 18:50
 * @email : scott_x@163.com
 * @description : 按已发布季度限量扫描到期保证金；扫描不持有长事务，每条候选由独立事务完成释放。
 * @status : create
 */
@Slf4j
@Service
public class ReserveReleaseScanService {

    /**
     * 季度批次大小，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int QUARTER_BATCH_SIZE = 200;
    /**
     * 物理节点常量，统一 {@code ReserveReleaseScanService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final Pattern PHYSICAL_NODE = Pattern.compile("\\d{4}0[1-4]");
    /**
     * {@code BUSINESS_ZONE}常量，统一 {@code ReserveReleaseScanService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID);

    private final ClearingReserveMapper reserveMapper;
    private final ReserveReleaseService releaseService;
    private final TransactionShardingProperties shardingProperties;
    private final ClearingOperationalMetrics metrics;
    private final Clock clock;

    /** 创建使用 UTC 时钟和固定交易路由时区的生产扫描服务。 */
    @Autowired
    public ReserveReleaseScanService(ClearingReserveMapper reserveMapper,
                                     ReserveReleaseService releaseService,
                                     TransactionShardingProperties shardingProperties,
                                     ClearingOperationalMetrics metrics) {
        this(reserveMapper, releaseService, shardingProperties, metrics, Clock.systemUTC());
    }

    /** 包级构造器用于固定测试时点，不改变生产调度行为。 */
    ReserveReleaseScanService(ClearingReserveMapper reserveMapper,
                              ReserveReleaseService releaseService,
                              TransactionShardingProperties shardingProperties,
                              ClearingOperationalMetrics metrics,
                              Clock clock) {
        this.reserveMapper = reserveMapper;
        this.releaseService = releaseService;
        this.shardingProperties = shardingProperties;
        this.metrics = metrics;
        this.clock = clock;
    }

    /**
     * 扫描不晚于当前季度的全部已发布节点，每季度每轮最多处理固定数量候选。
     *
     * @return 本轮扫描、成功、幂等跳过和失败数量
     */
    @DS(DataSourceName.TRANSACTION)
    public ReserveReleaseScanResult scan() {
        Instant releaseInstant = clock.instant();
        LocalDateTime businessTime = LocalDateTime.ofInstant(releaseInstant, BUSINESS_ZONE);
        LocalDate businessDate = businessTime.toLocalDate();
        LocalDateTime currentQuarter = quarterAnchor(businessTime);
        int scanned = 0;
        int released = 0;
        int skipped = 0;
        int failed = 0;

        for (LocalDateTime begin : publishedQuarters()) {
            if (begin.isAfter(currentQuarter)) {
                continue;
            }
            List<ClearingReserveStateDO> candidates = reserveMapper.selectDueReleaseCandidates(
                    begin, begin.plusMonths(3), businessDate, QUARTER_BATCH_SIZE);
            for (ClearingReserveStateDO candidate : candidates == null
                    ? Collections.<ClearingReserveStateDO>emptyList() : candidates) {
                scanned++;
                try {
                    validateCandidate(candidate);
                    ReserveReleaseOutcome outcome = releaseService.release(
                            candidate.getReserveStateId(), candidate.getOriginalTransactionId(),
                            candidate.getTransactionDateTime(), releaseInstant).outcome();
                    if (outcome == null) {
                        throw new IllegalStateException("reserve release outcome is missing");
                    }
                    metrics.recordReserveRelease(outcome.name());
                    if (outcome == ReserveReleaseOutcome.RELEASED) {
                        released++;
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException exception) {
                    failed++;
                    metrics.recordReserveRelease("FAILED");
                    log.error("event: RESERVE_RELEASE_CANDIDATE_FAILED exceptionType: {}",
                            exception.getClass().getSimpleName());
                }
            }
        }
        return new ReserveReleaseScanResult(scanned, released, skipped, failed);
    }

    /** 只扫描数据库治理表声明已发布的季度。 */
    private List<LocalDateTime> publishedQuarters() {
        List<String> nodes = shardingProperties.getPhysicalNodes();
        if (nodes == null) {
            throw new IllegalStateException("transaction sharding physical nodes are unavailable");
        }
        return nodes.stream().map(this::quarterAnchor).distinct().sorted().toList();
    }

    private LocalDateTime quarterAnchor(String suffix) {
        if (suffix == null || !PHYSICAL_NODE.matcher(suffix).matches()) {
            throw new IllegalStateException("transaction sharding physical node suffix must use yyyyQQ");
        }
        int year = Integer.parseInt(suffix.substring(0, 4));
        int quarter = Integer.parseInt(suffix.substring(5, 6));
        return LocalDateTime.of(year, (quarter - 1) * 3 + 1, 1, 0, 0);
    }

    private LocalDateTime quarterAnchor(LocalDateTime value) {
        int firstMonth = ((value.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDateTime.of(value.getYear(), firstMonth, 1, 0, 0);
    }

    /** 扫描候选必须携带状态号、原支付身份和真实分片时间。 */
    private void validateCandidate(ClearingReserveStateDO candidate) {
        if (candidate == null || candidate.getReserveStateId() == null
                || candidate.getReserveStateId().isBlank()
                || candidate.getOriginalTransactionId() == null
                || candidate.getOriginalTransactionId().isBlank()
                || candidate.getTransactionDateTime() == null) {
            throw new IllegalStateException("reserve release scan candidate identity is invalid");
        }
    }

    /** 单轮扫描结果，不包含任何商户号、交易号或金额。 */
    public record ReserveReleaseScanResult(int scanned, int released, int skipped, int failed) {
    }
}
