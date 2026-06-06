package com.scott.payment.admin.api;

import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.SysDictDataSaveRequest;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.dto.SysDictTypeQueryRequest;
import com.scott.payment.admin.dto.SysDictTypeSaveRequest;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDictController
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 管理后台数据字典内部接口
 * @status : create
 */
@RestController
@RequestMapping("/admin/system/dicts")
public class AdminDictController {

    /**
     * 数据字典服务。
     */
    private final AdminDictService dictService;

    /**
     * 创建数据字典内部接口。
     *
     * @param dictService 数据字典服务
     */
    public AdminDictController(AdminDictService dictService) {
        this.dictService = dictService;
    }

    /**
     * 保存或更新字典类型。
     *
     * @param request 保存请求
     * @return 保存后的字典类型
     */
    @PostMapping("/types")
    @RequiresPermission("admin:dict:save")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.UPDATE, operation = "保存或更新字典类型")
    public CommonResult<SysDictTypeDTO> saveDictType(@Valid @RequestBody SysDictTypeSaveRequest request) {
        return CommonResult.success(dictService.saveDictType(request));
    }

    /**
     * 按条件查询字典类型列表。
     *
     * @param request 查询条件
     * @return 字典类型列表
     */
    @PostMapping("/types/search")
    @RequiresPermission("admin:dict:view")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.QUERY, operation = "分页查询字典类型列表")
    public CommonResult<PageResult<SysDictTypeDTO>> listDictTypes(@RequestBody(required = false) SysDictTypeQueryRequest request) {
        return CommonResult.success(dictService.pageDictTypes(request));
    }

    /**
     * 软删除字典类型。
     *
     * @param dictType 字典类型编码
     * @return 删除结果
     */
    @DeleteMapping("/types/{dictType}")
    @RequiresPermission("admin:dict:delete")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.DELETE, operation = "删除字典类型")
    public CommonResult<Void> deleteDictType(@PathVariable String dictType) {
        dictService.deleteDictType(dictType);
        return CommonResult.success();
    }

    /**
     * 保存或更新字典数据。
     *
     * @param request 保存请求
     * @return 保存后的字典数据
     */
    @PostMapping("/data")
    @RequiresPermission("admin:dict:save")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.UPDATE, operation = "保存或更新字典数据")
    public CommonResult<SysDictDataDTO> saveDictData(@Valid @RequestBody SysDictDataSaveRequest request) {
        return CommonResult.success(dictService.saveDictData(request));
    }

    /**
     * 按条件查询字典数据列表。
     *
     * @param request 查询条件
     * @return 字典数据列表
     */
    @PostMapping("/data/search")
    @RequiresPermission("admin:dict:view")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.QUERY, operation = "分页查询字典数据列表")
    public CommonResult<PageResult<SysDictDataDTO>> listDictData(@RequestBody(required = false) SysDictDataQueryRequest request) {
        return CommonResult.success(dictService.pageDictData(request));
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
    @RequiresPermission("admin:dict:delete")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.DELETE, operation = "删除字典数据")
    public CommonResult<Void> deleteDictData(@PathVariable String dictType,
                                             @PathVariable String dictValue,
                                             @RequestParam(required = false) String locale) {
        dictService.deleteDictData(dictType, dictValue, locale);
        return CommonResult.success();
    }
}
