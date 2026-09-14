package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertActionRequest;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertDetailResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertSearchResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ApiMonitorQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ApiMonitorResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ChannelMonitorQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ChannelMonitorResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.LogSearchQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.LogSearchResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.OverviewResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ProviderCapability;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.RuntimeResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.SecurityStatisticsResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ServiceInventoryResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TimeRangeQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TraceResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TraceSearchQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.WebhookMonitorQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.WebhookMonitorResponse;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMonitorWorkbenchService
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 系统监控工作台服务契约，定义监控查询、能力声明和带乐观锁的告警人工处理边界。
 * @status : create
 */
public interface AdminMonitorWorkbenchService {

    /**
     * 聚合系统监控总览。
     *
     * @param query 时间范围条件；允许为空
     * @return 系统监控总览
     */
    OverviewResponse overview(TimeRangeQuery query);

    /**
     * 查询注册中心服务与实例清单。
     *
     * @return 注册中心服务与实例清单
     */
    ServiceInventoryResponse services();

    /**
     * 查询当前 Admin JVM 运行时采样。
     *
     * @param query 时间范围条件；允许为空
     * @return 当前 JVM 运行时采样
     */
    RuntimeResponse runtime(TimeRangeQuery query);

    /**
     * 查询监控提供方能力清单。
     *
     * @return 当前监控提供方能力清单
     */
    List<ProviderCapability> capabilities();

    /**
     * 聚合 API 监控指标。
     *
     * @param query API 监控查询；允许为空
     * @return API 聚合监控结果
     */
    ApiMonitorResponse searchApis(ApiMonitorQuery query);

    /**
     * 查询单笔交易结构化链路。
     *
     * @param query 交易链路定位条件
     * @return 脱敏后的交易链路详情
     */
    TraceResponse searchTrace(TraceSearchQuery query);

    /**
     * 聚合渠道监控指标。
     *
     * @param query 渠道监控查询；允许为空
     * @return 渠道聚合监控结果
     */
    ChannelMonitorResponse searchChannels(ChannelMonitorQuery query);

    /**
     * 聚合商户通知监控指标。
     *
     * @param query Webhook 监控查询；允许为空
     * @return 商户通知聚合监控结果
     */
    WebhookMonitorResponse searchWebhooks(WebhookMonitorQuery query);

    /**
     * 查询脱敏后的结构化业务日志。
     *
     * @param query 结构化日志查询；允许为空
     * @return 脱敏日志分页结果
     */
    LogSearchResponse searchLogs(LogSearchQuery query);

    /**
     * 查询统一告警。
     *
     * @param query 告警查询；允许为空
     * @return 告警聚合与分页结果
     */
    AlertSearchResponse searchAlerts(AlertQuery query);

    /**
     * 查询单个来源告警详情。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源告警标识
     * @return 告警详情
     */
    AlertDetailResponse alertDetail(String sourceType, String sourceId);

    /**
     * 接手指定告警并写入处理历史。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源告警标识
     * @param request 当前版本号和可选备注
     * @return 接手后的告警详情
     */
    AlertDetailResponse takeOver(String sourceType, String sourceId, AlertActionRequest request);

    /**
     * 将指定告警标记为处理中并写入处理历史。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源告警标识
     * @param request 当前版本号和可选备注
     * @return 标记处理后的告警详情
     */
    AlertDetailResponse markProcessing(String sourceType, String sourceId, AlertActionRequest request);

    /**
     * 关闭指定告警并写入处理历史。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源告警标识
     * @param request 当前版本号和可选备注
     * @return 关闭后的告警详情
     */
    AlertDetailResponse closeAlert(String sourceType, String sourceId, AlertActionRequest request);

    /**
     * 聚合安全拦截统计。
     *
     * @param query 时间范围条件；允许为空
     * @return 安全拦截聚合统计
     */
    SecurityStatisticsResponse securityStatistics(TimeRangeQuery query);
}
