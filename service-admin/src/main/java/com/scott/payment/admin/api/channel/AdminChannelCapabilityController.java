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

@RestController
@RequestMapping("/admin/channel/capabilities")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelCapabilityController
 * @date : 2026-07-03 16:10
 * @email : scott_x@163.com
 * @description : AdminChannelCapabilityController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminChannelCapabilityController {

    /**
     * channel Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminChannelApplicationService channelApplicationService;

    /**
     * 创建 AdminChannelCapabilityController 实例并注入其运行所需依赖。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param channelApplicationService channel Application Service 输入值，含义由调用方法名称和所属业务对象限定
     */
    public AdminChannelCapabilityController(AdminChannelApplicationService channelApplicationService) {
        this.channelApplicationService = channelApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("channel:capability:list")
    /**
     * 完成 page Capabilities 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<PageResult<CapabilityResponse>> pageCapabilities(@RequestBody(required = false) CapabilityQuery query) {
        return success(channelApplicationService.pageCapabilities(query));
    }

    @GetMapping("/{id}")
    @RequiresPermission("channel:capability:detail")
    /**
     * 完成 get Capability 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<CapabilityResponse> getCapability(@PathVariable("id") Long id) {
        return success(channelApplicationService.getCapability(id));
    }

    @PostMapping
    @RequiresPermission("channel:capability:add")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道支付能力")
    /**
     * 完成 create Capability 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<CapabilityResponse> createCapability(@Valid @RequestBody CapabilitySaveRequest request) {
        return success(channelApplicationService.createCapability(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("channel:capability:edit")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道支付能力")
/**
 * 写入或更新 update Capability 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param id id 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
    public CommonResult<CapabilityResponse> updateCapability(@PathVariable("id") Long id,
                                                             @Valid @RequestBody CapabilitySaveRequest request) {
        return success(channelApplicationService.updateCapability(id, request));
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("channel:capability:status")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道支付能力状态")
/**
 * 写入或更新 update Capability Status 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param id id 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
    public CommonResult<CapabilityResponse> updateCapabilityStatus(@PathVariable("id") Long id,
                                                                   @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateCapabilityStatus(id, request.getStatus()));
    }

    @PutMapping("/{id}/support")
    @RequiresPermission("channel:capability:edit")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道支付能力支持项")
/**
 * 写入或更新 update Capability Support 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param id id 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
    public CommonResult<CapabilityResponse> updateCapabilitySupport(@PathVariable("id") Long id,
                                                                    @RequestBody CapabilitySupportRequest request) {
        return success(channelApplicationService.updateCapabilitySupport(
                id,
                request.getSupport3ds(),
                request.getSupportIncrementalAuthorization()
        ));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("channel:capability:remove")
    @OperationLog(moduleName = "渠道支付能力管理", businessType = OperationTypeConstants.DELETE, operation = "删除渠道支付能力")
    /**
     * 完成 delete Capability 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> deleteCapability(@PathVariable("id") Long id) {
        channelApplicationService.deleteCapability(id);
        return success();
    }
}
