package com.scott.payment.admin.application.monitor;

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
import com.scott.payment.admin.service.AdminMonitorWorkbenchService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMonitorWorkbenchApplicationService
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 管理端系统监控工作台用例编排服务，保持 Controller 与监控查询实现解耦，并统一暴露告警处理用例。
 * @status : create
 */
@Service
public class AdminMonitorWorkbenchApplicationService {

    /** 系统监控工作台领域服务。 */
    private final AdminMonitorWorkbenchService monitorWorkbenchService;

    /**
     * 创建系统监控工作台应用服务。
     *
     * @param monitorWorkbenchService 系统监控工作台领域服务
     */
    public AdminMonitorWorkbenchApplicationService(AdminMonitorWorkbenchService monitorWorkbenchService) {
        this.monitorWorkbenchService = monitorWorkbenchService;
    }

    /**
     * 查询系统监控总览。
     *
     * @param query 时间范围条件；允许为空
     * @return 系统监控总览
     */
    public OverviewResponse overview(TimeRangeQuery query) {
        return monitorWorkbenchService.overview(query);
    }

    /**
     * 查询注册中心服务与实例清单。
     *
     * @return 注册中心服务与实例清单
     */
    public ServiceInventoryResponse services() {
        return monitorWorkbenchService.services();
    }

    /**
     * 查询当前 Admin JVM 运行时采样。
     *
     * @param query 时间范围条件；允许为空
     * @return 当前 JVM 运行时采样
     */
    public RuntimeResponse runtime(TimeRangeQuery query) {
        return monitorWorkbenchService.runtime(query);
    }

    /**
     * 查询监控提供方能力清单。
     *
     * @return 当前监控提供方能力清单
     */
    public List<ProviderCapability> capabilities() {
        return monitorWorkbenchService.capabilities();
    }

    /**
     * 查询 API 聚合监控结果。
     *
     * @param query API 监控查询；允许为空
     * @return API 聚合监控结果
     */
    public ApiMonitorResponse searchApis(ApiMonitorQuery query) {
        return monitorWorkbenchService.searchApis(query);
    }

    /**
     * 查询单笔交易结构化链路。
     *
     * @param query 交易链路定位条件；允许为空但服务层将校验定位结果
     * @return 交易链路详情
     */
    public TraceResponse searchTrace(TraceSearchQuery query) {
        return monitorWorkbenchService.searchTrace(query);
    }

    /**
     * 查询渠道聚合监控结果。
     *
     * @param query 渠道监控查询；允许为空
     * @return 渠道聚合监控结果
     */
    public ChannelMonitorResponse searchChannels(ChannelMonitorQuery query) {
        return monitorWorkbenchService.searchChannels(query);
    }

    /**
     * 查询商户通知聚合监控结果。
     *
     * @param query Webhook 监控查询；允许为空
     * @return 商户通知聚合监控结果
     */
    public WebhookMonitorResponse searchWebhooks(WebhookMonitorQuery query) {
        return monitorWorkbenchService.searchWebhooks(query);
    }

    /**
     * 查询脱敏后的结构化业务日志。
     *
     * @param query 结构化日志查询；允许为空
     * @return 脱敏日志分页结果
     */
    public LogSearchResponse searchLogs(LogSearchQuery query) {
        return monitorWorkbenchService.searchLogs(query);
    }

    /**
     * 查询统一告警。
     *
     * @param query 告警查询；允许为空
     * @return 告警聚合与分页结果
     */
    public AlertSearchResponse searchAlerts(AlertQuery query) {
        return monitorWorkbenchService.searchAlerts(query);
    }

    /**
     * 查询单个来源告警详情。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源告警标识
     * @return 告警详情
     */
    public AlertDetailResponse alertDetail(String sourceType, String sourceId) {
        return monitorWorkbenchService.alertDetail(sourceType, sourceId);
    }

    /**
     * 接手指定告警。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源告警标识
     * @param request 当前版本号和可选备注
     * @return 接手后的告警详情
     */
    public AlertDetailResponse takeOver(String sourceType, String sourceId, AlertActionRequest request) {
        return monitorWorkbenchService.takeOver(sourceType, sourceId, request);
    }

    /**
     * 将指定告警标记为处理中。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源告警标识
     * @param request 当前版本号和可选备注
     * @return 标记处理后的告警详情
     */
    public AlertDetailResponse markProcessing(String sourceType, String sourceId, AlertActionRequest request) {
        return monitorWorkbenchService.markProcessing(sourceType, sourceId, request);
    }

    /**
     * 关闭指定告警。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源告警标识
     * @param request 当前版本号和可选备注
     * @return 关闭后的告警详情
     */
    public AlertDetailResponse closeAlert(String sourceType, String sourceId, AlertActionRequest request) {
        return monitorWorkbenchService.closeAlert(sourceType, sourceId, request);
    }

    /**
     * 查询安全拦截聚合统计。
     *
     * @param query 时间范围条件；允许为空
     * @return 安全拦截聚合统计
     */
    public SecurityStatisticsResponse securityStatistics(TimeRangeQuery query) {
        return monitorWorkbenchService.securityStatistics(query);
    }
}
