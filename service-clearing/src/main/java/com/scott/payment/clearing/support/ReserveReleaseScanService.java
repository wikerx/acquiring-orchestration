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
 * 到期保证金分片扫描服务。
 *
 * <p>仅遍历治理配置中已经发布且不晚于当前业务季度的物理节点。扫描本身不持有长事务，每条候选由
 * {@link ReserveReleaseService} 在独立事务中执行版本校验和幂等释放；单条失败不会阻断其他候选。</p>
 */
@Slf4j
@Service
public class ReserveReleaseScanService {

    /** 每个季度单轮最多领取的候选数，限制调度压力和故障影响范围。 */
    private static final int QUARTER_BATCH_SIZE = 200;
    /** 已发布交易分片后缀格式：四位年份、固定分隔位 0、一位季度。 */
    private static final Pattern PHYSICAL_NODE = Pattern.compile("\\d{4}0[1-4]");
    /** 与交易分片路由一致的业务时区，避免服务器默认时区改变季度和业务日。 */
    private static final ZoneId BUSINESS_ZONE = ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID);

    private final ClearingReserveMapper reserveMapper;
    private final ReserveReleaseService releaseService;
    private final TransactionShardingProperties shardingProperties;
    private final ClearingOperationalMetrics metrics;
    private final Clock clock;

    /**
     * 创建使用 UTC 时钟和固定交易路由时区的生产扫描服务。
     *
     * @param reserveMapper 到期保证金候选查询 Mapper
     * @param releaseService 单条保证金幂等释放服务
     * @param shardingProperties 已发布交易分片配置
     * @param metrics 保证金释放低基数指标
     */
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
                    log.error("到期保证金候选释放失败, event: RESERVE_RELEASE_CANDIDATE_FAILED, reserveStateId: {}, exceptionType: {}",
                            candidate == null ? null : candidate.getReserveStateId(),
                            exception.getClass().getSimpleName(), exception);
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

    /** 将已发布物理节点后缀转换为季度起点，拒绝非治理格式。 */
    private LocalDateTime quarterAnchor(String suffix) {
        if (suffix == null || !PHYSICAL_NODE.matcher(suffix).matches()) {
            throw new IllegalStateException("transaction sharding physical node suffix must use yyyyQQ");
        }
        int year = Integer.parseInt(suffix.substring(0, 4));
        int quarter = Integer.parseInt(suffix.substring(5, 6));
        return LocalDateTime.of(year, (quarter - 1) * 3 + 1, 1, 0, 0);
    }

    /** 将业务时间归一到所属季度首日零点。 */
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
