package com.scott.payment.job.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.job.api.internal.dto.ShardingTablePreCreateInternalRequest;
import com.scott.payment.job.application.ShardingTablePreCreateApplicationService;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

@RestController
@RequestMapping("/internal/job/sharding")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableInternalController
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : ShardingTableInternalController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 调度任务服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class ShardingTableInternalController {

    /**
     * sharding Table Pre Create Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ShardingTablePreCreateApplicationService shardingTablePreCreateApplicationService;

    /**
     * 创建分表治理内部控制器。
     *
     * @param shardingTablePreCreateApplicationService 分表预建表应用服务
     */
    public ShardingTableInternalController(ShardingTablePreCreateApplicationService shardingTablePreCreateApplicationService) {
        this.shardingTablePreCreateApplicationService = shardingTablePreCreateApplicationService;
    }

    /**
     * 预演分表物理表预创建。
     *
     * @param request 预创建请求
     * @return 预演结果
     */
    @PostMapping("/table-create/dry-run")
    public CommonResult<ShardingTablePreCreateResult> dryRun(@RequestBody(required = false) ShardingTablePreCreateInternalRequest request) {
        return success(shardingTablePreCreateApplicationService.preCreate(request, true));
    }

    /**
     * 立即创建缺失的分表物理表。
     *
     * @param request 建表请求
     * @return 建表结果
     */
    @PostMapping("/table-create/execute")
    public CommonResult<ShardingTablePreCreateResult> execute(@RequestBody(required = false) ShardingTablePreCreateInternalRequest request) {
        return success(shardingTablePreCreateApplicationService.preCreate(request, false));
    }
}
