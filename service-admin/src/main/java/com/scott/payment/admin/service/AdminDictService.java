package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.SysDictDataSaveRequest;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.dto.SysDictTypeQueryRequest;
import com.scott.payment.admin.dto.SysDictTypeSaveRequest;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDictService
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 管理后台数据字典服务
 * @status : create
 */
public interface AdminDictService {

    /**
     * 保存或更新字典类型。
     *
     * @param request 字典类型保存请求
     * @return 保存后的字典类型
     */
    SysDictTypeDTO saveDictType(SysDictTypeSaveRequest request);

    /**
     * 按条件查询字典类型列表。
     *
     * @param request 查询条件
     * @return 字典类型列表
     */
    PageResult<SysDictTypeDTO> pageDictTypes(SysDictTypeQueryRequest request);

    /**
     * 软删除字典类型。
     *
     * @param dictType 字典类型编码
     */
    void deleteDictType(String dictType);

    /**
     * 保存或更新字典数据。
     *
     * @param request 字典数据保存请求
     * @return 保存后的字典数据
     */
    SysDictDataDTO saveDictData(SysDictDataSaveRequest request);

    /**
     * 按条件查询字典数据列表。
     *
     * @param request 查询条件
     * @return 字典数据列表
     */
    PageResult<SysDictDataDTO> pageDictData(SysDictDataQueryRequest request);

    /**
     * 软删除指定字典数据。
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典键值
     * @param locale    语言区域
     */
    void deleteDictData(String dictType, String dictValue, String locale);
}
