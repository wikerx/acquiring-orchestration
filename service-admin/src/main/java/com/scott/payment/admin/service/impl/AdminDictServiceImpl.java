package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
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
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.dictionary.model.DictionaryOptionSnapshot;
import com.scott.payment.component.db.dictionary.service.DictionaryOptionCacheReader;
import com.scott.payment.component.db.dictionary.support.DictionaryOptionCacheKey;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminDictServiceImpl
 * @date : 2026-06-19 21:55
 * @email : scott_x@163.com
 * @description : 管理后台数据字典领域服务实现
 * @status : create
 *
 * <p>该类只负责字典主表和字典项的持久化规则、唯一键约束与软删除处理，不承担权限控制或页面交互逻辑。</p>
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

    /**
     * 字典类型数据访问组件。
     */
    private final SysDictTypeMapper dictTypeMapper;

    /**
     * 字典项数据访问组件。
     */
    private final SysDictDataMapper dictDataMapper;

    /**
     * 数据字典对象转换器。
     */
    private final DictConverter dictConverter;

    /** 跨系统启用字典下拉快照读取器。 */
    private final DictionaryOptionCacheReader dictionaryOptionCacheReader;

    /** 事务感知的 Spring Cache 管理器。 */
    private final CacheManager cacheManager;

    /** 字典项写事务使用的可靠精确缓存失效协调器。 */
    private final ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;

    /**
     * 创建数据字典领域服务。
     *
     * @param dictTypeMapper 字典类型数据访问组件
     * @param dictDataMapper 字典项数据访问组件
     * @param dictConverter  数据字典对象转换器
     * @param dictionaryOptionCacheReader 跨系统启用字典下拉快照读取器
     * @param cacheManager 事务感知的 Spring Cache 管理器
     * @param cacheInvalidationCoordinator 事务缓存可靠失效协调器
     */
    public AdminDictServiceImpl(SysDictTypeMapper dictTypeMapper,
                                SysDictDataMapper dictDataMapper,
                                DictConverter dictConverter,
                                DictionaryOptionCacheReader dictionaryOptionCacheReader,
                                CacheManager cacheManager,
                                ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator) {
        this.dictTypeMapper = dictTypeMapper;
        this.dictDataMapper = dictDataMapper;
        this.dictConverter = dictConverter;
        this.dictionaryOptionCacheReader = dictionaryOptionCacheReader;
        this.cacheManager = cacheManager;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
    }

    /**
     * 保存或更新字典类型。
     *
     * @param request 字典类型保存请求
     * @return 保存后的字典类型
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
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
        clearOptionCache();
        return dictConverter.toTypeDTO(entity);
    }

    /**
     * 按条件查询字典类型列表。
     *
     * @param request 查询条件
     * @return 字典类型列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
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
                page.getRecords().stream().map(dictConverter::toTypeDTO).toList()
        );
    }

    /**
     * 按条件查询全部字典类型，不应用分页截断。
     *
     * @param request 字典类型、名称和状态等可选条件
     * @return 字典类型列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<SysDictTypeDTO> listDictTypes(SysDictTypeQueryRequest request) {
        SysDictTypeQueryRequest query = request == null ? new SysDictTypeQueryRequest() : request;
        return dictTypeMapper.selectList(buildDictTypeQueryWrapper(query))
                .stream()
                .map(dictConverter::toTypeDTO)
                .toList();
    }

    /**
     * 软删除字典类型。
     *
     * @param dictType 字典类型编码
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteDictType(String dictType) {
        SysDictTypeDO entity = findDictTypeOrThrowWhenBlank(dictType);
        if (entity == null) {
            return;
        }
        entity.setDeleted(entity.getId());
        entity.setUpdatedAt(LocalDateTime.now());
        dictTypeMapper.updateById(entity);
        clearOptionCache();
    }

    /**
     * 保存或更新字典数据。
     *
     * @param request 字典数据保存请求
     * @return 保存后的字典数据
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public SysDictDataDTO saveDictData(SysDictDataSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        String locale = defaultIfBlank(request.getLocale(), DEFAULT_LOCALE);
        SysDictDataDO entity = findDictDataOrThrowWhenBlank(request.getDictType(), request.getDictValue(), locale);
        String previousType = entity == null ? request.getDictType() : entity.getDictType();
        String previousLocale = entity == null ? locale : entity.getLocale();
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
        evictOptionCaches(previousType, previousLocale, entity.getDictType(), entity.getLocale());
        return dictConverter.toDataDTO(entity);
    }

    /**
     * 按条件查询字典数据列表。
     *
     * @param request 查询条件
     * @return 字典数据列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
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
                page.getRecords().stream().map(dictConverter::toDataDTO).toList()
        );
    }

    /**
     * 按条件查询全部字典数据，不应用分页截断。
     *
     * @param request 字典类型、值、语言和状态等可选条件
     * @return 字典数据列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<SysDictDataDTO> listDictData(SysDictDataQueryRequest request) {
        SysDictDataQueryRequest query = request == null ? new SysDictDataQueryRequest() : request;
        if (isEnabledOptionQuery(query)) {
            String locale = defaultIfBlank(query.getLocale(), DEFAULT_LOCALE);
            return dictionaryOptionCacheReader.findEnabled(query.getDictType().trim(), locale)
                    .stream()
                    .map(this::toDataDTO)
                    .toList();
        }
        return dictDataMapper.selectList(buildDictDataQueryWrapper(query))
                .stream()
                .map(dictConverter::toDataDTO)
                .toList();
    }

    /**
     * 按主键查询字典数据详情。
     *
     * @param id 字典数据主键
     * @return 字典数据详情
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public SysDictDataDTO getDictDataById(Long id) {
        SysDictDataDO entity = findDictDataById(id);
        return dictConverter.toDataDTO(entity);
    }

    /**
     * 按主键更新字典数据。
     *
     * @param id      字典数据主键
     * @param request 字典数据保存请求
     * @return 更新后的字典数据
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public SysDictDataDTO updateDictDataById(Long id, SysDictDataSaveRequest request) {
        SysDictDataDO entity = findDictDataById(id);
        String previousType = entity.getDictType();
        String previousLocale = entity.getLocale();
        LocalDateTime now = LocalDateTime.now();
        fillDictData(entity, request, defaultIfBlank(request.getLocale(), entity.getLocale()), now);
        dictDataMapper.updateById(entity);
        evictOptionCaches(previousType, previousLocale, entity.getDictType(), entity.getLocale());
        return dictConverter.toDataDTO(entity);
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
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteDictData(String dictType, String dictValue, String locale) {
        SysDictDataDO entity = findDictDataOrThrowWhenBlank(dictType, dictValue, defaultIfBlank(locale, DEFAULT_LOCALE));
        if (entity == null) {
            return;
        }
        entity.setDeleted(entity.getId());
        entity.setUpdatedAt(LocalDateTime.now());
        dictDataMapper.updateById(entity);
        evictOptionCache(entity.getDictType(), entity.getLocale());
    }

    /**
     * 按主键删除字典数据。
     *
     * @param id 字典数据主键
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteDictDataById(Long id) {
        SysDictDataDO entity = findDictDataById(id);
        entity.setDeleted(entity.getId());
        entity.setUpdatedAt(LocalDateTime.now());
        dictDataMapper.updateById(entity);
        evictOptionCache(entity.getDictType(), entity.getLocale());
    }

    /** 清空两端共享的启用数据字典下拉快照。 */
    @Override
    public void refreshOptionCache() {
        clearOptionCache();
    }

    /** 判断是否为可使用共享快照的纯启用字典下拉查询。 */
    private boolean isEnabledOptionQuery(SysDictDataQueryRequest query) {
        return StringUtils.hasText(query.getDictType())
                && !StringUtils.hasText(query.getDictLabel())
                && !StringUtils.hasText(query.getDictValue())
                && !StringUtils.hasText(query.getParentValue())
                && query.getStatus() != null
                && query.getStatus() == ENABLED;
    }

    /** 将共享下拉快照转换为管理端字典数据响应。 */
    private SysDictDataDTO toDataDTO(DictionaryOptionSnapshot snapshot) {
        SysDictDataDTO response = new SysDictDataDTO();
        response.setId(snapshot.getId());
        response.setDictType(snapshot.getDictType());
        response.setDictLabel(snapshot.getDictLabel());
        response.setDictValue(snapshot.getDictValue());
        response.setParentValue(snapshot.getParentValue());
        response.setLocale(snapshot.getLocale());
        response.setDictSort(snapshot.getDictSort());
        response.setListClass(snapshot.getListClass());
        response.setExtraJson(snapshot.getExtraJson());
        response.setIsDefault(snapshot.getIsDefault());
        response.setStatus(snapshot.getStatus());
        return response;
    }

    /** 在当前业务事务内登记指定字典类型和语言的下拉快照可靠失效。 */
    private void evictOptionCache(String dictType, String locale) {
        cacheInvalidationCoordinator.prepare(
                PaymentCacheNames.SYSTEM_DICT_OPTIONS,
                DictionaryOptionCacheKey.of(dictType, defaultIfBlank(locale, DEFAULT_LOCALE))
        );
    }

    /** 新旧字典分组相同时只失效一次，不同时分别删除两个精确业务键。 */
    private void evictOptionCaches(String previousType,
                                   String previousLocale,
                                   String currentType,
                                   String currentLocale) {
        String previousKey = DictionaryOptionCacheKey.of(
                previousType, defaultIfBlank(previousLocale, DEFAULT_LOCALE));
        String currentKey = DictionaryOptionCacheKey.of(
                currentType, defaultIfBlank(currentLocale, DEFAULT_LOCALE));
        cacheInvalidationCoordinator.prepare(PaymentCacheNames.SYSTEM_DICT_OPTIONS, previousKey);
        if (!previousKey.equals(currentKey)) {
            cacheInvalidationCoordinator.prepare(PaymentCacheNames.SYSTEM_DICT_OPTIONS, currentKey);
        }
    }

    /** 在当前事务提交后清空所有共享字典下拉快照。 */
    private void clearOptionCache() {
        Cache cache = cacheManager.getCache(PaymentCacheNames.SYSTEM_DICT_OPTIONS);
        if (cache != null) {
            cache.clear();
        }
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
