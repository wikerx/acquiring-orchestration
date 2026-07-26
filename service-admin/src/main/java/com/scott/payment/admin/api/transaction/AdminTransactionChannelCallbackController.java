package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminTransactionApplicationService;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackQuery;
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
 * @classname : AdminTransactionChannelCallbackController
 * @date : 2026-07-14 23:59
 * @email : scott_x@163.com
 * @description : 渠道回调记录查询接口，位于 service-admin 接口层，查询渠道回调幂等处理、验签结果和状态映射结果。
 * @status : create
 */
@RestController
@RequestMapping("/admin/transactions/channel-callbacks")
public class AdminTransactionChannelCallbackController {

    /**
     * transaction Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminTransactionApplicationService transactionApplicationService;

    /**
     * 创建渠道回调记录查询接口。
     *
     * @param transactionApplicationService 交易查询应用服务
     */
    public AdminTransactionChannelCallbackController(AdminTransactionApplicationService transactionApplicationService) {
        this.transactionApplicationService = transactionApplicationService;
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
    public CommonResult<PageResult<Map<String, Object>>> search(@RequestBody(required = false) ChannelCallbackQuery query) {
        return success(transactionApplicationService.pageChannelCallbacks(query));
    }
}
