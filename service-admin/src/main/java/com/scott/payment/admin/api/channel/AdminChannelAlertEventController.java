package com.scott.payment.admin.api.channel;

import com.scott.payment.admin.application.channel.AdminChannelAlertApplicationService;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.AlertEventAcknowledgeRequest;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertEventQuery;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertEventResponse;
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
 * @classname : AdminChannelAlertEventController
 * @date : 2026-07-17 00:00
 * @email : scott_x@163.com
 * @description : 渠道预警事件查询接口，位于 service-admin 接口层，用于后台查看和人工确认预警事件。
 * @status : create
 */
@RestController
@RequestMapping("/admin/channel/alert-events")
public class AdminChannelAlertEventController {

    /**
     * channel Alert Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminChannelAlertApplicationService channelAlertApplicationService;

    /**
     * 创建渠道预警事件查询接口。
     *
     * @param channelAlertApplicationService 渠道预警应用服务
     */
    public AdminChannelAlertEventController(AdminChannelAlertApplicationService channelAlertApplicationService) {
        this.channelAlertApplicationService = channelAlertApplicationService;
    }

    /**
     * 分页查询渠道预警事件。
     *
     * @param query 查询条件
     * @return 事件分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("channel:alert-event:list")
    public CommonResult<PageResult<ChannelAlertEventResponse>> pageEvents(@RequestBody(required = false) ChannelAlertEventQuery query) {
        return success(channelAlertApplicationService.pageEvents(query));
    }

    /**
     * 查询渠道预警事件详情。
     *
     * @param id 事件 ID
     * @return 事件详情
     */
    @GetMapping("/{id}")
    @RequiresPermission("channel:alert-event:detail")
    public CommonResult<ChannelAlertEventResponse> getEvent(@PathVariable("id") Long id) {
        return success(channelAlertApplicationService.getEvent(id));
    }

    /**
     * 人工确认渠道预警事件。
     *
     * @param id 事件 ID
     * @param request 确认请求
     * @return 确认后的事件
     */
    @PutMapping("/{id}/acknowledge")
    @RequiresPermission("channel:alert-event:acknowledge")
    @OperationLog(moduleName = "渠道预警事件", businessType = OperationTypeConstants.UPDATE, operation = "确认渠道预警事件")
    public CommonResult<ChannelAlertEventResponse> acknowledgeEvent(@PathVariable("id") Long id,
                                                                    @Valid @RequestBody(required = false) AlertEventAcknowledgeRequest request) {
        return success(channelAlertApplicationService.acknowledgeEvent(id, request));
    }

    /**
     * 删除渠道预警事件。
     *
     * @param id 事件 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("channel:alert-event:remove")
    @OperationLog(moduleName = "渠道预警事件", businessType = OperationTypeConstants.DELETE, operation = "删除渠道预警事件")
    public CommonResult<Void> deleteEvent(@PathVariable("id") Long id) {
        channelAlertApplicationService.deleteEvent(id);
        return success();
    }
}
