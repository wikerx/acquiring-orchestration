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
 * 调度分片物理表预创建，并将结构不一致或建表失败转换为任务失败结果。
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
