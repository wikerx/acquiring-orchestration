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

/**
 * 管理后台渠道限额规则接口。
 */
@RestController
@RequestMapping("/admin/channel/limits")
public class AdminChannelLimitController {

    private final AdminChannelApplicationService channelApplicationService;

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
