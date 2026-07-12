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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelCapabilityController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 渠道管理Admin Channel Capability 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/channel/capabilities")
public class AdminChannelCapabilityController {

    /**
     * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final AdminChannelApplicationService channelApplicationService;

    public AdminChannelCapabilityController(AdminChannelApplicationService channelApplicationService) {
        this.channelApplicationService = channelApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("channel:capability:list")
    public CommonResult<PageResult<CapabilityResponse>> pageCapabilities(@RequestBody(required = false) CapabilityQuery query) {
        return success(channelApplicationService.pageCapabilities(query));
    }

    /**
     * 获取渠道管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/{id}")
    @RequiresPermission("channel:capability:detail")
    public CommonResult<CapabilityResponse> getCapability(@PathVariable("id") Long id) {
        return success(channelApplicationService.getCapability(id));
    }

    /**
     * 创建或保存渠道管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping
    @RequiresPermission("channel:capability:add")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道支付能力")
    public CommonResult<CapabilityResponse> createCapability(@Valid @RequestBody CapabilitySaveRequest request) {
        return success(channelApplicationService.createCapability(request));
    }

    /**
     * 更新渠道管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}")
    @RequiresPermission("channel:capability:edit")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道支付能力")
    public CommonResult<CapabilityResponse> updateCapability(@PathVariable("id") Long id,
                                                             @Valid @RequestBody CapabilitySaveRequest request) {
        return success(channelApplicationService.updateCapability(id, request));
    }

    /**
     * 更新渠道管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("channel:capability:status")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道支付能力状态")
    public CommonResult<CapabilityResponse> updateCapabilityStatus(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateCapabilityStatus(id, request.getStatus()));
    }

    /**
     * 更新渠道管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 删除渠道管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("channel:capability:remove")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.DELETE, operation = "删除渠道支付能力")
    public CommonResult<Void> deleteCapability(@PathVariable("id") Long id) {
        channelApplicationService.deleteCapability(id);
        return success();
    }
}
