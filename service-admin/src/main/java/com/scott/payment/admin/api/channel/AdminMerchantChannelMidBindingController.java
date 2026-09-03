package com.scott.payment.admin.api.channel;

import com.scott.payment.admin.application.channel.AdminChannelApplicationService;
import com.scott.payment.admin.dto.channel.ChannelDTOs.MerchantChannelMidBindingQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.MerchantChannelMidBindingResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.MerchantChannelMidBindingSaveRequest;
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
 * @classname : AdminMerchantChannelMidBindingController
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 商户渠道 MID 绑定管理接口，位于 service-admin 接口层，用于维护商户可用渠道 MID 的启停关系。
 * @status : create
 */
@RestController
@RequestMapping("/admin/channel/mid-bindings")
public class AdminMerchantChannelMidBindingController {

    private final AdminChannelApplicationService channelApplicationService;

    public AdminMerchantChannelMidBindingController(AdminChannelApplicationService channelApplicationService) {
        this.channelApplicationService = channelApplicationService;
    }

    /**
     * 分页查询商户渠道 MID 绑定。
     *
     * @param query 查询条件
     * @return 绑定分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("channel:mid-binding:list")
    public CommonResult<PageResult<MerchantChannelMidBindingResponse>> pageBindings(
            @RequestBody(required = false) MerchantChannelMidBindingQuery query) {
        return success(channelApplicationService.pageMidBindings(query));
    }

    /**
     * 查询商户渠道 MID 绑定详情。
     *
     * @param id 绑定主键
     * @return 绑定详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("channel:mid-binding:detail")
    public CommonResult<MerchantChannelMidBindingResponse> getBinding(@PathVariable("id") Long id) {
        return success(channelApplicationService.getMidBinding(id));
    }

    /**
     * 新增商户渠道 MID 绑定。
     *
     * @param request 绑定参数
     * @return 新增后的绑定关系
     */
    @PostMapping
    @RequiresPermission("channel:mid-binding:add")
    @OperationLog(moduleName = "商户渠道MID绑定", businessType = OperationTypeConstants.CREATE, operation = "新增商户渠道MID绑定")
    public CommonResult<MerchantChannelMidBindingResponse> createBinding(
            @Valid @RequestBody MerchantChannelMidBindingSaveRequest request) {
        return success(channelApplicationService.createMidBinding(request));
    }

    /**
     * 更新商户渠道 MID 绑定。
     *
     * @param id 绑定主键
     * @param request 绑定参数
     * @return 更新后的绑定关系
     */
    @PutMapping("/{id}")
    @RequiresPermission("channel:mid-binding:edit")
    @OperationLog(moduleName = "商户渠道MID绑定", businessType = OperationTypeConstants.UPDATE, operation = "修改商户渠道MID绑定")
    public CommonResult<MerchantChannelMidBindingResponse> updateBinding(
            @PathVariable("id") Long id,
            @Valid @RequestBody MerchantChannelMidBindingSaveRequest request) {
        return success(channelApplicationService.updateMidBinding(id, request));
    }

    /**
     * 更新商户渠道 MID 绑定状态。
     *
     * @param id 绑定主键
     * @param request 状态请求
     * @return 更新后的绑定关系
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("channel:mid-binding:status")
    @OperationLog(moduleName = "商户渠道MID绑定", businessType = OperationTypeConstants.UPDATE, operation = "切换商户渠道MID绑定状态")
    public CommonResult<MerchantChannelMidBindingResponse> updateBindingStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateMidBindingStatus(id, request.getStatus()));
    }

    /**
     * 删除商户渠道 MID 绑定。
     *
     * @param id 绑定主键
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("channel:mid-binding:remove")
    @OperationLog(moduleName = "商户渠道MID绑定", businessType = OperationTypeConstants.DELETE, operation = "删除商户渠道MID绑定")
    public CommonResult<Void> deleteBinding(@PathVariable("id") Long id) {
        channelApplicationService.deleteMidBinding(id);
        return success();
    }
}
