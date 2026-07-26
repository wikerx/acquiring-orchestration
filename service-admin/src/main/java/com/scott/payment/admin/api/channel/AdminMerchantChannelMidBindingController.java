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

    /**
     * channel Application Service 依赖，用于 Admin Merchant Channel MID Binding Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminChannelApplicationService channelApplicationService;

    /**
     * 整理admin商户渠道midbindingcontroller，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param channelApplicationService channel Application Service 输入值，参与 渠道applicationservice 的查询、校验、转换、写入或日志摘要
     */
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
