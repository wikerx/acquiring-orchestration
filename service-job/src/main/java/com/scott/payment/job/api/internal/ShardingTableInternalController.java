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
 * @description : Sharding Table Internal Controller 控制器，位于 调度任务服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
public class ShardingTableInternalController {

    /**
     * sharding Table Pre Create Application Service 依赖，用于 Sharding Table Internal Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
