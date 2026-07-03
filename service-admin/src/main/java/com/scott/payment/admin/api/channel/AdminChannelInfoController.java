package com.scott.payment.admin.api.channel;

import com.scott.payment.admin.application.channel.AdminChannelApplicationService;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoQuery;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoResponse;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelInfoSaveRequest;
import com.scott.payment.admin.dto.channel.ChannelDTOs.ChannelOption;
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
 * 管理后台渠道信息接口。
 *
 * <p>负责渠道基础资料的参数接收、权限校验和 HTTP 映射，业务规则由应用服务层处理。</p>
 */
@RestController
@RequestMapping("/admin/channel/info")
public class AdminChannelInfoController {

    private final AdminChannelApplicationService channelApplicationService;

    public AdminChannelInfoController(AdminChannelApplicationService channelApplicationService) {
        this.channelApplicationService = channelApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("channel:info:list")
    public CommonResult<PageResult<ChannelInfoResponse>> pageChannels(@RequestBody(required = false) ChannelInfoQuery query) {
        return success(channelApplicationService.pageChannels(query));
    }

    @GetMapping("/options")
    @RequiresPermission("channel:info:list")
    public CommonResult<List<ChannelOption>> channelOptions() {
        return success(channelApplicationService.listChannelOptions());
    }

    @GetMapping("/{id}")
    @RequiresPermission("channel:info:detail")
    public CommonResult<ChannelInfoResponse> getChannel(@PathVariable("id") Long id) {
        return success(channelApplicationService.getChannel(id));
    }

    @PostMapping
    @RequiresPermission("channel:info:add")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道")
    public CommonResult<ChannelInfoResponse> createChannel(@Valid @RequestBody ChannelInfoSaveRequest request) {
        return success(channelApplicationService.createChannel(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("channel:info:edit")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道")
    public CommonResult<ChannelInfoResponse> updateChannel(@PathVariable("id") Long id,
                                                           @Valid @RequestBody ChannelInfoSaveRequest request) {
        return success(channelApplicationService.updateChannel(id, request));
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("channel:info:status")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道状态")
    public CommonResult<ChannelInfoResponse> updateChannelStatus(@PathVariable("id") Long id,
                                                                 @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateChannelStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("channel:info:remove")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.DELETE, operation = "删除渠道")
    public CommonResult<Void> deleteChannel(@PathVariable("id") Long id) {
        channelApplicationService.deleteChannel(id);
        return success();
    }
}
