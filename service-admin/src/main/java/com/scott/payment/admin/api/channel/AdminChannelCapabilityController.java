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
 * @date : 2026-07-03 16:10
 * @email : scott_x@163.com
 * @description : admin渠道capability HTTP 控制器，位于 运营后台服务，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。
 * @status : create
 */
@RestController
@RequestMapping("/admin/channel/capabilities")
public class AdminChannelCapabilityController {

    private final AdminChannelApplicationService channelApplicationService;

    public AdminChannelCapabilityController(AdminChannelApplicationService channelApplicationService) {
        this.channelApplicationService = channelApplicationService;
    }

    /**
     * 分页查询渠道在交易类型、支付方式和币种维度的处理能力。
     *
     * @param query 渠道能力可选查询条件
     * @return 渠道能力分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("channel:capability:list")
    public CommonResult<PageResult<CapabilityResponse>> pageCapabilities(@RequestBody(required = false) CapabilityQuery query) {
        return success(channelApplicationService.pageCapabilities(query));
    }

    /**
     * 查询指定渠道能力配置详情。
     *
     * @param id 渠道能力主键
     * @return 渠道能力详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("channel:capability:detail")
    public CommonResult<CapabilityResponse> getCapability(@PathVariable("id") Long id) {
        return success(channelApplicationService.getCapability(id));
    }

    /**
     * 创建渠道能力配置，维度唯一性由应用服务校验。
     *
     * @param request 渠道能力保存请求
     * @return 创建后的能力详情
     */
    @PostMapping
    @RequiresPermission("channel:capability:add")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道支付能力")
    public CommonResult<CapabilityResponse> createCapability(@Valid @RequestBody CapabilitySaveRequest request) {
        return success(channelApplicationService.createCapability(request));
    }

    /**
     * 更新指定渠道能力配置。
     *
     * @param id 渠道能力主键
     * @param request 渠道能力保存请求
     * @return 更新后的能力详情
     */
    @PutMapping("/{id}")
    @RequiresPermission("channel:capability:edit")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道支付能力")
    public CommonResult<CapabilityResponse> updateCapability(@PathVariable("id") Long id,
                                                             @Valid @RequestBody CapabilitySaveRequest request) {
        return success(channelApplicationService.updateCapability(id, request));
    }

    /**
     * 切换渠道能力启停状态。
     *
     * @param id 渠道能力主键
     * @param request 目标状态请求
     * @return 更新后的能力详情
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("channel:capability:status")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道支付能力状态")
    public CommonResult<CapabilityResponse> updateCapabilityStatus(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateCapabilityStatus(id, request.getStatus()));
    }

    /**
     * 更新 3DS 与增量授权支持标记，不改变能力的其他交易维度。
     *
     * @param id 渠道能力主键
     * @param request 能力支持项请求
     * @return 更新后的能力详情
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
     * 删除指定渠道能力配置。
     *
     * @param id 渠道能力主键
     * @return 无业务数据的成功响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("channel:capability:remove")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.DELETE, operation = "删除渠道支付能力")
    public CommonResult<Void> deleteCapability(@PathVariable("id") Long id) {
        channelApplicationService.deleteCapability(id);
        return success();
    }
}
