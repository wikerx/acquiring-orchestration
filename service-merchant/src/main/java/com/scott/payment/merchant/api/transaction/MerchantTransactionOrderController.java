package com.scott.payment.merchant.api.transaction;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.merchant.application.transaction.MerchantTransactionApplicationService;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionActionRequest;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionActionResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionOrderController
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户后台交易查询接口，位于 service-merchant 接口层，以当前登录商户为边界查询交易清单、详情、统计并发起商户退款动作。
 * @status : create
 */
@RestController
@RequestMapping("/merchant/transactions/orders")
public class MerchantTransactionOrderController {

    /**
     * transaction Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final MerchantTransactionApplicationService transactionApplicationService;

    /**
     * 创建商户后台交易查询接口。
     *
     * @param transactionApplicationService 商户交易查询应用服务
     */
    public MerchantTransactionOrderController(MerchantTransactionApplicationService transactionApplicationService) {
        this.transactionApplicationService = transactionApplicationService;
    }

    /**
     * 分页查询当前商户交易主单。
     *
     * @param query 查询条件
     * @return 当前商户主单分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("merchant:transaction:order:list")
    @OperationLog(moduleName = "商户交易查询", businessType = OperationTypeConstants.QUERY, operation = "分页查询商户交易主单")
    public CommonResult<PageResult<TransactionOrderResponse>> search(@RequestBody(required = false) TransactionPageQuery query) {
        return success(transactionApplicationService.pageOrders(currentMerchantId(), query));
    }

    /**
     * 分页查询当前商户交易动作单并返回统计。
     *
     * @param query 查询条件
     * @return 当前商户动作单分页与统计
     */
    @PostMapping("/operations/search")
    @RequiresPermission("merchant:transaction:order:list")
    @OperationLog(moduleName = "商户交易查询", businessType = OperationTypeConstants.QUERY, operation = "查询商户交易动作统计")
    public CommonResult<TransactionOperationSearchResponse> searchOperations(@RequestBody(required = false) TransactionPageQuery query) {
        return success(transactionApplicationService.searchOperations(currentMerchantId(), query));
    }

    /**
     * 按当前查询条件导出当前商户交易主单。
     *
     * @param query    查询条件
     * @param response HTTP 响应
     */
    @PostMapping("/export")
    @RequiresPermission("merchant:transaction:order:export")
    @OperationLog(moduleName = "商户交易查询", businessType = OperationTypeConstants.EXPORT, operation = "导出商户交易主单")
    public void export(@RequestBody(required = false) TransactionPageQuery query, HttpServletResponse response) {
        transactionApplicationService.exportOrders(currentMerchantId(), query, currentOperatorName(), response);
    }

    /**
     * 查询当前商户交易聚合详情。
     *
     * @param transactionId 平台交易 ID
     * @return 交易聚合详情
     */
    @GetMapping("/{transactionId}")
    @RequiresPermission("merchant:transaction:order:detail")
    @OperationLog(moduleName = "商户交易查询", businessType = OperationTypeConstants.QUERY, operation = "查询商户交易详情")
    public CommonResult<TransactionDetailResponse> detail(@PathVariable("transactionId") String transactionId) {
        return success(transactionApplicationService.detail(currentMerchantId(), transactionId));
    }

    /**
     * 当前商户发起请款动作。
     *
     * @param transactionId 原授权平台交易 ID
     * @param request       请款请求
     * @return 请款动作结果
     */
    @PostMapping("/{transactionId}/capture")
    @RequiresPermission("merchant:transaction:order:capture")
    @OperationLog(moduleName = "商户交易查询", businessType = OperationTypeConstants.UPDATE, operation = "商户发起交易请款")
    public CommonResult<TransactionActionResponse> capture(@PathVariable("transactionId") String transactionId,
                                                           @RequestBody(required = false) TransactionActionRequest request) {
        return success(transactionApplicationService.capture(currentMerchantId(), transactionId, request));
    }

    /**
     * 当前商户发起退款动作。
     *
     * @param transactionId 原平台交易 ID
     * @param request       退款请求
     * @return 退款动作结果
     */
    @PostMapping("/{transactionId}/refund")
    @RequiresPermission("merchant:transaction:order:refund")
    @OperationLog(moduleName = "商户交易查询", businessType = OperationTypeConstants.UPDATE, operation = "商户发起交易退款")
    public CommonResult<TransactionActionResponse> refund(@PathVariable("transactionId") String transactionId,
                                                          @RequestBody(required = false) TransactionActionRequest request) {
        return success(transactionApplicationService.refund(currentMerchantId(), transactionId, request));
    }

    /**
     * 当前商户发起撤销动作。
     *
     * @param transactionId 原授权平台交易 ID
     * @param request       撤销请求
     * @return 撤销动作结果
     */
    @PostMapping("/{transactionId}/void")
    @RequiresPermission("merchant:transaction:order:void")
    @OperationLog(moduleName = "商户交易查询", businessType = OperationTypeConstants.UPDATE, operation = "商户发起交易撤销")
    public CommonResult<TransactionActionResponse> voidPayment(@PathVariable("transactionId") String transactionId,
                                                               @RequestBody(required = false) TransactionActionRequest request) {
        return success(transactionApplicationService.voidPayment(currentMerchantId(), transactionId, request));
    }

    /**
     * 完成 current Merchant Id 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    private String currentMerchantId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account.getMerchantId();
    }

    /**
     * 完成 current Operator Name 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "merchant";
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        return account.getLoginAccount();
    }
}
