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

@RestController
@RequestMapping("/admin/base/ip-library")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminBaseIpLibraryController
 * @date : 2026-07-05 00:34
 * @email : scott_x@163.com
 * @description : AdminBaseIpLibraryController HTTP 接口控制器，用于接收请求、调用应用服务并返回统一响应，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminBaseIpLibraryController {

    /**
     * ip Library Application Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
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
    @PostMapping("/lookup")
    @RequiresPermission("base:ip-library:list")
    @OperationLog(moduleName = "全球IP库管理", businessType = OperationTypeConstants.QUERY, operation = "查询IP归属区间")
    public CommonResult<IpLibraryDTOs.IpLibraryRecordResponse> lookup(@Valid @RequestBody IpLibraryDTOs.IpLibraryLookupRequest request) {
        return success(ipLibraryApplicationService.lookup(request));
    }
}
