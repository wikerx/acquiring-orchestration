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
 * @date : 2026-07-03 16:10
 * @email : scott_x@163.com
 * @description : admin渠道limit HTTP 控制器，位于 运营后台服务，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。
 * @status : create
 */
@RestController
@RequestMapping("/admin/channel/limits")
public class AdminChannelLimitController {

    private final AdminChannelApplicationService channelApplicationService;

    public AdminChannelLimitController(AdminChannelApplicationService channelApplicationService) {
        this.channelApplicationService = channelApplicationService;
    }

    /**
     * 分页查询渠道金额或笔数限额配置。
     *
     * @param query 渠道、币种、限额类型和状态等可选条件
     * @return 渠道限额分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("channel:limit:list")
    public CommonResult<PageResult<LimitResponse>> pageLimits(@RequestBody(required = false) LimitQuery query) {
        return success(channelApplicationService.pageLimits(query));
    }

    /**
     * 查询指定渠道限额详情。
     *
     * @param id 渠道限额主键
     * @return 渠道限额详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("channel:limit:detail")
    public CommonResult<LimitResponse> getLimit(@PathVariable("id") Long id) {
        return success(channelApplicationService.getLimit(id));
    }

    /**
     * 创建单条渠道限额，金额精度、币种和上下限关系由应用服务校验。
     *
     * @param request 渠道限额保存请求，金额禁止使用浮点语义
     * @return 创建后的限额详情
     */
    @PostMapping
    @RequiresPermission("channel:limit:add")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道限额")
    public CommonResult<LimitResponse> createLimit(@Valid @RequestBody LimitSaveRequest request) {
        return success(channelApplicationService.createLimit(request));
    }

    /**
     * 批量创建同一业务维度下的渠道限额。
     *
     * @param request 批量限额保存请求
     * @return 创建后的限额列表
     */
    @PostMapping("/batch")
    @RequiresPermission("channel:limit:add")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.CREATE, operation = "批量新增渠道限额")
    public CommonResult<List<LimitResponse>> createLimits(@Valid @RequestBody LimitBatchSaveRequest request) {
        return success(channelApplicationService.createLimits(request));
    }

    /**
     * 整体保存一个渠道限额维度，缺失项的处理语义由应用服务统一控制。
     *
     * @param request 限额维度批量保存请求
     * @return 保存后的完整限额列表
     */
    @PutMapping("/dimension")
    @RequiresPermission("channel:limit:edit")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.UPDATE, operation = "维度编辑渠道限额")
    public CommonResult<List<LimitResponse>> saveLimitDimension(@Valid @RequestBody LimitBatchSaveRequest request) {
        return success(channelApplicationService.saveLimitDimension(request));
    }

    /**
     * 更新指定渠道限额，接口层不对金额执行舍入。
     *
     * @param id 渠道限额主键
     * @param request 渠道限额保存请求
     * @return 更新后的限额详情
     */
    @PutMapping("/{id}")
    @RequiresPermission("channel:limit:edit")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道限额")
    public CommonResult<LimitResponse> updateLimit(@PathVariable("id") Long id,
                                                   @Valid @RequestBody LimitSaveRequest request) {
        return success(channelApplicationService.updateLimit(id, request));
    }

    /**
     * 切换渠道限额启停状态。
     *
     * @param id 渠道限额主键
     * @param request 目标状态请求
     * @return 更新后的限额详情
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("channel:limit:status")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道限额状态")
    public CommonResult<LimitResponse> updateLimitStatus(@PathVariable("id") Long id,
                                                         @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateLimitStatus(id, request.getStatus()));
    }

    /**
     * 删除指定渠道限额配置。
     *
     * @param id 渠道限额主键
     * @return 无业务数据的成功响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("channel:limit:remove")
    @OperationLog(moduleName = "渠道限额管理", businessType = OperationTypeConstants.DELETE, operation = "删除渠道限额")
    public CommonResult<Void> deleteLimit(@PathVariable("id") Long id) {
        channelApplicationService.deleteLimit(id);
        return success();
    }
}
