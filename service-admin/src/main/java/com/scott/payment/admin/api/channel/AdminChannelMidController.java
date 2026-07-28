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

    /**
     * channel Application Service 依赖，用于 Admin Channel MID Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminChannelApplicationService channelApplicationService;

    /**
     * 整理admin渠道midcontroller，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param channelApplicationService channel Application Service 输入值，参与 渠道applicationservice 的查询、校验、转换、写入或日志摘要
     */
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
