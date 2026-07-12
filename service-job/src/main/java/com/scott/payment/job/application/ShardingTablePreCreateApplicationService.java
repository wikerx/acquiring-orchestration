package com.scott.payment.job.application;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.job.enums.JobExecuteModeEnum;
import com.scott.payment.component.job.enums.JobSchedulerModeEnum;
import com.scott.payment.component.job.enums.JobTriggerTypeEnum;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.api.internal.dto.ShardingTablePreCreateInternalRequest;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateRequest;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateResult;
import com.scott.payment.job.handler.ShardingTablePreCreateJob;
import com.scott.payment.job.service.ShardingTablePreCreateService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Sharding Table Pre Create Application 服务契约，位于 service-job 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class ShardingTablePreCreateApplicationService {

    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String MANUAL_RUN_PREFIX = "sharding-manual-";

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final ShardingTablePreCreateService shardingTablePreCreateService;

    /**
     * 创建分表预建表应用服务。
     *
     * @param shardingTablePreCreateService 分表预建表领域服务
     */
    public ShardingTablePreCreateApplicationService(ShardingTablePreCreateService shardingTablePreCreateService) {
        this.shardingTablePreCreateService = shardingTablePreCreateService;
    }

    /**
     * 执行预演或真实建表。
     *
     * @param request 内部请求
     * @param dryRun  是否只预演
     * @return 建表处理结果
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param dryRun 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public ShardingTablePreCreateResult preCreate(ShardingTablePreCreateInternalRequest request, boolean dryRun) {
        ShardingTablePreCreateInternalRequest safeRequest = request == null ? new ShardingTablePreCreateInternalRequest() : request;
        ShardingTablePreCreateRequest preCreateRequest = toPreCreateRequest(safeRequest, dryRun);
        return shardingTablePreCreateService.preCreate(preCreateRequest, buildContext(safeRequest, preCreateRequest));
    }

    private ShardingTablePreCreateRequest toPreCreateRequest(ShardingTablePreCreateInternalRequest request, boolean dryRun) {
        ShardingTablePreCreateRequest preCreateRequest = new ShardingTablePreCreateRequest();
        preCreateRequest.setDryRun(dryRun);
        preCreateRequest.setIncludeCurrentQuarter(Boolean.TRUE.equals(request.getIncludeCurrentQuarter()));
        preCreateRequest.setIncludeNextQuarter(Boolean.TRUE.equals(request.getIncludeNextQuarter()));
        preCreateRequest.setLogicalTables(request.getLogicalTables());
        preCreateRequest.setCompareSchemaIfExists(Boolean.TRUE.equals(request.getCompareSchemaIfExists()));
        return preCreateRequest;
    }

    private JobExecuteContext buildContext(ShardingTablePreCreateInternalRequest request,
                                           ShardingTablePreCreateRequest preCreateRequest) {
        JobExecuteContext context = new JobExecuteContext();
        context.setJobCode(ShardingTablePreCreateJob.JOB_CODE);
        context.setJobName("分表物理表预创建");
        context.setHandlerCode(ShardingTablePreCreateJob.HANDLER_CODE);
        context.setRunId(MANUAL_RUN_PREFIX + UUID.randomUUID());
        context.setTriggerType(JobTriggerTypeEnum.MANUAL);
        context.setSchedulerMode(JobSchedulerModeEnum.STANDALONE);
        context.setExecuteMode(JobExecuteModeEnum.SYNC);
        context.setParamsJson(JsonUtils.toJsonString(preCreateRequest));
        context.setActualTriggerTime(LocalDateTime.now());
        context.setScheduledTime(LocalDateTime.now());
        context.setRetryIndex(0);
        context.setMaxRetryCount(0);
        context.setOperatorId(trimToNull(request.getOperatorId()));
        context.setOperatorName(trimToNull(request.getOperatorName()));
        context.setExecutorNode("admin-manual");
        context.setTraceId(context.getRunId());
        return context;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
