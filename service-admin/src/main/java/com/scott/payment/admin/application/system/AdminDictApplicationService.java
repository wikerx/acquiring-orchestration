package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.SysDictDataSaveRequest;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.dto.SysDictTypeQueryRequest;
import com.scott.payment.admin.dto.SysDictTypeSaveRequest;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDictApplicationService
 * @date : 2026-06-19 20:40
 * @email : scott_x@163.com
 * @description : 管理后台数据字典应用服务
 * @status : create
 */
@Service
public class AdminDictApplicationService {

    private final AdminDictService adminDictService;

    /**
     * 创建后台数据字典应用服务。
     *
     * @param adminDictService 数据字典领域服务
     */
    public AdminDictApplicationService(AdminDictService adminDictService) {
        this.adminDictService = adminDictService;
    }

    /**
     * 保存字典类型。
     *
     * @param request 保存请求
     * @return 字典类型详情
     */
    public SysDictTypeDTO saveDictType(SysDictTypeSaveRequest request) {
        return adminDictService.saveDictType(request);
    }

    /**
     * 分页查询字典类型。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResult<SysDictTypeDTO> pageDictTypes(SysDictTypeQueryRequest request) {
        return adminDictService.pageDictTypes(request);
    }

    /**
     * 删除字典类型。
     *
     * @param dictType 字典类型编码
     */
    public void deleteDictType(String dictType) {
        adminDictService.deleteDictType(dictType);
    }

    /**
     * 保存字典数据。
     *
     * @param request 保存请求
     * @return 字典数据详情
     */
    public SysDictDataDTO saveDictData(SysDictDataSaveRequest request) {
        return adminDictService.saveDictData(request);
    }

    /**
     * 分页查询字典数据。
     *
     * @param request 查询条件
     * @return 分页结果
     */
    public PageResult<SysDictDataDTO> pageDictData(SysDictDataQueryRequest request) {
        return adminDictService.pageDictData(request);
    }

    /**
     * 按主键查询字典数据详情。
     *
     * @param id 字典数据主键
     * @return 字典数据详情
     */
    public SysDictDataDTO getDictDataById(Long id) {
        return adminDictService.getDictDataById(id);
    }

    /**
     * 按主键更新字典数据。
     *
     * @param id      字典数据主键
     * @param request 保存请求
     * @return 更新后的字典数据
     */
    public SysDictDataDTO updateDictDataById(Long id, SysDictDataSaveRequest request) {
        return adminDictService.updateDictDataById(id, request);
    }

    /**
     * 删除字典数据。
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典值
     * @param locale    语言区域
     */
    public void deleteDictData(String dictType, String dictValue, String locale) {
        adminDictService.deleteDictData(dictType, dictValue, locale);
    }

    /**
     * 按主键删除字典数据。
     *
     * @param id 字典数据主键
     */
    public void deleteDictDataById(Long id) {
        adminDictService.deleteDictDataById(id);
    }
}
