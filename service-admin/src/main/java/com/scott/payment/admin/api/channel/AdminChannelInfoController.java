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
 * @description : AdminChannelInfoController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminChannelInfoController {

    /**
     * channel Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminChannelApplicationService channelApplicationService;

    /**
     * 创建 AdminChannelInfoController 实例并注入其运行所需依赖。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param channelApplicationService channel Application Service 输入值，含义由调用方法名称和所属业务对象限定
     */
    public AdminChannelInfoController(AdminChannelApplicationService channelApplicationService) {
        this.channelApplicationService = channelApplicationService;
    }

    @PostMapping("/search")
    @RequiresPermission("channel:info:list")
    /**
     * 完成 page Channels 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<PageResult<ChannelInfoResponse>> pageChannels(@RequestBody(required = false) ChannelInfoQuery query) {
        return success(channelApplicationService.pageChannels(query));
    }

    @GetMapping("/options")
    @RequiresPermission("channel:info:list")
    /**
     * 完成 channel Options 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<List<ChannelOption>> channelOptions() {
        return success(channelApplicationService.listChannelOptions());
    }

    @GetMapping("/{id}")
    @RequiresPermission("channel:info:detail")
    /**
     * 完成 get Channel 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<ChannelInfoResponse> getChannel(@PathVariable("id") Long id) {
        return success(channelApplicationService.getChannel(id));
    }

    @PostMapping
    @RequiresPermission("channel:info:add")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.CREATE, operation = "新增渠道")
    /**
     * 完成 create Channel 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<ChannelInfoResponse> createChannel(@Valid @RequestBody ChannelInfoSaveRequest request) {
        return success(channelApplicationService.createChannel(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission("channel:info:edit")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.UPDATE, operation = "修改渠道")
/**
 * 写入或更新 update Channel 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param id id 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
    public CommonResult<ChannelInfoResponse> updateChannel(@PathVariable("id") Long id,
                                                           @Valid @RequestBody ChannelInfoSaveRequest request) {
        return success(channelApplicationService.updateChannel(id, request));
    }

    @PutMapping("/{id}/status")
    @RequiresPermission("channel:info:status")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.UPDATE, operation = "切换渠道状态")
/**
 * 写入或更新 update Channel Status 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param id id 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
    public CommonResult<ChannelInfoResponse> updateChannelStatus(@PathVariable("id") Long id,
                                                                 @Valid @RequestBody StatusRequest request) {
        return success(channelApplicationService.updateChannelStatus(id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("channel:info:remove")
    @OperationLog(moduleName = "渠道信息管理", businessType = OperationTypeConstants.DELETE, operation = "删除渠道")
    /**
     * 完成 delete Channel 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> deleteChannel(@PathVariable("id") Long id) {
        channelApplicationService.deleteChannel(id);
        return success();
    }
}
