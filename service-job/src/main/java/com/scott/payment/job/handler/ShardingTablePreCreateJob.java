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

@Component
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateJob
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingTablePreCreateJob 调度任务组件，用于执行定时扫描、异步任务或后台补偿流程，位于 调度任务服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
     * sharding Table Pre Create Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
