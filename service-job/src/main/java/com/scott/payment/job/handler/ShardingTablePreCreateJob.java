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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Sharding Table Pre Create Job，位于 service-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
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
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param context 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
