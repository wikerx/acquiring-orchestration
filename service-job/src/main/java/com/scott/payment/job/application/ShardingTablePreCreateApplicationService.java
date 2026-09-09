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
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : 分表表precreate应用服务，位于 调度任务服务，编排可信登录上下文、权限、领域服务调用和响应模型组装。
 * @status : create
 */
@Service
public class ShardingTablePreCreateApplicationService {

    /**
     * {@code MANUAL_RUN_PREFIX}常量，统一 {@code ShardingTablePreCreateApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String MANUAL_RUN_PREFIX = "sharding-manual-";

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
