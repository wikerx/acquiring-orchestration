package com.scott.payment.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 统一运营待办的查询预算和默认 SLA。
 *
 * <p>全部配置均提供安全默认值，Nacos 可按环境覆盖，但配置缺失不会形成新的启动门禁。</p>
 */
@ConfigurationProperties(prefix = "admin.operations.work-item")
public class AdminOperationalWorkItemProperties {

    private Duration settlementSla = Duration.ofHours(4);
    private Duration exchangeCriticalSla = Duration.ofMinutes(30);
    private Duration exchangeErrorSla = Duration.ofHours(2);
    private Duration exchangeDefaultSla = Duration.ofHours(4);
    private Duration notificationSla = Duration.ofHours(1);
    private Duration channelAbnormalCriticalSla = Duration.ofMinutes(30);
    private Duration channelAbnormalHighSla = Duration.ofHours(1);
    private Duration channelAbnormalWarningSla = Duration.ofHours(4);
    private Duration clearingBacklogSla = Duration.ofHours(1);
    private Duration settlementBacklogSla = Duration.ofHours(1);
    private Duration highRiskCriticalSla = Duration.ofMinutes(30);
    private Duration highRiskEventSla = Duration.ofHours(2);
    private int maxSyncRows = 10000;
    private int maxQueryRows = 10000;

    /**
     * 返回结算复核待办的默认处理时限。
     *
     * @return 结算复核 SLA
     */
    public Duration getSettlementSla() {
        return settlementSla;
    }

    /**
     * 设置结算复核待办的处理时限，无效值回退为四小时。
     *
     * @param settlementSla 结算复核 SLA
     */
    public void setSettlementSla(Duration settlementSla) {
        this.settlementSla = positiveDuration(settlementSla, Duration.ofHours(4));
    }

    /**
     * 返回严重汇率预警的默认处理时限。
     *
     * @return 严重汇率预警 SLA
     */
    public Duration getExchangeCriticalSla() {
        return exchangeCriticalSla;
    }

    /**
     * 设置严重汇率预警的处理时限，无效值回退为三十分钟。
     *
     * @param exchangeCriticalSla 严重汇率预警 SLA
     */
    public void setExchangeCriticalSla(Duration exchangeCriticalSla) {
        this.exchangeCriticalSla = positiveDuration(exchangeCriticalSla, Duration.ofMinutes(30));
    }

    /**
     * 返回错误级汇率预警的默认处理时限。
     *
     * @return 错误级汇率预警 SLA
     */
    public Duration getExchangeErrorSla() {
        return exchangeErrorSla;
    }

    /**
     * 设置错误级汇率预警的处理时限，无效值回退为两小时。
     *
     * @param exchangeErrorSla 错误级汇率预警 SLA
     */
    public void setExchangeErrorSla(Duration exchangeErrorSla) {
        this.exchangeErrorSla = positiveDuration(exchangeErrorSla, Duration.ofHours(2));
    }

    /**
     * 返回其他汇率预警的默认处理时限。
     *
     * @return 默认汇率预警 SLA
     */
    public Duration getExchangeDefaultSla() {
        return exchangeDefaultSla;
    }

    /**
     * 设置其他汇率预警的处理时限，无效值回退为四小时。
     *
     * @param exchangeDefaultSla 默认汇率预警 SLA
     */
    public void setExchangeDefaultSla(Duration exchangeDefaultSla) {
        this.exchangeDefaultSla = positiveDuration(exchangeDefaultSla, Duration.ofHours(4));
    }

    /**
     * 返回商户通知失败待办的默认处理时限。
     *
     * @return 商户通知失败 SLA
     */
    public Duration getNotificationSla() {
        return notificationSla;
    }

    /**
     * 设置商户通知失败待办的处理时限，无效值回退为一小时。
     *
     * @param notificationSla 商户通知失败 SLA
     */
    public void setNotificationSla(Duration notificationSla) {
        this.notificationSla = positiveDuration(notificationSla, Duration.ofHours(1));
    }

    /**
     * 返回渠道勾兑紧急异常的默认处理时限。
     *
     * @return 紧急异常 SLA
     */
    public Duration getChannelAbnormalCriticalSla() {
        return channelAbnormalCriticalSla;
    }

    /**
     * 设置渠道勾兑紧急异常的处理时限，无效值回退为三十分钟。
     *
     * @param channelAbnormalCriticalSla 紧急异常 SLA
     */
    public void setChannelAbnormalCriticalSla(Duration channelAbnormalCriticalSla) {
        this.channelAbnormalCriticalSla = positiveDuration(
                channelAbnormalCriticalSla, Duration.ofMinutes(30));
    }

