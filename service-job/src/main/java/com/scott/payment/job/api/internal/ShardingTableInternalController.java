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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTableInternalController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Sharding Table Internal 管理接口，位于 service-job 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/internal/job/sharding")
public class ShardingTableInternalController {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
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
