package com.scott.payment.admin.api.channel;

import com.scott.payment.admin.application.channel.AdminChannelAlertApplicationService;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertNotifyLogQuery;
import com.scott.payment.admin.dto.channel.ChannelAlertDTOs.ChannelAlertNotifyLogResponse;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelAlertNotifyLogController
 * @date : 2026-07-17 00:00
 * @email : scott_x@163.com
 * @description : 渠道预警通知日志查询接口，位于 service-admin 接口层，当前仅查询邮件通知日志。
 * @status : create
 */
@RestController
@RequestMapping("/admin/channel/alert-notify-logs")
public class AdminChannelAlertNotifyLogController {

    private final AdminChannelAlertApplicationService channelAlertApplicationService;

    /**
     * 创建渠道预警通知日志查询接口。
     *
     * @param channelAlertApplicationService 渠道预警应用服务
     */
    public AdminChannelAlertNotifyLogController(AdminChannelAlertApplicationService channelAlertApplicationService) {
        this.channelAlertApplicationService = channelAlertApplicationService;
    }

    /**
     * 分页查询渠道预警通知日志。
     *
     * @param query 查询条件
     * @return 通知日志分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("channel:alert-notify-log:list")
    public CommonResult<PageResult<ChannelAlertNotifyLogResponse>> pageNotifyLogs(@RequestBody(required = false) ChannelAlertNotifyLogQuery query) {
        return success(channelAlertApplicationService.pageNotifyLogs(query));
    }
}
