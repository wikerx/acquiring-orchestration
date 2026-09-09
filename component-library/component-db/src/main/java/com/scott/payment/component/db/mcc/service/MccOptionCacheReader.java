package com.scott.payment.component.db.mcc.service;

import com.alibaba.fastjson2.TypeReference;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.cache.PaymentRedisKeyResolver;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.mcc.entity.SharedMccCodeDO;
import com.scott.payment.component.db.mcc.entity.SharedMccLevel1DO;
import com.scott.payment.component.db.mcc.entity.SharedMccLevel2DO;
import com.scott.payment.component.db.mcc.mapper.SharedMccCodeMapper;
import com.scott.payment.component.db.mcc.mapper.SharedMccLevel1Mapper;
import com.scott.payment.component.db.mcc.mapper.SharedMccLevel2Mapper;
import com.scott.payment.component.db.mcc.model.MccOptionSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MccOptionCacheReader
 * @date : 2026-08-20 00:00
 * @email : scott_x@163.com
 * @description : 公共 MCC 选项常驻缓存读取器，缓存缺失时从主库重建三级级联快照
 * @status : create
 */
@Slf4j
@Service
public class MccOptionCacheReader implements MccOptionCacheInvalidator {

    /**
     * {@code NOT_DELETED}常量，统一 {@code MccOptionCacheReader} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    /**
     * 启用标识，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：布尔值或 0/1 标识；不允许为空；非敏感字段。
     * 取值范围：仅允许平台约定的真假取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * {@code CACHE_DOMAIN}，表示远程服务主机、商户域名或渠道访问域名。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CACHE_DOMAIN = "mcc";
    /**
     * {@code CACHE_BUSINESS}常量，统一 {@code MccOptionCacheReader} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CACHE_BUSINESS = "options";
    /**
     * {@code CACHE_BUSINESS_KEY}常量，统一 {@code MccOptionCacheReader} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CACHE_BUSINESS_KEY = "all";
    /**
     * 等级1值前缀常量，统一 {@code MccOptionCacheReader} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String LEVEL1_VALUE_PREFIX = "L1:";
    /**
     * 等级2值前缀常量，统一 {@code MccOptionCacheReader} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String LEVEL2_VALUE_PREFIX = "L2:";

    /** MCC 一级分类只读 Mapper。 */
    private final SharedMccLevel1Mapper level1Mapper;

    /** MCC 二级分类只读 Mapper。 */
    private final SharedMccLevel2Mapper level2Mapper;

    /** MCC 编码只读 Mapper。 */
    private final SharedMccCodeMapper codeMapper;

    /** Redis 字符串模板；未启用 Redis 时允许为空并直接查询数据库。 */
    private final StringRedisTemplate redisTemplate;

    /** 统一 Redis 物理键解析器。 */
    private final PaymentRedisKeyResolver keyResolver;

    /** 常驻缓存失效门禁。 */
    private final CacheInvalidationGuard invalidationGuard;

    /** 事务提交后可靠失效协调器。 */
    private final ManagedCacheInvalidationCoordinator invalidationCoordinator;

    /**
     * 创建公共 MCC 选项缓存读取器。
     *
     * @param level1Mapper MCC 一级分类只读 Mapper
     * @param level2Mapper MCC 二级分类只读 Mapper
     * @param codeMapper MCC 编码只读 Mapper
     * @param redisTemplateProvider Redis 字符串模板延迟提供器
     * @param keyResolverProvider 统一 Redis 物理键解析器延迟提供器
     * @param invalidationGuardProvider 缓存失效门禁延迟提供器
     * @param invalidationCoordinatorProvider 事务提交后可靠失效协调器延迟提供器
     */
    public MccOptionCacheReader(
            SharedMccLevel1Mapper level1Mapper,
            SharedMccLevel2Mapper level2Mapper,
            SharedMccCodeMapper codeMapper,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectProvider<PaymentRedisKeyResolver> keyResolverProvider,
            ObjectProvider<CacheInvalidationGuard> invalidationGuardProvider,
            ObjectProvider<ManagedCacheInvalidationCoordinator> invalidationCoordinatorProvider) {
        this.level1Mapper = level1Mapper;
        this.level2Mapper = level2Mapper;
        this.codeMapper = codeMapper;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.keyResolver = keyResolverProvider.getIfAvailable();
        this.invalidationGuard = invalidationGuardProvider.getIfAvailable();
        this.invalidationCoordinator = invalidationCoordinatorProvider.getIfAvailable();
    }

    /**
     * 查询全部启用 MCC 三级级联选项。
     *
     * @return 一级分类、二级分类和四位 MCC 编码组成的有序树
     */
    @DS(DataSourceName.MASTER)
    public List<MccOptionSnapshot> listOptions() {
        String cacheKey = cacheKey();
        boolean cacheAllowed = isCacheAllowed();
        if (cacheAllowed) {
            List<MccOptionSnapshot> cached = readCache(cacheKey);
            if (!cached.isEmpty()) {
                return cached;
            }
        }
        List<MccOptionSnapshot> options = loadFromDatabase();
        if (cacheAllowed && !options.isEmpty()) {
            writeCache(cacheKey, options);
        }
        return options;
    }

    /**
     * 在 MCC 管理事务内登记公共常驻快照可靠失效。
     *
     * <p>未启用统一失效协调器的最小运行环境中，降级为直接删除 Redis 键。</p>
     */
    @Override
    public void evictOptions() {
        if (invalidationCoordinator != null) {
            invalidationCoordinator.prepare(PaymentCacheNames.MCC_OPTIONS, CACHE_BUSINESS_KEY);
            return;
        }
        if (redisTemplate != null && StringUtils.hasText(cacheKey())) {
            redisTemplate.delete(cacheKey());
        }
    }

