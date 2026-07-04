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
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelLimitController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 渠道管理Admin Channel Limit 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/channel/limits")
public class AdminChannelLimitController {

    /**
     * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final AdminChannelApplicationService channelApplicationService;

    public AdminChannelLimitController(AdminChannelApplicationService channelApplicationService) {
        this.channelApplicationService = channelApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("channel:limit:list")
    public CommonResult<PageResult<LimitResponse>> pageLimits(@RequestBody(required = false) LimitQuery query) {
        return success(channelApplicationService.pageLimits(query));
    }

    /**
     * 获取渠道管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/{id}")
    @RequiresPermission("channel:limit:detail")
    public CommonResult<LimitResponse> getLimit(@PathVariable("id") Long id) {
        return success(channelApplicationService.getLimit(id));
    }

    /**
     * 创建或保存渠道管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping
    @RequiresPermission("channel:limit:add")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道限额")
    public CommonResult<LimitResponse> createLimit(@Valid @RequestBody LimitSaveRequest request) {
        return success(channelApplicationService.createLimit(request));
    }

    /**
     * 创建或保存渠道管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/batch")
    @RequiresPermission("channel:limit:add")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.CREATE, operation = "批量新增渠道限额")
    public CommonResult<List<LimitResponse>> createLimits(@Valid @RequestBody LimitBatchSaveRequest request) {
        return success(channelApplicationService.createLimits(request));
    }

    /**
     * 创建或保存渠道管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/dimension")
    @RequiresPermission("channel:limit:edit")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.UPDATE, operation = "维度编辑渠道限额")
    public CommonResult<List<LimitResponse>> saveLimitDimension(@Valid @RequestBody LimitBatchSaveRequest request) {
        return success(channelApplicationService.saveLimitDimension(request));
    }

    /**
     * 更新渠道管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}")
    @RequiresPermission("channel:limit:edit")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道限额")
    public CommonResult<LimitResponse> updateLimit(@PathVariable("id") Long id,
                                                   @Valid @RequestBody LimitSaveRequest request) {
        return success(channelApplicationService.updateLimit(id, request));
    }

    /**
     * 更新渠道管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("channel:limit:status")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道限额状态")
    public CommonResult<LimitResponse> updateLimitStatus(@PathVariable("id") Long id,
                                                         @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateLimitStatus(id, request.getStatus()));
    }

    /**
     * 删除渠道管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("channel:limit:remove")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.DELETE, operation = "删除渠道限额")
    public CommonResult<Void> deleteLimit(@PathVariable("id") Long id) {
        channelApplicationService.deleteLimit(id);
        return success();
    }
}
