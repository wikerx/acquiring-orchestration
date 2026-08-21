package com.scott.payment.merchant.api;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.merchant.application.MerchantFinanceApplicationService;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.DetailQuery;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundAccountResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundLedgerResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFundAccountController
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端可用余额、在途余额、保证金余额与统一余额明细只读接口。
 * @status : create
 */
@RestController
@RequestMapping("/merchant/fund-account")
public class MerchantFundAccountController {
    private final MerchantFinanceApplicationService applicationService;

    /**
     * 构造商户资金账户接口。
     *
     * @param applicationService 绑定认证商户范围的财务应用服务
     */
    public MerchantFundAccountController(MerchantFinanceApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 查询当前认证商户资金账户及实时余额汇总。
     *
     * @return 当前商户资金账户响应
     */
    @GetMapping
    @RequiresPermission("merchant:fund:account:view")
    public CommonResult<FundAccountResponse> getAccount() { return success(applicationService.getFundAccount()); }

    /**
     * 分页查询当前认证商户余额流水。
     *
     * @param query 业务类型、入账时间和分页条件
     * @return 当前商户余额流水分页结果
     */
    @PostMapping("/ledgers/search")
    @RequiresPermission("merchant:fund:ledger:view")
    public CommonResult<PageResult<FundLedgerResponse>> pageLedgers(@RequestBody(required = false) DetailQuery query) {
        return success(applicationService.pageLedgers(query));
    }

    /**
     * 按筛选条件导出当前认证商户全部余额明细。
     *
     * @param query 业务类型、入账时间等导出条件
     * @param response Excel 文件响应，写入后不再返回 JSON
     */
    @PostMapping("/ledgers/export")
    @RequiresPermission("merchant:fund:ledger:export")
    public void exportLedgers(@RequestBody(required = false) DetailQuery query,
                              HttpServletResponse response) {
        applicationService.exportLedgers(query, response);
    }

}