    /** 从 Redis 读取 MCC 树，异常时按缓存未命中处理。 */
    private List<MccOptionSnapshot> readCache(String cacheKey) {
        if (redisTemplate == null || !StringUtils.hasText(cacheKey)) {
            return List.of();
        }
        try {
            List<MccOptionSnapshot> values = JsonUtils.parseObject(
                    redisTemplate.opsForValue().get(cacheKey),
                    new TypeReference<List<MccOptionSnapshot>>() {
                    });
            return values == null ? List.of() : values;
        } catch (RuntimeException exception) {
            log.warn("读取 MCC 公共缓存失败，cacheKey: {}，异常类型: {}",
                    cacheKey, exception.getClass().getSimpleName());
            return List.of();
        }
    }

    /** 将数据库权威快照写入无物理 TTL 的公共缓存。 */
    private void writeCache(String cacheKey, List<MccOptionSnapshot> options) {
        if (redisTemplate == null || !StringUtils.hasText(cacheKey)) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, JsonUtils.toJsonString(options));
        } catch (RuntimeException exception) {
            log.warn("写入 MCC 公共缓存失败，cacheKey: {}，异常类型: {}",
                    cacheKey, exception.getClass().getSimpleName());
        }
    }

    /** 缓存变更门禁处于 pending 时禁止读取和回填旧快照。 */
    private boolean isCacheAllowed() {
        if (invalidationGuard == null) {
            return true;
        }
        try {
            return !invalidationGuard.isPending(PaymentCacheNames.MCC_OPTIONS, CACHE_BUSINESS_KEY);
        } catch (RuntimeException exception) {
            log.warn("读取 MCC 缓存门禁失败，异常类型: {}", exception.getClass().getSimpleName());
            return false;
        }
    }

    /** 生成 acquiring:{environment}:mcc:options:all 物理键。 */
    private String cacheKey() {
        return keyResolver == null
                ? null
                : keyResolver.businessKey(CACHE_DOMAIN, CACHE_BUSINESS, CACHE_BUSINESS_KEY);
    }

    /** 从主库加载启用记录并组装三级级联树。 */
    private List<MccOptionSnapshot> loadFromDatabase() {
        List<SharedMccLevel1DO> level1Rows = level1Mapper.selectList(
                Wrappers.<SharedMccLevel1DO>lambdaQuery()
                        .eq(SharedMccLevel1DO::getDeleted, NOT_DELETED)
                        .eq(SharedMccLevel1DO::getStatus, ENABLED)
                        .orderByAsc(SharedMccLevel1DO::getSortNo)
                        .orderByAsc(SharedMccLevel1DO::getLevel1Code));
        List<SharedMccLevel2DO> level2Rows = level2Mapper.selectList(
                Wrappers.<SharedMccLevel2DO>lambdaQuery()
                        .eq(SharedMccLevel2DO::getDeleted, NOT_DELETED)
                        .eq(SharedMccLevel2DO::getStatus, ENABLED)
                        .orderByAsc(SharedMccLevel2DO::getSortNo)
                        .orderByAsc(SharedMccLevel2DO::getLevel2Code));
        List<SharedMccCodeDO> codeRows = codeMapper.selectList(
                Wrappers.<SharedMccCodeDO>lambdaQuery()
                        .eq(SharedMccCodeDO::getDeleted, NOT_DELETED)
                        .eq(SharedMccCodeDO::getStatus, ENABLED)
                        .orderByAsc(SharedMccCodeDO::getSortNo)
                        .orderByAsc(SharedMccCodeDO::getMccCode));

        Map<Long, MccOptionSnapshot> level1Options = new LinkedHashMap<>();
        level1Rows.forEach(row -> level1Options.put(row.getId(), option(
                LEVEL1_VALUE_PREFIX + row.getId(), row.getLevel1Code(), row.getNameCn(), row.getNameEn())));
        Map<Long, MccOptionSnapshot> level2Options = new LinkedHashMap<>();
        level2Rows.forEach(row -> {
            MccOptionSnapshot parent = level1Options.get(row.getLevel1Id());
            if (parent != null) {
                MccOptionSnapshot child = option(
                        LEVEL2_VALUE_PREFIX + row.getId(), row.getLevel2Code(), row.getNameCn(), row.getNameEn());
                level2Options.put(row.getId(), child);
                parent.getChildren().add(child);
            }
        });
        codeRows.forEach(row -> {
            MccOptionSnapshot parent = level2Options.get(row.getLevel2Id());
            if (parent != null) {
                parent.getChildren().add(option(row.getMccCode(), row.getMccCode(), row.getNameCn(), row.getNameEn()));
            }
        });
        return new ArrayList<>(level1Options.values());
    }

    /** 创建一个 MCC 级联节点。 */
    private MccOptionSnapshot option(String value, String code, String nameCn, String nameEn) {
        MccOptionSnapshot option = new MccOptionSnapshot();
        option.setValue(value);
        option.setLabel(label(code, nameCn, nameEn));
        option.setNameCn(nameCn);
        option.setNameEn(nameEn);
        return option;
    }

    /** 创建兼容旧调用方的代码及中英文展示文本。 */
    private String label(String code, String nameCn, String nameEn) {
        StringBuilder value = new StringBuilder(code == null ? "" : code);
        if (StringUtils.hasText(nameCn)) {
            value.append(" — ").append(nameCn.trim());
        }
        if (StringUtils.hasText(nameEn)) {
            value.append(" / ").append(nameEn.trim());
        }
        return value.toString();
    }
}
