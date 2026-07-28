package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminPostApplicationService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.SysPostDO;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminPostController
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台岗位管理控制器
 * @status : create
 *
 * <p>岗位分页、详情和维护请求统一通过
 * {@link AdminPostApplicationService} 编排，Controller 保持为薄入口层。</p>
 */
@RestController
@RequestMapping("/admin/system/post")
public class AdminPostController {

    /**
     * admin Post Application Service 依赖，用于 Admin Post Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminPostApplicationService adminPostApplicationService;

    /**
     * 创建岗位管理控制器。
     *
     * @param adminPostApplicationService 岗位应用服务
     */
    public AdminPostController(AdminPostApplicationService adminPostApplicationService) {
        this.adminPostApplicationService = adminPostApplicationService;
    }

    /**
     * 分页查询岗位列表。
     *
     * @param pageNo   页码，默认 1
     * @param pageSize 每页条数，默认 10
     * @param postCode 岗位编码（可选）
     * @param postName 岗位名称（可选）
     * @param status   状态（可选）
     * @return 分页岗位列表
     */
    @GetMapping("/list")
    @RequiresPermission("system:post:list")
    public CommonResult<PageResult<SysPostDO>> list(@RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
                                                    @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
                                                    @RequestParam(value = "postCode", required = false) String postCode,
                                                    @RequestParam(value = "postName", required = false) String postName,
                                                    @RequestParam(value = "status", required = false) Integer status) {
        return success(adminPostApplicationService.pagePosts(pageNo, pageSize, postCode, postName, status));
    }

    /**
     * 查询全部岗位（用于下拉选择）。
     *
     * @return 全部启用岗位
     */
    @GetMapping("/all")
    @RequiresPermission("system:post:list")
    public CommonResult<List<SysPostDO>> all() {
        return success(adminPostApplicationService.listEnabledPosts());
    }

    /**
     * 查询岗位详情。
     *
     * @param id 岗位主键 ID
     * @return 岗位实体
     */
    @GetMapping("/{id}")
    @RequiresPermission("system:post:query")
    public CommonResult<SysPostDO> detail(@PathVariable("id") Long id) {
        return success(adminPostApplicationService.getPost(id));
    }

    /**
     * 导出岗位列表。
     */
    @GetMapping("/export")
    @RequiresPermission("system:post:export")
    @OperationLog(moduleName = "岗位管理", businessType = OperationTypeConstants.EXPORT, operation = "导出岗位")
    public void export(HttpServletResponse response) {
        adminPostApplicationService.exportPosts(currentOperatorName(), response);
    }

    /**
     * 新增岗位。
     *
     * @param post 岗位实体
     * @return 新增后的岗位
     */
    @PostMapping
    @RequiresPermission("system:post:add")
    @OperationLog(moduleName = "岗位管理", businessType = OperationTypeConstants.CREATE, operation = "新增岗位")
    public CommonResult<SysPostDO> create(@RequestBody SysPostDO post) {
        return success(adminPostApplicationService.createPost(post));
    }

    /**
     * 修改岗位。
     *
     * @param id   岗位主键 ID
     * @param input 岗位实体
     * @return 更新后的岗位
     */
    @PutMapping("/{id}")
    @RequiresPermission("system:post:edit")
    @OperationLog(moduleName = "岗位管理", businessType = OperationTypeConstants.UPDATE, operation = "修改岗位")
    public CommonResult<SysPostDO> update(@PathVariable("id") Long id, @RequestBody SysPostDO input) {
        return success(adminPostApplicationService.updatePost(id, input));
    }

    /**
     * 删除岗位（逻辑删除）。
     *
     * @param id 岗位主键 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("system:post:remove")
    @OperationLog(moduleName = "岗位管理", businessType = OperationTypeConstants.DELETE, operation = "删除岗位")
    public CommonResult<Void> remove(@PathVariable("id") Long id) {
        adminPostApplicationService.removePost(id);
        return success();
    }

    /**
     * 获取当前操作人名称，用于写入导出文件元信息。
     *
     * @return 操作人名称
     */
    private String currentOperatorName() {
        com.scott.payment.component.core.auth.InternalAuthAccount account =
                com.scott.payment.component.core.auth.InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName();
        }
        return account.getLoginAccount();
    }
}
