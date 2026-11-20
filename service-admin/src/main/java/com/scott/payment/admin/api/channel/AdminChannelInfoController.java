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
 * 管理端渠道信息 HTTP 控制器，用于运营后台，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。
 */
@RestController
@RequestMapping("/admin/channel/info")
public class AdminChannelInfoController {

    private final AdminChannelApplicationService channelApplicationService;
    private final com.scott.payment.admin.application.channel.AdminChannelImageApplicationService channelImages;

    /**
     * 创建渠道基础信息管理控制器。
     *
     * @param channelApplicationService 渠道配置应用服务
     */
    public AdminChannelInfoController(AdminChannelApplicationService channelApplicationService,
            com.scott.payment.admin.application.channel.AdminChannelImageApplicationService channelImages) {
        this.channelImages = channelImages;
        this.channelApplicationService = channelApplicationService;
    }

    /**
     * 按管理端条件分页查询渠道基础资料。
     *
     * @param query 渠道编号、名称、类型、状态等可选查询条件
     * @return 渠道分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("channel:info:list")
    public CommonResult<PageResult<ChannelInfoResponse>> pageChannels(@RequestBody(required = false) ChannelInfoQuery query) {
        return success(channelApplicationService.pageChannels(query));
    }

    /**
     * 查询下拉选择使用的启用渠道简要信息。
     *
     * @return 渠道选项列表
     */
    @GetMapping("/options")
    @RequiresPermission("channel:info:list")
    public CommonResult<List<ChannelOption>> channelOptions() {
        return success(channelApplicationService.listChannelOptions());
    }

    /**
     * 查询指定渠道的管理端详情。
     *
     * @param id 渠道主键
     * @return 渠道详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("channel:info:detail")
    public CommonResult<ChannelInfoResponse> getChannel(@PathVariable("id") Long id) {
        return success(channelApplicationService.getChannel(id));
    }

    /**
     * 创建渠道基础资料，渠道编码唯一性和必填配置由应用服务校验。
     *
     * @param request 渠道保存请求
     * @return 创建后的渠道详情
     */
    @PostMapping
    @RequiresPermission("channel:info:add")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道")
    public CommonResult<ChannelInfoResponse> createChannel(@Valid @RequestBody ChannelInfoSaveRequest request) {
        return success(channelApplicationService.createChannel(request));
    }

    /**
     * 更新指定渠道基础资料，不在接口层修改渠道能力或商户 MID。
     *
     * @param id 渠道主键
     * @param request 渠道保存请求
     * @return 更新后的渠道详情
     */
    @PutMapping("/{id}")
    @RequiresPermission("channel:info:edit")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道")
    public CommonResult<ChannelInfoResponse> updateChannel(@PathVariable("id") Long id,
                                                           @Valid @RequestBody ChannelInfoSaveRequest request) {
        return success(channelApplicationService.updateChannel(id, request));
    }

    /**
     * 切换渠道启停状态，状态合法性及关联使用约束由应用服务处理。
     *
     * @param id 渠道主键
     * @param request 目标状态请求
     * @return 更新后的渠道详情
     */
    @PutMapping("/{id}/status")
    @RequiresPermission("channel:info:status")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道状态")
    public CommonResult<ChannelInfoResponse> updateChannelStatus(@PathVariable("id") Long id,
                                                                 @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateChannelStatus(id, request.getStatus()));
    }

    /**
     * 删除指定渠道；存在关联配置时由应用服务拒绝删除。
     *
     * @param id 渠道主键
     * @return 无业务数据的成功响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("channel:info:remove")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.DELETE, operation = "删除渠道")
    public CommonResult<Void> deleteChannel(@PathVariable("id") Long id) {
        channelImages.delete(id);
        return success();
    }

    /** 以新增权限接收渠道资料与可选 Logo，图片必须经过统一上传安全链路。 */
    @PostMapping(value = "/with-logo", consumes = "multipart/form-data")
    @RequiresPermission("channel:info:add")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道及 Logo")
    public CommonResult<ChannelInfoResponse> createWithLogo(
            @Valid @org.springframework.web.bind.annotation.RequestPart("metadata") ChannelInfoSaveRequest request,
            @org.springframework.web.bind.annotation.RequestPart(value = "file", required = false) org.springframework.web.multipart.MultipartFile file) {
        return success(channelImages.save(null, request, file, false));
    }
    /** 以编辑权限更新渠道及 Logo，移除与替换由同一事务协调。 */
    @PutMapping(value = "/{id}/with-logo", consumes = "multipart/form-data")
    @RequiresPermission("channel:info:edit")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道及 Logo")
    public CommonResult<ChannelInfoResponse> updateWithLogo(@PathVariable("id") Long id,
            @Valid @org.springframework.web.bind.annotation.RequestPart("metadata") ChannelInfoSaveRequest request,
            @org.springframework.web.bind.annotation.RequestPart(value = "file", required = false) org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam(value = "removeLogo", defaultValue = "false") boolean removeLogo) {
        return success(channelImages.save(id, request, file, removeLogo));
    }
    /** 按渠道可见范围返回受管 Logo 正文，响应不暴露对象存储地址。 */
    @GetMapping("/{id}/logo")
    @RequiresPermission("channel:info:list")
    public org.springframework.http.ResponseEntity<byte[]> logo(@PathVariable("id") Long id) {
        return com.scott.payment.component.web.upload.ImageResponses.png(channelImages.read(id));
    }
}
