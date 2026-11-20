package com.scott.payment.admin.support.query;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 管理端历史查询的统一有效时间边界；计划释放日等业务日期仅校验顺序，不截断未来计划。
 */
public final class AdminQueryTimeBounds {
    private static final ZoneId STORAGE_ZONE = ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID);
    private final LocalDateTime historyStart;
    private final Clock clock;

    /**
     * 生产查询以存储时区的系统时钟确定上界。
     */
    public AdminQueryTimeBounds(TransactionShardingProperties properties) {
        this(properties, Clock.system(STORAGE_ZONE));
    }

    /**
     * 固定时钟可验证跨日和未来范围，避免测试依赖执行机器的默认时区。
     */
    public AdminQueryTimeBounds(TransactionShardingProperties properties, Clock clock) {
        this.historyStart = resolveHistoryStart(properties.getPhysicalNodes());
        this.clock = clock.withZone(STORAGE_ZONE);
    }

    /**
     * 查询下界取已登记物理季度的最早起点，不从待建表的规则日期推断数据存在。
     */
    public LocalDateTime historyStart() {
        return historyStart;
    }

    /**
     * 规范化已发生业务的日期窗口；完全落在可查询历史之外的窗口保留为空区间。
     */
    public DateRange historicalDates(LocalDate begin, LocalDate end) {
        if (begin == null || end == null || begin.isAfter(end)) throw invalid();
        LocalDate first = historyStart.toLocalDate();
        LocalDate today = LocalDate.now(clock);
        return new DateRange(begin.isBefore(first) ? first : begin, end.isAfter(today) ? today : end);
    }

    /**
     * 校验可选计划日期；预计释放、结算可用日允许未来日期，不能套用历史时间上界。
     */
    public void validateScheduledDates(LocalDate begin, LocalDate end) {
        if ((begin == null) != (end == null) || begin != null && begin.isAfter(end)) throw invalid();
    }

    /**
     * 将可选来源交易时间限制在已登记分片起点和当前存储时区时刻之间。
     */
    public TimeRange transactionTimes(LocalDateTime begin, LocalDateTime end) {
        if ((begin == null) != (end == null) || begin != null && begin.isAfter(end)) throw invalid();
        if (begin == null) return new TimeRange(null, null);
        LocalDateTime now = LocalDateTime.now(clock);
        return new TimeRange(begin.isBefore(historyStart) ? historyStart : begin,
                end.isAfter(now) ? now : end);
    }

    /**
     * 只解析季度节点；未登记节点时沿用交易查询的兼容下界，不猜测首笔交易日期。
     */
    private static LocalDateTime resolveHistoryStart(List<String> nodes) {
        return nodes == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : nodes.stream()
                .filter(node -> node != null && node.matches("\\d{4}0[1-4]"))
                .min(String::compareTo)
                .map(node -> LocalDateTime.of(Integer.parseInt(node.substring(0, 4)),
                        (Character.digit(node.charAt(5), 10) - 1) * 3 + 1, 1, 0, 0))
                .orElse(LocalDateTime.of(1970, 1, 1, 0, 0));
    }

    /**
     * 缺失端点和倒置范围继续沿用现有参数错误契约。
     */
    private static ServiceException invalid() {
        return new ServiceException(ApiResultEnum.PARAM_INVALID);
    }

    /**
     * 闭合业务日期窗口；裁剪后的倒置端点表示无交集。
     */
    public record DateRange(LocalDate begin, LocalDate end) {
        /**
         * 无交集时调用方直接返回空页，避免发起无效查询。
         */
        public boolean empty() {
            return begin.isAfter(end);
        }
    }

    /**
     * 可选来源时刻窗口；两个空端点表示未启用该条件。
     */
    public record TimeRange(LocalDateTime begin, LocalDateTime end) {
        /**
         * 未启用的条件不视为空窗口。
         */
        public boolean empty() {
            return begin != null && begin.isAfter(end);
        }
    }
}
