package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.channel.AdminChannelApplicationService;
import com.scott.payment.admin.application.transaction.AdminTransactionApplicationService;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackChannelOption;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackResponse;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 渠道回调记录查询接口，位于 service-admin 接口层，查询渠道回调幂等处理、验签结果和状态映射结果。
 */
@RestController
@RequestMapping("/admin/transactions/channel-callbacks")
public class AdminTransactionChannelCallbackController {

    private final AdminTransactionApplicationService transactionApplicationService;
    private final AdminChannelApplicationService channelApplicationService;

    /**
     * 创建渠道回调记录查询接口。
     *
     * @param transactionApplicationService 交易查询应用服务
     * @param channelApplicationService 渠道筛选项应用服务
     */
    public AdminTransactionChannelCallbackController(AdminTransactionApplicationService transactionApplicationService,
                                                     AdminChannelApplicationService channelApplicationService) {
        this.transactionApplicationService = transactionApplicationService;
        this.channelApplicationService = channelApplicationService;
    }

    /**
     * 分页查询渠道回调业务记录。
     *
     * @param query 查询条件
     * @return 渠道回调业务记录分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("transaction:channel-callback:list")
    @OperationLog(moduleName = "渠道回调记录查询", businessType = OperationTypeConstants.QUERY, operation = "分页查询渠道回调记录")
    public CommonResult<PageResult<ChannelCallbackResponse>> search(
            @RequestBody(required = false) ChannelCallbackQuery query) {
        return success(transactionApplicationService.pageChannelCallbacks(query));
    }

    /** 查询收单渠道筛选项，返回编码及名称。 */
    @GetMapping("/channel-options")
    @RequiresPermission("transaction:channel-callback:list")
    public CommonResult<List<ChannelCallbackChannelOption>> channelOptions() {
        return success(channelApplicationService.listChannelOptions().stream()
                .filter(option -> Integer.valueOf(1).equals(option.getSupportAcquiring()))
                .map(option -> new ChannelCallbackChannelOption(option.getChannelCode(), option.getChannelName()))
                .toList());
    }

    /** 读取单条回调审计详情，包括留存的脱敏业务字段和应答。 */
    @GetMapping("/{callbackId}")
    @RequiresPermission("transaction:channel-callback:detail")
    @OperationLog(moduleName = "渠道回调记录查询", businessType = OperationTypeConstants.QUERY, operation = "查询渠道回调脱敏详情")
    public CommonResult<ChannelCallbackDetailResponse> detail(
            @PathVariable("callbackId") String callbackId,
            @RequestParam("transactionDateTime")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS") LocalDateTime transactionDateTime,
            @RequestParam(name = "deliveryPage", defaultValue = "1") int deliveryPage) {
        return success(transactionApplicationService.channelCallbackDetail(callbackId, transactionDateTime, deliveryPage));
    }
}
