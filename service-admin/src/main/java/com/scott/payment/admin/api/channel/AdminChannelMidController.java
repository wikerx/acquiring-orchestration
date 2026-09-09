package com.scott.payment.admin.api.channel;

import com.scott.payment.admin.application.channel.AdminChannelApplicationService;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelMidConfigQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelMidConfigResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelMidConfigSaveRequest;
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
 * @classname : AdminChannelMidController
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道 MID 配置管理接口，位于 service-admin 接口层，用于维护渠道真实 MID、能力范围和渠道元数据。
 * @status : create
 */
@RestController
@RequestMapping("/admin/channel/mids")
public class AdminChannelMidController {

    private final AdminChannelApplicationService channelApplicationService;

    public AdminChannelMidController(AdminChannelApplicationService channelApplicationService) {
        this.channelApplicationService = channelApplicationService;
    }

    /**
     * 分页查询渠道 MID 配置。
     *
     * @param query 查询条件
     * @return MID 配置分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("channel:mid:list")
    public CommonResult<PageResult<ChannelMidConfigResponse>> pageMids(@RequestBody(required = false) ChannelMidConfigQuery query) {
        return success(channelApplicationService.pageMids(query));
    }

    /**
     * 查询渠道 MID 配置详情。
     *
     * @param id MID 配置主键
     * @return MID 配置详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("channel:mid:detail")
    public CommonResult<ChannelMidConfigResponse> getMid(@PathVariable("id") Long id) {
        return success(channelApplicationService.getMid(id));
    }

    /**
     * 新增渠道 MID 配置。
     *
     * @param request MID 配置参数
     * @return 新增后的 MID 配置
     */
    @PostMapping
    @RequiresPermission("channel:mid:add")
    @OperationLog(moduleName = "渠道MID管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道MID")
    public CommonResult<ChannelMidConfigResponse> createMid(@Valid @RequestBody ChannelMidConfigSaveRequest request) {
        return success(channelApplicationService.createMid(request));
    }

    /**
     * 更新渠道 MID 配置。
     *
     * @param id MID 配置主键
     * @param request MID 配置参数
     * @return 更新后的 MID 配置
     */
    @PutMapping("/{id}")
    @RequiresPermission("channel:mid:edit")
    @OperationLog(moduleName = "渠道MID管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道MID")
    public CommonResult<ChannelMidConfigResponse> updateMid(@PathVariable("id") Long id,
                                                            @Valid @RequestBody ChannelMidConfigSaveRequest request) {
        return success(channelApplicationService.updateMid(id, request));
    }

    /**
     * 更新渠道 MID 状态。
     *
     * @param id MID 配置主键
     * @param request 状态请求
     * @return 更新后的 MID 配置
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("channel:mid:status")
    @OperationLog(moduleName = "渠道MID管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道MID状态")
    public CommonResult<ChannelMidConfigResponse> updateMidStatus(@PathVariable("id") Long id,
                                                                  @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateMidStatus(id, request.getStatus()));
    }

    /**
     * 删除渠道 MID 配置。
     *
     * @param id MID 配置主键
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("channel:mid:remove")
    @OperationLog(moduleName = "渠道MID管理", businessType = OperationTypeConstants.DELETE, operation = "删除渠道MID")
    public CommonResult<Void> deleteMid(@PathVariable("id") Long id) {
        channelApplicationService.deleteMid(id);
        return success();
    }
}
