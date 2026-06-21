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
 * 分表治理内部接口。
 *
 * <p>该入口仅供管理后台通过内部鉴权链路调用，真实 DDL 执行仍由 service-job 统一编排和记录。</p>
 */
@RestController
@RequestMapping("/internal/job/sharding")
public class ShardingTableInternalController {

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
