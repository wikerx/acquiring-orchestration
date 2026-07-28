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

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateApplicationService
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Table Pre Create Application Service 应用服务，位于 调度任务服务，编排控制器入参、登录或商户上下文、领域服务调用和响应模型组装。
 * @status : create
 */
public class ShardingTablePreCreateApplicationService {

    /**
     * MANUAL RUN PREFIX，用于保存 Sharding Table Pre Create Application Service 中与 manualrunprefix 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String MANUAL_RUN_PREFIX = "sharding-manual-";

    /**
     * sharding Table Pre Create Service 依赖，用于 Sharding Table Pre Create Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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
    public ShardingTablePreCreateResult preCreate(ShardingTablePreCreateInternalRequest request, boolean dryRun) {
        ShardingTablePreCreateInternalRequest safeRequest = request == null ? new ShardingTablePreCreateInternalRequest() : request;
        ShardingTablePreCreateRequest preCreateRequest = toPreCreateRequest(safeRequest, dryRun);
        return shardingTablePreCreateService.preCreate(preCreateRequest, buildContext(safeRequest, preCreateRequest));
    }

    /**
     * 构造precreate请求对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @param dryRun dry Run 输入值，参与 dryrun 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private ShardingTablePreCreateRequest toPreCreateRequest(ShardingTablePreCreateInternalRequest request, boolean dryRun) {
        ShardingTablePreCreateRequest preCreateRequest = new ShardingTablePreCreateRequest();
        preCreateRequest.setDryRun(dryRun);
        preCreateRequest.setIncludeCurrentQuarter(Boolean.TRUE.equals(request.getIncludeCurrentQuarter()));
        preCreateRequest.setIncludeNextQuarter(Boolean.TRUE.equals(request.getIncludeNextQuarter()));
        preCreateRequest.setLogicalTables(request.getLogicalTables());
        preCreateRequest.setCompareSchemaIfExists(Boolean.TRUE.equals(request.getCompareSchemaIfExists()));
        return preCreateRequest;
    }

/**
 * 构造context对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 调度任务服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param preCreateRequest pre Create Request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @return 构造、转换或解析后的业务值
 */
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

    /**
     * 规范化trimtonull，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 调度任务服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