    /**
     * 返回渠道勾兑高风险异常的默认处理时限。
     *
     * @return 高风险异常 SLA
     */
    public Duration getChannelAbnormalHighSla() {
        return channelAbnormalHighSla;
    }

    /**
     * 设置渠道勾兑高风险异常的处理时限，无效值回退为一小时。
     *
     * @param channelAbnormalHighSla 高风险异常 SLA
     */
    public void setChannelAbnormalHighSla(Duration channelAbnormalHighSla) {
        this.channelAbnormalHighSla = positiveDuration(channelAbnormalHighSla, Duration.ofHours(1));
    }

    /**
     * 返回渠道勾兑普通预警异常的默认处理时限。
     *
     * @return 普通预警异常 SLA
     */
    public Duration getChannelAbnormalWarningSla() {
        return channelAbnormalWarningSla;
    }

    /**
     * 设置渠道勾兑普通预警异常的处理时限，无效值回退为四小时。
     *
     * @param channelAbnormalWarningSla 普通预警异常 SLA
     */
    public void setChannelAbnormalWarningSla(Duration channelAbnormalWarningSla) {
        this.channelAbnormalWarningSla = positiveDuration(channelAbnormalWarningSla, Duration.ofHours(4));
    }

    /**
     * 返回需要人工介入的清分异常积压处理时限。
     *
     * @return 清分异常积压 SLA
     */
    public Duration getClearingBacklogSla() {
        return clearingBacklogSla;
    }

    /**
     * 设置清分异常积压处理时限，无效值回退为一小时。
     *
     * @param clearingBacklogSla 清分异常积压 SLA
     */
    public void setClearingBacklogSla(Duration clearingBacklogSla) {
        this.clearingBacklogSla = positiveDuration(clearingBacklogSla, Duration.ofHours(1));
    }

    /**
     * 返回结算批次人工复核积压的默认处理时限。
     *
     * @return 结算异常积压 SLA
     */
    public Duration getSettlementBacklogSla() {
        return settlementBacklogSla;
    }

    /**
     * 设置结算批次人工复核积压处理时限，无效值回退为一小时。
     *
     * @param settlementBacklogSla 结算异常积压 SLA
     */
    public void setSettlementBacklogSla(Duration settlementBacklogSla) {
        this.settlementBacklogSla = positiveDuration(settlementBacklogSla, Duration.ofHours(1));
    }

    /**
     * 返回紧急高风险事件的默认处理时限。
     *
     * @return 紧急高风险事件 SLA
     */
    public Duration getHighRiskCriticalSla() {
        return highRiskCriticalSla;
    }

    /**
     * 设置紧急高风险事件处理时限，无效值回退为三十分钟。
     *
     * @param highRiskCriticalSla 紧急高风险事件 SLA
     */
    public void setHighRiskCriticalSla(Duration highRiskCriticalSla) {
        this.highRiskCriticalSla = positiveDuration(highRiskCriticalSla, Duration.ofMinutes(30));
    }

    /**
     * 返回一般高风险或拒绝事件的默认处理时限。
     *
     * @return 高风险事件 SLA
     */
    public Duration getHighRiskEventSla() {
        return highRiskEventSla;
    }

    /**
     * 设置一般高风险或拒绝事件处理时限，无效值回退为两小时。
     *
     * @param highRiskEventSla 高风险事件 SLA
     */
    public void setHighRiskEventSla(Duration highRiskEventSla) {
        this.highRiskEventSla = positiveDuration(highRiskEventSla, Duration.ofHours(2));
    }

    /**
     * 返回单轮来源同步允许扫描的最大记录数。
     *
     * @return 同步扫描上限
     */
    public int getMaxSyncRows() {
        return maxSyncRows;
    }

    /**
     * 设置单轮来源同步扫描上限，超出安全范围时回退为一万条。
     *
     * @param maxSyncRows 同步扫描上限
     */
    public void setMaxSyncRows(int maxSyncRows) {
        this.maxSyncRows = boundedRows(maxSyncRows, 10000);
    }

    /**
     * 返回列表、汇总及导出查询允许读取的最大记录数。
     *
     * @return 查询读取上限
     */
    public int getMaxQueryRows() {
        return maxQueryRows;
    }

    /**
     * 设置列表、汇总及导出查询上限，超出安全范围时回退为一万条。
     *
     * @param maxQueryRows 查询读取上限
     */
    public void setMaxQueryRows(int maxQueryRows) {
        this.maxQueryRows = boundedRows(maxQueryRows, 10000);
    }

    private Duration positiveDuration(Duration candidate, Duration fallback) {
        return candidate == null || candidate.isZero() || candidate.isNegative() ? fallback : candidate;
    }

    private int boundedRows(int candidate, int fallback) {
        return candidate < 100 || candidate > 50000 ? fallback : candidate;
    }
}
