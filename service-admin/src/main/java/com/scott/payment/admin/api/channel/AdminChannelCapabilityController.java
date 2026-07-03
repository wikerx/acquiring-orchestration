package com.scott.payment.admin.api.channel;

import com.scott.payment.admin.application.channel.AdminChannelApplicationService;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilityQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilityResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilitySaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.CapabilitySupportRequest;
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
 * 管理后台渠道支付能力接口。
 */
@RestController
@RequestMapping("/admin/channel/capabilities")
public class AdminChannelCapabilityController {

    private final AdminChannelApplicationService channelApplicationService;

    public AdminChannelCapabilityController(AdminChannelApplicationService channelApplicationService) {
        this.channelApplicationService = channelApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("channel:capability:list")
    public CommonResult<PageResult<CapabilityResponse>> pageCapabilities(@RequestBody(required = false) CapabilityQuery query) {
        return success(channelApplicationService.pageCapabilities(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission("channel:capability:detail")
    public CommonResult<CapabilityResponse> getCapability(@PathVariable("id") Long id) {
        return success(channelApplicationService.getCapability(id));
    }

    @PostMapping
    @RequiresPermission("channel:capability:add")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道支付能力")
    public CommonResult<CapabilityResponse> createCapability(@Valid @RequestBody CapabilitySaveRequest request) {
        return success(channelApplicationService.createCapability(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("channel:capability:edit")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道支付能力")
    public CommonResult<CapabilityResponse> updateCapability(@PathVariable("id") Long id,
                                                             @Valid @RequestBody CapabilitySaveRequest request) {
        return success(channelApplicationService.updateCapability(id, request));
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("channel:capability:status")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道支付能力状态")
    public CommonResult<CapabilityResponse> updateCapabilityStatus(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateCapabilityStatus(id, request.getStatus()));
    }

    @PutMapping("/{id}/support")
    @RequiresPermission("channel:capability:edit")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道支付能力支持项")
    public CommonResult<CapabilityResponse> updateCapabilitySupport(@PathVariable("id") Long id,
                                                                    @RequestBody CapabilitySupportRequest request) {
        return success(channelApplicationService.updateCapabilitySupport(
                id,
                request.getSupport3ds(),
                request.getSupportIncrementalAuthorization()
        ));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("channel:capability:remove")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.DELETE, operation = "删除渠道支付能力")
    public CommonResult<Void> deleteCapability(@PathVariable("id") Long id) {
        channelApplicationService.deleteCapability(id);
        return success();
    }
}
