package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.SysDictDataSaveRequest;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.dto.SysDictTypeQueryRequest;
import com.scott.payment.admin.dto.SysDictTypeSaveRequest;
import com.scott.payment.component.core.model.PageResult;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDictService
 * @date : 2026-06-19 21:52
 * @email : scott_x@163.com
 * @description : 管理后台数据字典领域服务
 * @status : create
 *
 * <p>负责字典类型与字典项的持久化、查询和删除规则，不处理接口协议适配。</p>
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
     * 按条件查询导出用字典类型列表。
     *
     * @param request 查询条件
     * @return 字典类型列表
     */
    List<SysDictTypeDTO> listDictTypes(SysDictTypeQueryRequest request);

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
     * 按条件查询导出用字典数据列表。
     *
     * @param request 查询条件
     * @return 字典数据列表
     */
    List<SysDictDataDTO> listDictData(SysDictDataQueryRequest request);

    /**
     * 按主键查询字典数据详情。
     *
     * @param id 字典数据主键
     * @return 字典数据详情
     */
    SysDictDataDTO getDictDataById(Long id);

    /**
     * 按主键更新字典数据。
     *
     * @param id      字典数据主键
     * @param request 字典数据保存请求
     * @return 更新后的字典数据
     */
    SysDictDataDTO updateDictDataById(Long id, SysDictDataSaveRequest request);

    /**
     * 按主键删除字典数据。
     *
     * @param id 字典数据主键
     */
    void deleteDictDataById(Long id);

    /**
     * 软删除指定字典数据。
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典键值
     * @param locale    语言区域
     */
    void deleteDictData(String dictType, String dictValue, String locale);

    /** 主动清空管理端与商户端共享的启用字典下拉快照。 */
    void refreshOptionCache();
}
