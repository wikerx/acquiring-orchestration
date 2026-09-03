package com.scott.payment.job.handler;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateRequest;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateResult;
import com.scott.payment.job.service.ShardingTablePreCreateService;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateJob
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Table Pre Create Job 任务组件，位于 调度任务服务，执行定时扫描、分片调度、补偿处理或后台同步，并记录任务执行结果。
 * @status : create
 */
@Component
public class ShardingTablePreCreateJob implements JobHandler {

    /**
     * 调度任务编码，和 sys_job_task.job_code 保持一致。
     */
    public static final String JOB_CODE = "SHARDING_TABLE_PRE_CREATE";

    /**
     * 任务处理器编码。
     */
    public static final String HANDLER_CODE = "shardingTablePreCreate";

    /**
     * {@code shardingTablePreCreateService} 依赖，用于 {@code ShardingTablePreCreateJob} 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * </p>
     */
    private final ShardingTablePreCreateService shardingTablePreCreateService;

    /**
     * 创建分表预建表任务处理器。
     *
     * @param shardingTablePreCreateService 分表预建表服务
     */
    public ShardingTablePreCreateJob(ShardingTablePreCreateService shardingTablePreCreateService) {
        this.shardingTablePreCreateService = shardingTablePreCreateService;
    }

    /**
     * 返回处理器注册描述。
     *
     * @return 处理器描述
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(
                HANDLER_CODE,
                "分表物理表预创建任务",
                "sharding",
                "按季度检查并预创建分表物理表，支持 dryRun 和手动执行"
        );
    }

    /**
     * 执行分表物理表预创建。
     *
     * @param context 任务执行上下文
     * @return 任务执行结果
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        if (context == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "job execute context is required");
        }
        ShardingTablePreCreateRequest request = context.parseParams(ShardingTablePreCreateRequest.class);
        if (request == null) {
            request = new ShardingTablePreCreateRequest();
        }
        ShardingTablePreCreateResult result = shardingTablePreCreateService.preCreate(request, context);
        if (!result.getFailedTables().isEmpty()) {
            return JobExecuteResult.failed(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "sharding table pre-create has failed tables: " + result.getFailedTables().size());
        }
        if (!result.getSchemaMismatchTables().isEmpty()) {
            return JobExecuteResult.failed(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "sharding table schema mismatch: " + result.getSchemaMismatchTables().size());
        }
        return JobExecuteResult.success("sharding table pre-create finished", result);
    }
}
