package com.scott.payment.admin.api.system;

import com.scott.payment.admin.application.system.AdminDictApplicationService;
import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.SysDictDataSaveRequest;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.dto.SysDictTypeQueryRequest;
import com.scott.payment.admin.dto.SysDictTypeSaveRequest;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDictController
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台数据字典控制器
 * @status : create
 *
 * <p>Controller 只处理权限、参数接收和 HTTP 协议映射，具体业务编排交由
 * {@link AdminDictApplicationService}。</p>
 */
@RestController
@RequestMapping("/admin/system/dicts")
public class AdminDictController {

    /**
     * admin Dict Application Service 依赖，用于 Admin Dict Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final AdminDictApplicationService adminDictApplicationService;

    /**
     * 规范化admindictcontroller，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param adminDictApplicationService admin Dict Application Service 输入值，参与 admindictapplicationservice 的查询、校验、转换、写入或日志摘要
     */
    public AdminDictController(AdminDictApplicationService adminDictApplicationService) {
        this.adminDictApplicationService = adminDictApplicationService;
    }

    /**
     * 保存或更新字典类型。
     *
     * @param request 保存请求
     * @return 保存后的字典类型
     */
    @PostMapping("/types")
    @RequiresPermission("system:dict:add")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.CREATE, operation = "新增字典类型")
    public CommonResult<SysDictTypeDTO> createDictType(@Valid @RequestBody SysDictTypeSaveRequest request) {
        return success(adminDictApplicationService.saveDictType(request));
    }

    /**
     * 更新字典类型。
     *
     * @param dictType 字典类型编码
     * @param request  保存请求
     * @return 保存后的字典类型
     */
    @PutMapping("/types/{dictType}")
    @RequiresPermission("system:dict:edit")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.UPDATE, operation = "更新字典类型")
    public CommonResult<SysDictTypeDTO> updateDictType(@PathVariable("dictType") String dictType,
                                                       @Valid @RequestBody SysDictTypeSaveRequest request) {
        dictType = decodePathSegment(dictType);
        request.setDictType(dictType);
        return success(adminDictApplicationService.saveDictType(request));
    }

    /**
     * 按条件查询字典类型列表。
     *
     * @param request 查询条件
     * @return 字典类型列表
     */
    @PostMapping("/types/search")
    @RequiresPermission("system:dict:list")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.QUERY, operation = "分页查询字典类型列表")
    public CommonResult<PageResult<SysDictTypeDTO>> listDictTypes(@RequestBody(required = false) SysDictTypeQueryRequest request) {
        return success(adminDictApplicationService.pageDictTypes(request));
    }

    /**
     * 软删除字典类型。
     *
     * @param dictType 字典类型编码
     * @return 删除结果
     */
    @DeleteMapping("/types/{dictType}")
    @RequiresPermission("system:dict:remove")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.DELETE, operation = "删除字典类型")
    public CommonResult<Void> deleteDictType(@PathVariable("dictType") String dictType) {
        dictType = decodePathSegment(dictType);
        adminDictApplicationService.deleteDictType(dictType);
        return success();
    }

    /**
     * 导出字典类型列表。
     *
     * @param request 查询条件
     * @return 字典类型列表
     */
    @PostMapping("/types/export")
    @RequiresPermission("system:dict:export")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.EXPORT, operation = "导出字典类型列表")
    public void exportDictTypes(@RequestBody(required = false) SysDictTypeQueryRequest request,
                                HttpServletResponse response) {
        adminDictApplicationService.exportDictTypes(request, currentOperatorName(), response);
    }

    /**
     * 刷新字典缓存。
     *
     * @return 空响应
     */
    @PostMapping("/refresh-cache")
    @RequiresPermission("system:dict:refresh")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.UPDATE, operation = "刷新字典缓存")
    public CommonResult<Void> refreshCache() {
        return success();
    }

    /**
     * 保存或更新字典数据。
     *
     * @param request 保存请求
     * @return 保存后的字典数据
     */
    @PostMapping("/data")
    @RequiresPermission("system:dictData:add")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.CREATE, operation = "新增字典数据")
    public CommonResult<SysDictDataDTO> createDictData(@Valid @RequestBody SysDictDataSaveRequest request) {
        return success(adminDictApplicationService.saveDictData(request));
    }

    /**
     * 按主键查询字典数据详情。
     *
     * @param id 字典数据主键
     * @return 字典数据详情
     */
    @GetMapping("/data/id/{id}")
    @RequiresPermission("system:dictData:query")
    public CommonResult<SysDictDataDTO> getDictDataById(@PathVariable("id") Long id) {
        return success(adminDictApplicationService.getDictDataById(id));
    }

    /**
     * 按主键更新字典数据。
     *
     * @param id      字典数据主键
     * @param request 保存请求
     * @return 更新后的字典数据
     */
    @PutMapping("/data/id/{id}")
    @RequiresPermission("system:dictData:edit")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.UPDATE, operation = "按主键更新字典数据")
    public CommonResult<SysDictDataDTO> updateDictDataById(@PathVariable("id") Long id,
                                                           @Valid @RequestBody SysDictDataSaveRequest request) {
        return success(adminDictApplicationService.updateDictDataById(id, request));
    }

    /**
     * 更新字典数据。
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典键值
     * @param request   保存请求
     * @return 保存后的字典数据
     */
    @PutMapping("/data/{dictType}/{dictValue}")
    @RequiresPermission("system:dictData:edit")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.UPDATE, operation = "更新字典数据")
    public CommonResult<SysDictDataDTO> updateDictData(@PathVariable("dictType") String dictType,
                                                       @PathVariable("dictValue") String dictValue,
                                                       @Valid @RequestBody SysDictDataSaveRequest request) {
        dictType = decodePathSegment(dictType);
        dictValue = decodePathSegment(dictValue);
        request.setDictType(dictType);
        request.setDictValue(dictValue);
        return success(adminDictApplicationService.saveDictData(request));
    }

    /**
     * 按条件查询字典数据列表。
     *
     * @param request 查询条件
     * @return 字典数据列表
     */
    @PostMapping("/data/search")
    @RequiresPermission("system:dictData:list")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.QUERY, operation = "分页查询字典数据列表")
    public CommonResult<PageResult<SysDictDataDTO>> listDictData(@RequestBody(required = false) SysDictDataQueryRequest request) {
        return success(adminDictApplicationService.pageDictData(request));
    }

    /**
     * 查询字典数据详情。
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典键值
     * @param locale    语言区域
     * @return 字典数据分页结果中的第一条
     */
    @GetMapping("/data/{dictType}/{dictValue}")
    @RequiresPermission("system:dictData:query")
    public CommonResult<PageResult<SysDictDataDTO>> queryDictData(@PathVariable("dictType") String dictType,
                                                                  @PathVariable("dictValue") String dictValue,
                                                                  @RequestParam(value = "locale", required = false) String locale) {
        dictType = decodePathSegment(dictType);
        dictValue = decodePathSegment(dictValue);
        SysDictDataQueryRequest request = new SysDictDataQueryRequest();
        request.setDictType(dictType);
        request.setDictValue(dictValue);
        request.setLocale(locale);
        request.setPageNo(1);
        request.setPageSize(1);
        return success(adminDictApplicationService.pageDictData(request));
    }

    /**
     * 软删除字典数据。
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典键值
     * @param locale    语言区域，默认 zh-CN
     * @return 删除结果
     */
    @DeleteMapping("/data/{dictType}/{dictValue}")
    @RequiresPermission("system:dictData:remove")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.DELETE, operation = "删除字典数据")
    public CommonResult<Void> deleteDictData(@PathVariable("dictType") String dictType,
                                             @PathVariable("dictValue") String dictValue,
                                             @RequestParam(value = "locale", required = false) String locale) {
        dictType = decodePathSegment(dictType);
        dictValue = decodePathSegment(dictValue);
        adminDictApplicationService.deleteDictData(dictType, dictValue, locale);
        return success();
    }

    /**
     * 按主键删除字典数据。
     *
     * @param id 字典数据主键
     * @return 删除结果
     */
    @DeleteMapping("/data/id/{id}")
    @RequiresPermission("system:dictData:remove")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.DELETE, operation = "按主键删除字典数据")
    public CommonResult<Void> deleteDictDataById(@PathVariable("id") Long id) {
        adminDictApplicationService.deleteDictDataById(id);
        return success();
    }

    /**
     * 导出字典数据列表。
     *
     * @param request 查询条件
     * @return 字典数据列表
     */
    @PostMapping("/data/export")
    @RequiresPermission("system:dictData:export")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.EXPORT, operation = "导出字典数据列表")
    public void exportDictData(@RequestBody(required = false) SysDictDataQueryRequest request,
                               HttpServletResponse response) {
        adminDictApplicationService.exportDictData(request, currentOperatorName(), response);
    }

    /**
     * 兼容前端对路径段的双重编码，避免字典键值包含斜杠时被网关或容器拆段。
     *
     * @param value 路径变量原始值
     * @return 解码后的业务值
     */
    private String decodePathSegment(String value) {
        return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 获取当前操作人名称，用于写入 Excel 导出元信息。
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
