package com.scott.payment.admin.api.base;

import com.scott.payment.admin.application.base.AdminBaseIpLibraryApplicationService;
import com.scott.payment.admin.dto.base.IpLibraryDTOs;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseIpLibraryController
 * @date : 2026-07-05 00:34
 * @email : scott_x@163.com
 * @description : admin基础iplibrary HTTP 控制器，位于 运营后台服务，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。
 * @status : create
 */
@RestController
@RequestMapping("/admin/base/ip-library")
public class AdminBaseIpLibraryController {

    private final AdminBaseIpLibraryApplicationService ipLibraryApplicationService;

    /**
     * 创建全球 IP 库管理控制器。
     */
    public AdminBaseIpLibraryController(AdminBaseIpLibraryApplicationService ipLibraryApplicationService) {
        this.ipLibraryApplicationService = ipLibraryApplicationService;
    }

    /**
     * 分页查询全球 IP 库。
     */
    @PostMapping("/page")
    @RequiresPermission("base:ip-library:list")
    @OperationLog(moduleName = "全球IP库管理", businessType = OperationTypeConstants.QUERY, operation = "分页查询全球IP库")
    public CommonResult<PageResult<IpLibraryDTOs.IpLibraryRecordResponse>> page(@RequestBody(required = false) IpLibraryDTOs.IpLibraryQueryRequest request) {
        return success(ipLibraryApplicationService.page(request));
    }

    /**
     * 查询单个 IP 命中的归属区间。
     */
    @PostMapping("/lookup")
    @RequiresPermission("base:ip-library:list")
    @OperationLog(moduleName = "全球IP库管理", businessType = OperationTypeConstants.QUERY, operation = "查询IP归属区间")
    public CommonResult<IpLibraryDTOs.IpLibraryRecordResponse> lookup(@Valid @RequestBody IpLibraryDTOs.IpLibraryLookupRequest request) {
        return success(ipLibraryApplicationService.lookup(request));
    }
}
