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
 * @description : Admin Base IP Library Controller 控制器，位于 运营后台服务，接收 HTTP 请求、提取路径和查询条件、委托应用服务处理，并返回统一响应。
 * @status : create
 */
public class AdminBaseIpLibraryController {

    /**
     * IP Library Application Service 依赖，用于 Admin Base IP Library Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
