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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDictController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Admin Dict 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/dicts")
public class AdminDictController {

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final AdminDictApplicationService adminDictApplicationService;

    public AdminDictController(AdminDictApplicationService adminDictApplicationService) {
        this.adminDictApplicationService = adminDictApplicationService;
    }

    /**
     * 保存或更新字典类型。
     *
     * @param request 保存请求
     * @return 保存后的字典类型
     */
    /**
     * 创建或保存系统管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param dictType 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 删除系统管理数据，按业务规则处理引用校验和删除边界。
     * @param dictType 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 创建或保存系统管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 获取系统管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 更新系统管理数据，保持已有记录、状态和审计字段的一致性。
     * @param dictType 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param dictValue 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 删除系统管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
