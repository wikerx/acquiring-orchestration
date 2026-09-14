package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.application.monitor.AdminMonitorWorkbenchApplicationService;
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
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMonitorWorkbenchController
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : 系统监控工作台 HTTP 入口，负责权限、请求校验和统一响应封装，不承载指标查询或告警状态流转规则。
 * @status : create
 */
@RestController
@RequestMapping("/admin/monitor/workbench")
public class AdminMonitorWorkbenchController {

    /** 系统监控工作台用例编排服务。 */
    private final AdminMonitorWorkbenchApplicationService applicationService;

    /**
     * 创建系统监控工作台控制器。
     *
     * @param applicationService 系统监控工作台用例编排服务
     */
    public AdminMonitorWorkbenchController(AdminMonitorWorkbenchApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询系统监控总览。
     *
     * @param query 时间范围和时区；为空时查询最近二十四小时
     * @return 服务、API、告警和依赖状态聚合结果
     */
    @PostMapping("/overview")
    @RequiresPermission("system:monitor:overview:query")
    public CommonResult<OverviewResponse> overview(@RequestBody(required = false) TimeRangeQuery query) {
        return success(applicationService.overview(query));
    }

    /**
     * 查询注册中心服务与实例清单。
     *
     * @return 已脱敏的服务实例和能力状态
     */
    @GetMapping("/services")
    @RequiresPermission("system:monitor:service:query")
    public CommonResult<ServiceInventoryResponse> services() {
        return success(applicationService.services());
    }

    /**
     * 查询当前 Admin JVM 的运行时采样。
     *
     * @param query 时间范围和时区；为空时查询最近二十四小时
     * @return 当前采样、进程内历史和线程池能力状态
     */
    @PostMapping("/runtime")
    @RequiresPermission("system:monitor:service:query")
    public CommonResult<RuntimeResponse> runtime(@RequestBody(required = false) TimeRangeQuery query) {
        return success(applicationService.runtime(query));
    }

    /**
     * 查询监控提供方能力清单。
     *
     * @return 注册中心、JVM、Prometheus、日志、APM 和 RocketMQ 能力状态
     */
    @GetMapping("/capabilities")
    @RequiresPermission("system:monitor:overview:query")
    public CommonResult<List<ProviderCapability>> capabilities() {
        return success(applicationService.capabilities());
    }

    /**
     * 查询商户 API 聚合指标。
     *
     * @param query API 筛选、时间范围和分页条件；为空时使用默认条件
     * @return API 趋势、TopN 和分页聚合结果
     */
    @PostMapping("/apis/search")
    @RequiresPermission("system:monitor:api:query")
    public CommonResult<ApiMonitorResponse> searchApis(@RequestBody(required = false) ApiMonitorQuery query) {
        return success(applicationService.searchApis(query));
    }

    /**
     * 定位并查询单笔交易结构化链路。
     *
     * @param query 交易号、商户订单号、渠道订单号或 TraceId 定位条件
     * @return 脱敏后的交易摘要、事件时间线和瀑布图数据
     */
    @PostMapping("/traces/search")
    @RequiresPermission("system:monitor:trace:query")
    public CommonResult<TraceResponse> searchTrace(@RequestBody(required = false) TraceSearchQuery query) {
        return success(applicationService.searchTrace(query));
    }

    /**
     * 查询渠道交互聚合指标。
     *
     * @param query 渠道筛选、时间范围和分页条件；为空时使用默认条件
     * @return 渠道成功率、延迟、错误分布和分页聚合结果
     */
    @PostMapping("/channels/search")
    @RequiresPermission("system:monitor:channel:query")
    public CommonResult<ChannelMonitorResponse> searchChannels(@RequestBody(required = false) ChannelMonitorQuery query) {
        return success(applicationService.searchChannels(query));
    }

    /**
     * 查询商户 Webhook 投递结果。
     *
     * @param query 商户、交易、事件、状态和时间范围条件；为空时使用默认条件
     * @return 投递成功率、重试趋势、HTTP 状态分布和分页结果
     */
    @PostMapping("/webhooks/search")
    @RequiresPermission("system:monitor:webhook:query")
    public CommonResult<WebhookMonitorResponse> searchWebhooks(@RequestBody(required = false) WebhookMonitorQuery query) {
        return success(applicationService.searchWebhooks(query));
    }

    /**
     * 查询脱敏后的结构化业务日志。
     *
     * @param query 业务标识、级别、服务、关键词和时间范围条件；为空时使用默认条件
     * @return 结构化日志分页结果和集中日志上下文能力状态
     */
    @PostMapping("/logs/search")
    @RequiresPermission("system:monitor:log:query")
    public CommonResult<LogSearchResponse> searchLogs(@RequestBody(required = false) LogSearchQuery query) {
        return success(applicationService.searchLogs(query));
    }

    /**
     * 查询统一告警列表。
     *
     * @param query 来源、级别、状态、负责人和时间范围条件；为空时使用默认条件
     * @return 告警摘要、趋势、来源 TopN 和分页结果
     */
    @PostMapping("/alerts/search")
    @RequiresPermission("system:monitor:alert:query")
    public CommonResult<AlertSearchResponse> searchAlerts(@RequestBody(required = false) AlertQuery query) {
        return success(applicationService.searchAlerts(query));
    }

    /**
     * 查询单个来源告警详情。
     *
     * @param sourceType 告警来源类型，例如 CHANNEL 或 SECURITY
     * @param sourceId 来源系统内的告警标识
     * @return 告警快照、人工处理历史和关联指标能力状态
     */
    @GetMapping("/alerts/{sourceType}/{sourceId}")
    @RequiresPermission("system:monitor:alert:query")
    public CommonResult<AlertDetailResponse> alertDetail(@PathVariable String sourceType,
                                                         @PathVariable String sourceId) {
        return success(applicationService.alertDetail(sourceType, sourceId));
    }

    /**
     * 接手指定告警并记录审计历史。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源系统内的告警标识
     * @param request 当前版本号和可选处理备注
     * @return 更新后的告警详情
     */
    @PutMapping("/alerts/{sourceType}/{sourceId}/take-over")
    @RequiresPermission("system:monitor:alert:handle")
    @OperationLog(moduleName = "系统监控告警", businessType = OperationTypeConstants.UPDATE, operation = "接手告警")
    public CommonResult<AlertDetailResponse> takeOver(@PathVariable String sourceType,
                                                      @PathVariable String sourceId,
                                                      @Valid @RequestBody AlertActionRequest request) {
        return success(applicationService.takeOver(sourceType, sourceId, request));
    }

    /**
     * 将指定告警标记为处理中并记录审计历史。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源系统内的告警标识
     * @param request 当前版本号和可选处理备注
     * @return 更新后的告警详情
     */
    @PutMapping("/alerts/{sourceType}/{sourceId}/processing")
    @RequiresPermission("system:monitor:alert:handle")
    @OperationLog(moduleName = "系统监控告警", businessType = OperationTypeConstants.UPDATE, operation = "标记告警处理中")
    public CommonResult<AlertDetailResponse> markProcessing(@PathVariable String sourceType,
                                                            @PathVariable String sourceId,
                                                            @Valid @RequestBody AlertActionRequest request) {
        return success(applicationService.markProcessing(sourceType, sourceId, request));
    }

    /**
     * 关闭指定告警并记录审计历史。
     *
     * @param sourceType 告警来源类型
     * @param sourceId 来源系统内的告警标识
     * @param request 当前版本号和可选关闭备注
     * @return 更新后的告警详情
     */
    @PutMapping("/alerts/{sourceType}/{sourceId}/close")
    @RequiresPermission("system:monitor:alert:handle")
    @OperationLog(moduleName = "系统监控告警", businessType = OperationTypeConstants.UPDATE, operation = "关闭告警")
    public CommonResult<AlertDetailResponse> closeAlert(@PathVariable String sourceType,
                                                        @PathVariable String sourceId,
                                                        @Valid @RequestBody AlertActionRequest request) {
        return success(applicationService.closeAlert(sourceType, sourceId, request));
    }

    /**
     * 查询安全拦截聚合统计。
     *
     * @param query 时间范围和时区；为空时查询最近二十四小时
     * @return 安全事件摘要、趋势、类型 TopN 和商户 TopN
     */
    @PostMapping("/security/statistics")
    @RequiresPermission("system:monitor:security:query")
    public CommonResult<SecurityStatisticsResponse> securityStatistics(
            @RequestBody(required = false) TimeRangeQuery query) {
        return success(applicationService.securityStatistics(query));
    }
}
