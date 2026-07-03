package com.scott.payment.admin.api.channel;

import com.scott.payment.admin.application.channel.AdminChannelApplicationService;
import com.scott.payment.admin.dto.channel.ChannelDTOs.AccessQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.AccessResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.AccessSaveRequest;
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

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理后台渠道接入配置接口。
 *
 * <p>查询响应只返回敏感字段掩码，新增和修改请求中的敏感值由服务层加密保存。</p>
 */
@RestController
@RequestMapping("/admin/channel/access-configs")
public class AdminChannelAccessController {

    private final AdminChannelApplicationService channelApplicationService;

    public AdminChannelAccessController(AdminChannelApplicationService channelApplicationService) {
        this.channelApplicationService = channelApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("channel:access:list")
    public CommonResult<PageResult<AccessResponse>> pageAccessConfigs(@RequestBody(required = false) AccessQuery query) {
        return success(channelApplicationService.pageAccessConfigs(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission("channel:access:detail")
    public CommonResult<AccessResponse> getAccessConfig(@PathVariable("id") Long id) {
        return success(channelApplicationService.getAccessConfig(id));
    }

    @PostMapping
    @RequiresPermission("channel:access:add")
    @OperationLog(moduleName = "渠道接入配置管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道接入配置")
    public CommonResult<AccessResponse> createAccessConfig(@Valid @RequestBody AccessSaveRequest request) {
        return success(channelApplicationService.createAccessConfig(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("channel:access:edit")
    @OperationLog(moduleName = "渠道接入配置管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道接入配置")
    public CommonResult<AccessResponse> updateAccessConfig(@PathVariable("id") Long id,
                                                           @Valid @RequestBody AccessSaveRequest request) {
        return success(channelApplicationService.updateAccessConfig(id, request));
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("channel:access:status")
    @OperationLog(moduleName = "渠道接入配置管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道接入配置状态")
    public CommonResult<AccessResponse> updateAccessConfigStatus(@PathVariable("id") Long id,
                                                                 @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateAccessConfigStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("channel:access:remove")
    @OperationLog(moduleName = "渠道接入配置管理", businessType = OperationTypeConstants.DELETE, operation = "删除渠道接入配置")
    public CommonResult<Void> deleteAccessConfig(@PathVariable("id") Long id) {
        channelApplicationService.deleteAccessConfig(id);
        return success();
    }
}
