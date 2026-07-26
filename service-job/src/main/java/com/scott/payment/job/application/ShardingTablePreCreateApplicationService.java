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
 * @description : ShardingTablePreCreateApplicationService 应用服务，用于编排接口请求、权限上下文、领域服务和外部依赖，位于 调度任务服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class ShardingTablePreCreateApplicationService {

    /**
     * MANUAL RUN PREFIX 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String MANUAL_RUN_PREFIX = "sharding-manual-";

    /**
     * sharding Table Pre Create Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
     * 转换生成 to Pre Create Request 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param dryRun dry Run 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
 * 构建 build Context 对应的领域对象、请求对象或日志对象。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @param preCreateRequest pre Create Request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 转换或构建后的目标对象
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
     * 完成 trim To Null 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
