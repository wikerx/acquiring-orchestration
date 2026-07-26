package com.scott.payment.admin.api.channel;

import com.scott.payment.admin.application.channel.AdminChannelApplicationService;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitBatchSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.LimitSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.StatusRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

@RestController
@RequestMapping("/admin/channel/limits")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelLimitController
 * @date : 2026-07-03 16:10
 * @email : scott_x@163.com
 * @description : AdminChannelLimitController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminChannelLimitController {

    /**
     * channel Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminChannelApplicationService channelApplicationService;

    /**
     * 创建 AdminChannelLimitController 实例并注入其运行所需依赖。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminChannelLimitController 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param channelApplicationService channel Application Service 输入值，含义由调用方法名称和所属业务对象限定
     */
    public AdminChannelLimitController(AdminChannelApplicationService channelApplicationService) {
        this.channelApplicationService = channelApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("channel:limit:list")
    public CommonResult<PageResult<LimitResponse>> pageLimits(@RequestBody(required = false) LimitQuery query) {
        return success(channelApplicationService.pageLimits(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission("channel:limit:detail")
    public CommonResult<LimitResponse> getLimit(@PathVariable("id") Long id) {
        return success(channelApplicationService.getLimit(id));
    }

    @PostMapping
    @RequiresPermission("channel:limit:add")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道限额")
    public CommonResult<LimitResponse> createLimit(@Valid @RequestBody LimitSaveRequest request) {
        return success(channelApplicationService.createLimit(request));
    }

    @PostMapping("/batch")
    @RequiresPermission("channel:limit:add")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.CREATE, operation = "批量新增渠道限额")
    public CommonResult<List<LimitResponse>> createLimits(@Valid @RequestBody LimitBatchSaveRequest request) {
        return success(channelApplicationService.createLimits(request));
    }

    @PutMapping("/dimension")
    @RequiresPermission("channel:limit:edit")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.UPDATE, operation = "维度编辑渠道限额")
    public CommonResult<List<LimitResponse>> saveLimitDimension(@Valid @RequestBody LimitBatchSaveRequest request) {
        return success(channelApplicationService.saveLimitDimension(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("channel:limit:edit")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道限额")
    public CommonResult<LimitResponse> updateLimit(@PathVariable("id") Long id,
                                                   @Valid @RequestBody LimitSaveRequest request) {
        return success(channelApplicationService.updateLimit(id, request));
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("channel:limit:status")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道限额状态")
    public CommonResult<LimitResponse> updateLimitStatus(@PathVariable("id") Long id,
                                                         @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateLimitStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("channel:limit:remove")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.DELETE, operation = "删除渠道限额")
    public CommonResult<Void> deleteLimit(@PathVariable("id") Long id) {
        channelApplicationService.deleteLimit(id);
        return success();
    }
}
