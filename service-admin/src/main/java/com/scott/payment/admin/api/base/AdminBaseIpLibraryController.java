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
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Admin Base Ip Library 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/base/ip-library")
public class AdminBaseIpLibraryController {

    /**
     * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
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
    /**
     * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/lookup")
    @RequiresPermission("base:ip-library:list")
    @OperationLog(moduleName = "全球IP库管理", businessType = OperationTypeConstants.QUERY, operation = "查询IP归属区间")
    public CommonResult<IpLibraryDTOs.IpLibraryRecordResponse> lookup(@Valid @RequestBody IpLibraryDTOs.IpLibraryLookupRequest request) {
        return success(ipLibraryApplicationService.lookup(request));
    }
}
