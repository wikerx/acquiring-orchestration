package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.converter.DictConverter;
import com.scott.payment.admin.dto.SysDictDataDTO;
import com.scott.payment.admin.dto.SysDictDataQueryRequest;
import com.scott.payment.admin.dto.SysDictDataSaveRequest;
import com.scott.payment.admin.dto.SysDictTypeDTO;
import com.scott.payment.admin.dto.SysDictTypeQueryRequest;
import com.scott.payment.admin.dto.SysDictTypeSaveRequest;
import com.scott.payment.admin.entity.SysDictDataDO;
import com.scott.payment.admin.entity.SysDictTypeDO;
import com.scott.payment.admin.mapper.SysDictDataMapper;
import com.scott.payment.admin.mapper.SysDictTypeMapper;
import com.scott.payment.admin.service.AdminDictService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 管理后台数据字典领域服务实现。
 *
 * <p>该类只负责字典主表和字典项的持久化规则，不承担权限控制或页面交互逻辑。</p>
 */
@Service
public class AdminDictServiceImpl implements AdminDictService {

    /**
     * 未删除标识。
     */
    private static final long NOT_DELETED = 0L;

    /**
     * 默认启用状态。
     */
    private static final int ENABLED = 1;

    /**
     * 默认可编辑标识。
     */
    private static final int EDITABLE = 1;

    /**
     * 默认语言区域。
     */
    private static final String DEFAULT_LOCALE = "zh-CN";

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    public AdminDictServiceImpl(SysDictTypeMapper dictTypeMapper, SysDictDataMapper dictDataMapper) {
        this.dictTypeMapper = dictTypeMapper;
        this.dictDataMapper = dictDataMapper;
    }

    /**
     * 保存或更新字典类型。
     *
     * @param request 字典类型保存请求
     * @return 保存后的字典类型
     */
    @Override
    public SysDictTypeDTO saveDictType(SysDictTypeSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        SysDictTypeDO entity = findDictTypeOrThrowWhenBlank(request.getDictType());
        if (entity == null) {
            entity = new SysDictTypeDO();
            entity.setDictType(request.getDictType());
            entity.setCreatedBy(request.getOperator());
            entity.setCreatedAt(now);
            entity.setDeleted(NOT_DELETED);
        }
        fillDictType(entity, request, now);
        if (entity.getId() == null) {
            dictTypeMapper.insert(entity);
        } else {
            dictTypeMapper.updateById(entity);
        }
        return DictConverter.INSTANCE.toTypeDTO(entity);
    }

