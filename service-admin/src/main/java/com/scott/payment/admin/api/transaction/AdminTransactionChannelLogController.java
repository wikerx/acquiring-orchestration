package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminTransactionApplicationService;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelLogQuery;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionChannelLogController
 * @date : 2026-07-14 23:59
 * @email : scott_x@163.com
 * @description : 交易渠道日志查询接口，位于 service-admin 接口层，按 transaction_date_time 分表范围查询渠道请求响应脱敏日志。
 * @status : create
 */
@RestController
@RequestMapping("/admin/transactions/channel-logs")
public class AdminTransactionChannelLogController {

    /**
     * transaction Application Service 依赖，用于 Admin Transaction Channel Log Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminTransactionApplicationService transactionApplicationService;

    /**
     * 创建交易渠道日志查询接口。
     *
     * @param transactionApplicationService 交易查询应用服务
     */
    public AdminTransactionChannelLogController(AdminTransactionApplicationService transactionApplicationService) {
        this.transactionApplicationService = transactionApplicationService;
    }

    /**
     * 分页查询渠道交互日志。
     *
     * @param query 查询条件
     * @return 渠道交互日志分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("transaction:channel-log:list")
    @OperationLog(moduleName = "交易渠道日志查询", businessType = OperationTypeConstants.QUERY, operation = "分页查询渠道日志")
    public CommonResult<PageResult<Map<String, Object>>> search(@RequestBody(required = false) ChannelLogQuery query) {
        return success(transactionApplicationService.pageChannelLogs(query));
    }
}
