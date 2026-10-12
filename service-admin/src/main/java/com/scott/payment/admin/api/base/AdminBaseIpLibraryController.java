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
     *
     * <p>精确查询与商户 OpenAPI 共用公共 IP 缓存和 SLAVE 回源逻辑。缓存 Key 使用
     * {@code acquiring:{env}:ip:v4|v6:{ipNumber}}，Value 同时保存对外归属字段、命中区间和审计信息；
     * 因此管理端和 OpenAPI 对同一 IP 看到同一数据版本。缓存中的路由版本过期时自动回源并覆盖当前 Key，
     * Redis 故障不会阻断只读查询。</p>
     *
     * @param request 精确 IP 与可选 IP 类型；不接受域名、CIDR、范围或批量地址
     * @return 命中区间详情；合法地址未命中时 data 为空
     */
    @PostMapping("/lookup")
    @RequiresPermission("base:ip-library:list")
    @OperationLog(moduleName = "全球IP库管理", businessType = OperationTypeConstants.QUERY, operation = "查询IP归属区间")
    public CommonResult<IpLibraryDTOs.IpLibraryRecordResponse> lookup(@Valid @RequestBody IpLibraryDTOs.IpLibraryLookupRequest request) {
        return success(ipLibraryApplicationService.lookup(request));
    }
}