    /**
     * 按条件查询字典类型列表。
     *
     * @param request 查询条件
     * @return 字典类型列表
     */
    @Override
    public PageResult<SysDictTypeDTO> pageDictTypes(SysDictTypeQueryRequest request) {
        SysDictTypeQueryRequest query = request == null ? new SysDictTypeQueryRequest() : request;
        Page<SysDictTypeDO> page = dictTypeMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildDictTypeQueryWrapper(query)
        );
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream().map(DictConverter.INSTANCE::toTypeDTO).toList()
        );
    }

    /**
     * 软删除字典类型。
     *
     * @param dictType 字典类型编码
     */
    @Override
    public void deleteDictType(String dictType) {
        SysDictTypeDO entity = findDictTypeOrThrowWhenBlank(dictType);
        if (entity == null) {
            return;
        }
        entity.setDeleted(entity.getId());
        entity.setUpdatedAt(LocalDateTime.now());
        dictTypeMapper.updateById(entity);
    }

    /**
     * 保存或更新字典数据。
     *
     * @param request 字典数据保存请求
     * @return 保存后的字典数据
     */
    @Override
    public SysDictDataDTO saveDictData(SysDictDataSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String locale = defaultIfBlank(request.getLocale(), DEFAULT_LOCALE);
        SysDictDataDO entity = findDictDataOrThrowWhenBlank(request.getDictType(), request.getDictValue(), locale);
        if (entity == null) {
            entity = new SysDictDataDO();
            entity.setDictType(request.getDictType());
            entity.setDictValue(request.getDictValue());
            entity.setLocale(locale);
            entity.setCreatedBy(request.getOperator());
            entity.setCreatedAt(now);
            entity.setDeleted(NOT_DELETED);
        }
        fillDictData(entity, request, locale, now);
        if (entity.getId() == null) {
            dictDataMapper.insert(entity);
        } else {
            dictDataMapper.updateById(entity);
        }
        return DictConverter.INSTANCE.toDataDTO(entity);
    }

    /**
     * 按条件查询字典数据列表。
     *
     * @param request 查询条件
     * @return 字典数据列表
     */
    @Override
    public PageResult<SysDictDataDTO> pageDictData(SysDictDataQueryRequest request) {
        SysDictDataQueryRequest query = request == null ? new SysDictDataQueryRequest() : request;
        Page<SysDictDataDO> page = dictDataMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildDictDataQueryWrapper(query)
        );
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream().map(DictConverter.INSTANCE::toDataDTO).toList()
        );
    }

    /**
     * 按主键查询字典数据详情。
     *
     * @param id 字典数据主键
     * @return 字典数据详情
     */
    @Override
    public SysDictDataDTO getDictDataById(Long id) {
        SysDictDataDO entity = findDictDataById(id);
        return DictConverter.INSTANCE.toDataDTO(entity);
    }

    /**
     * 按主键更新字典数据。
     *
     * @param id      字典数据主键
     * @param request 字典数据保存请求
     * @return 更新后的字典数据
     */
    @Override
    public SysDictDataDTO updateDictDataById(Long id, SysDictDataSaveRequest request) {
        SysDictDataDO entity = findDictDataById(id);
        LocalDateTime now = LocalDateTime.now();
        fillDictData(entity, request, defaultIfBlank(request.getLocale(), entity.getLocale()), now);
        dictDataMapper.updateById(entity);
        return DictConverter.INSTANCE.toDataDTO(entity);
    }

    /**
     * 构建字典类型查询条件。
     *
     * @param query 查询请求
     * @return MyBatis Plus 查询条件
     */
    private LambdaQueryWrapper<SysDictTypeDO> buildDictTypeQueryWrapper(SysDictTypeQueryRequest query) {
        return Wrappers.<SysDictTypeDO>lambdaQuery()
                .eq(SysDictTypeDO::getDeleted, NOT_DELETED)
                .eq(StringUtils.hasText(query.getDictType()), SysDictTypeDO::getDictType, query.getDictType())
                .eq(StringUtils.hasText(query.getBizDomain()), SysDictTypeDO::getBizDomain, query.getBizDomain())
                .eq(query.getStatus() != null, SysDictTypeDO::getStatus, query.getStatus())
                .likeRight(StringUtils.hasText(query.getDictName()), SysDictTypeDO::getDictName, query.getDictName())
                .orderByDesc(SysDictTypeDO::getUpdatedAt);
    }

    /**
     * 构建字典数据查询条件。
     *
     * @param query 查询请求
     * @return MyBatis Plus 查询条件
     */
    private LambdaQueryWrapper<SysDictDataDO> buildDictDataQueryWrapper(SysDictDataQueryRequest query) {
        return Wrappers.<SysDictDataDO>lambdaQuery()
                .eq(SysDictDataDO::getDeleted, NOT_DELETED)
                .eq(StringUtils.hasText(query.getDictType()), SysDictDataDO::getDictType, query.getDictType())
                .eq(StringUtils.hasText(query.getDictValue()), SysDictDataDO::getDictValue, query.getDictValue())
                .eq(StringUtils.hasText(query.getParentValue()), SysDictDataDO::getParentValue, query.getParentValue())
                .eq(StringUtils.hasText(query.getLocale()), SysDictDataDO::getLocale, query.getLocale())
                .eq(query.getStatus() != null, SysDictDataDO::getStatus, query.getStatus())
                .likeRight(StringUtils.hasText(query.getDictLabel()), SysDictDataDO::getDictLabel, query.getDictLabel())
                .orderByAsc(SysDictDataDO::getDictSort)
                .orderByAsc(SysDictDataDO::getId);
    }

    /**
     * 软删除指定字典数据。
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典键值
     * @param locale    语言区域
     */
    @Override
    public void deleteDictData(String dictType, String dictValue, String locale) {
        SysDictDataDO entity = findDictDataOrThrowWhenBlank(dictType, dictValue, defaultIfBlank(locale, DEFAULT_LOCALE));
        if (entity == null) {
            return;
        }
        entity.setDeleted(entity.getId());
        entity.setUpdatedAt(LocalDateTime.now());
        dictDataMapper.updateById(entity);
    }

    /**
     * 按主键删除字典数据。
     *
     * @param id 字典数据主键
     */
    @Override
    public void deleteDictDataById(Long id) {
        SysDictDataDO entity = findDictDataById(id);
        entity.setDeleted(entity.getId());
        entity.setUpdatedAt(LocalDateTime.now());
        dictDataMapper.updateById(entity);
    }

    /**
     * 查询未删除字典类型。
     *
     * @param dictType 字典类型编码
     * @return 字典类型实体
     */
    private SysDictTypeDO findDictTypeOrThrowWhenBlank(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), ApiResultEnum.PARAM_MISSING.getMessage() + ":dictType");
        }
        return dictTypeMapper.selectOne(
                Wrappers.<SysDictTypeDO>lambdaQuery()
                        .eq(SysDictTypeDO::getDictType, dictType)
                        .eq(SysDictTypeDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
    }

    /**
     * 查询未删除字典数据。
     *
     * @param dictType  字典类型编码
     * @param dictValue 字典键值
     * @param locale    语言区域
     * @return 字典数据实体
     */
    private SysDictDataDO findDictDataOrThrowWhenBlank(String dictType, String dictValue, String locale) {
        if (!StringUtils.hasText(dictType) || !StringUtils.hasText(dictValue)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), ApiResultEnum.PARAM_MISSING.getMessage() + ":dictType/dictValue");
        }
        return dictDataMapper.selectOne(
                Wrappers.<SysDictDataDO>lambdaQuery()
                        .eq(SysDictDataDO::getDictType, dictType)
                        .eq(SysDictDataDO::getDictValue, dictValue)
                        .eq(SysDictDataDO::getLocale, locale)
                        .eq(SysDictDataDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
    }

    /**
     * 按主键查询未删除字典数据。
     *
     * @param id 字典数据主键
     * @return 字典数据实体
     */
    private SysDictDataDO findDictDataById(Long id) {
        if (id == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), ApiResultEnum.PARAM_MISSING.getMessage() + ":id");
        }
        SysDictDataDO entity = dictDataMapper.selectOne(
                Wrappers.<SysDictDataDO>lambdaQuery()
                        .eq(SysDictDataDO::getId, id)
                        .eq(SysDictDataDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (entity == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), ApiResultEnum.NOT_FOUND.getMessage() + ":dictData");
        }
        return entity;
    }

    /**
     * 填充字典类型实体。
     *
     * @param entity  字典类型实体
     * @param request 保存请求
     * @param now     当前时间
     */
    private void fillDictType(SysDictTypeDO entity, SysDictTypeSaveRequest request, LocalDateTime now) {
        entity.setDictName(request.getDictName());
        entity.setBizDomain(request.getBizDomain());
        entity.setSystemBuiltin(defaultIfNull(request.getSystemBuiltin(), 0));
        entity.setEditable(defaultIfNull(request.getEditable(), EDITABLE));
        entity.setStatus(defaultIfNull(request.getStatus(), ENABLED));
        entity.setRemark(request.getRemark());
        entity.setUpdatedBy(request.getOperator());
        entity.setUpdatedAt(now);
    }

    /**
     * 填充字典数据实体。
     *
     * @param entity  字典数据实体
     * @param request 保存请求
     * @param locale  语言区域
     * @param now     当前时间
     */
    private void fillDictData(SysDictDataDO entity, SysDictDataSaveRequest request, String locale, LocalDateTime now) {
        entity.setDictLabel(request.getDictLabel());
        entity.setParentValue(request.getParentValue());
        entity.setLocale(locale);
        entity.setDictSort(defaultIfNull(request.getDictSort(), 0));
        entity.setListClass(request.getListClass());
        entity.setExtraJson(request.getExtraJson());
        entity.setIsDefault(defaultIfNull(request.getIsDefault(), 0));
        entity.setStatus(defaultIfNull(request.getStatus(), ENABLED));
        entity.setRemark(request.getRemark());
        entity.setUpdatedBy(request.getOperator());
        entity.setUpdatedAt(now);
    }

    /**
     * 获取非空字符串。
     *
     * @param value        入参值
     * @param defaultValue 默认值
     * @return 非空字符串
     */
    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    /**
     * 获取非空整数。
     *
     * @param value        入参值
     * @param defaultValue 默认值
     * @return 非空整数
     */
    private Integer defaultIfNull(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }
}
