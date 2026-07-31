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

@RestController
@RequestMapping("/admin/channel/info")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelInfoController
 * @date : 2026-07-03 16:10
 * @email : scott_x@163.com
 * @description : Admin Channel Info Controller 控制器，位于 运营后台服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
public class AdminChannelInfoController {

    /**
     * channel Application Service 依赖，用于 Admin Channel Info Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminChannelApplicationService channelApplicationService;

    /**
     * 整理admin渠道信息controller，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param channelApplicationService channel Application Service 输入值，参与 渠道applicationservice 的查询、校验、转换、写入或日志摘要
     */
    public AdminChannelInfoController(AdminChannelApplicationService channelApplicationService) {
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
        channelApplicationService.deleteChannel(id);
        return success();
    }
}
