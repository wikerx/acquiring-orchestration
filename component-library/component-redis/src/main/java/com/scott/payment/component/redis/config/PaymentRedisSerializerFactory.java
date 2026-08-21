package com.scott.payment.component.redis.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.serializer.JacksonObjectReader;
import org.springframework.data.redis.serializer.JacksonObjectWriter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRedisSerializerFactory
 * @date : 2026-07-29 19:10
 * @email : scott_x@163.com
 * @description : 集中创建支付系统 Redis JSON 序列化器，统一存量格式兼容和类型安全边界
 * @status : create
 */
public final class PaymentRedisSerializerFactory {

    private static final String MERCHANT_RUNTIME_PROFILE_CLASS_NAME =
            "com.scott.payment.component.db.auth.model.MerchantRuntimeProfile";

    private static final String MERCHANT_KEY_METADATA_CLASS_NAME =
            "com.scott.payment.component.db.auth.model.MerchantKeyMetadata";

    private static final String MERCHANT_ROUTE_PROFILE_CLASS_NAME =
            "com.scott.payment.component.db.route.model.MerchantRouteProfile";

    private static final String MERCHANT_ROUTE_OPTION_CLASS_NAME =
            "com.scott.payment.component.db.route.model.MerchantRouteProfile$RouteOption";

    private static final String MERCHANT_OPENAPI_ACCESS_POLICY_CLASS_NAME =
            "com.scott.payment.openapi.security.MerchantOpenApiAccessPolicy";

    private static final String PAYMENT_CARD_BIN_CACHE_ENTRY_CLASS_NAME =
            "com.scott.payment.payment.model.PaymentCardBinCacheEntry";

    private static final String DICTIONARY_OPTION_SNAPSHOT_CLASS_NAME =
            "com.scott.payment.component.db.dictionary.model.DictionaryOptionSnapshot";

    private static final String ENABLED_EMAIL_TEMPLATE_SNAPSHOT_CLASS_NAME =
            "com.scott.payment.component.db.email.model.EnabledEmailTemplateSnapshot";

    private static final String MCC_OPTION_SNAPSHOT_CLASS_NAME =
            "com.scott.payment.component.db.mcc.model.MccOptionSnapshot";

    private static final String SYSTEM_CONFIG_SNAPSHOT_CLASS_NAME =
            "com.scott.payment.component.db.systemconfig.model.SystemConfigSnapshot";

    private static final String HOLIDAY_CALENDAR_MONTH_CLASS_NAME =
            "com.scott.payment.admin.dto.system.HolidayCalendarDTOs$CalendarMonthResponse";

    private static final String HOLIDAY_CALENDAR_YEAR_CLASS_NAME =
            "com.scott.payment.admin.dto.system.HolidayCalendarDTOs$CalendarYearResponse";

    private static final String HOLIDAY_CALENDAR_DAY_CLASS_NAME =
            "com.scott.payment.admin.dto.system.HolidayCalendarDTOs$CalendarDayResponse";

    private static final String MERCHANT_CURRENT_FEE_CLASS_NAME =
            "com.scott.payment.merchant.dto.MerchantFinanceDTOs$CurrentFeeResponse";

    private static final String MERCHANT_FEE_RULE_CLASS_NAME =
            "com.scott.payment.merchant.dto.MerchantFinanceDTOs$FeeRuleResponse";

    private static final String MERCHANT_FEE_TIER_CLASS_NAME =
            "com.scott.payment.merchant.dto.MerchantFinanceDTOs$FeeTierResponse";

    private static final Pattern MERCHANT_RUNTIME_PROFILE = Pattern.compile(
            "^" + Pattern.quote(MERCHANT_RUNTIME_PROFILE_CLASS_NAME) + "$");

    private static final Pattern MERCHANT_KEY_METADATA = Pattern.compile(
            "^" + Pattern.quote(MERCHANT_KEY_METADATA_CLASS_NAME) + "$");

    private static final Pattern MERCHANT_ROUTE_PROFILE = Pattern.compile(
            "^" + Pattern.quote(MERCHANT_ROUTE_PROFILE_CLASS_NAME) + "$");

    private static final Pattern MERCHANT_ROUTE_OPTION = Pattern.compile(
            "^" + Pattern.quote(MERCHANT_ROUTE_OPTION_CLASS_NAME) + "$");

    private static final Pattern MERCHANT_OPENAPI_ACCESS_POLICY = Pattern.compile(
            "^" + Pattern.quote(MERCHANT_OPENAPI_ACCESS_POLICY_CLASS_NAME) + "$");

    private static final Pattern PAYMENT_CARD_BIN_CACHE_ENTRY = Pattern.compile(
            "^" + Pattern.quote(PAYMENT_CARD_BIN_CACHE_ENTRY_CLASS_NAME) + "$");

    private static final Pattern DICTIONARY_OPTION_SNAPSHOT = Pattern.compile(
            "^" + Pattern.quote(DICTIONARY_OPTION_SNAPSHOT_CLASS_NAME) + "$");

    private static final Pattern ENABLED_EMAIL_TEMPLATE_SNAPSHOT = Pattern.compile(
            "^" + Pattern.quote(ENABLED_EMAIL_TEMPLATE_SNAPSHOT_CLASS_NAME) + "$");

    private static final Pattern MCC_OPTION_SNAPSHOT = Pattern.compile(
            "^" + Pattern.quote(MCC_OPTION_SNAPSHOT_CLASS_NAME) + "$");

    private static final Pattern SYSTEM_CONFIG_SNAPSHOT = Pattern.compile(
            "^" + Pattern.quote(SYSTEM_CONFIG_SNAPSHOT_CLASS_NAME) + "$");

    private static final Pattern HOLIDAY_CALENDAR_MONTH = Pattern.compile(
            "^" + Pattern.quote(HOLIDAY_CALENDAR_MONTH_CLASS_NAME) + "$");

    private static final Pattern HOLIDAY_CALENDAR_YEAR = Pattern.compile(
            "^" + Pattern.quote(HOLIDAY_CALENDAR_YEAR_CLASS_NAME) + "$");

    private static final Pattern HOLIDAY_CALENDAR_DAY = Pattern.compile(
            "^" + Pattern.quote(HOLIDAY_CALENDAR_DAY_CLASS_NAME) + "$");

    private static final Pattern MERCHANT_CURRENT_FEE = Pattern.compile(
            "^" + Pattern.quote(MERCHANT_CURRENT_FEE_CLASS_NAME) + "$");

    private static final Pattern MERCHANT_FEE_RULE = Pattern.compile(
            "^" + Pattern.quote(MERCHANT_FEE_RULE_CLASS_NAME) + "$");

    private static final Pattern MERCHANT_FEE_TIER = Pattern.compile(
            "^" + Pattern.quote(MERCHANT_FEE_TIER_CLASS_NAME) + "$");

    private static final Set<String> REGISTERED_VALUE_TYPES = Set.of(
            String.class.getName(),
            Boolean.class.getName(),
            Integer.class.getName(),
            Long.class.getName(),
            BigDecimal.class.getName(),
            LocalDate.class.getName(),
            LocalDateTime.class.getName(),
            ArrayList.class.getName(),
            LinkedHashMap.class.getName(),
            LinkedHashSet.class.getName(),
            MERCHANT_RUNTIME_PROFILE_CLASS_NAME,
            MERCHANT_KEY_METADATA_CLASS_NAME,
            MERCHANT_ROUTE_PROFILE_CLASS_NAME,
            MERCHANT_ROUTE_OPTION_CLASS_NAME,
            MERCHANT_OPENAPI_ACCESS_POLICY_CLASS_NAME,
            PAYMENT_CARD_BIN_CACHE_ENTRY_CLASS_NAME,
            DICTIONARY_OPTION_SNAPSHOT_CLASS_NAME,
            ENABLED_EMAIL_TEMPLATE_SNAPSHOT_CLASS_NAME,
            MCC_OPTION_SNAPSHOT_CLASS_NAME,
            SYSTEM_CONFIG_SNAPSHOT_CLASS_NAME,
            HOLIDAY_CALENDAR_MONTH_CLASS_NAME,
            HOLIDAY_CALENDAR_YEAR_CLASS_NAME,
            HOLIDAY_CALENDAR_DAY_CLASS_NAME,
            MERCHANT_CURRENT_FEE_CLASS_NAME,
            MERCHANT_FEE_RULE_CLASS_NAME,
            MERCHANT_FEE_TIER_CLASS_NAME
    );

    private PaymentRedisSerializerFactory() {
    }

    /**
     * 创建兼容当前 Redis Value wire format 的 JSON 序列化器。
     *
     * @return 统一 Redis JSON 序列化器
     */
    public static RedisSerializer<Object> create() {
        return new RegisteredTypeRedisSerializer();
    }

    /**
     * 创建指定多态策略的 ObjectMapper；类型验证器始终使用同一份精确登记表。
     *
     * @param defaultTyping 新写或历史只读所需的 Jackson 多态策略
     * @return 已注册时间模块和精确类型验证器的 ObjectMapper
     */
    private static ObjectMapper objectMapper(ObjectMapper.DefaultTyping defaultTyping) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                typeValidator(),
                defaultTyping,
                JsonTypeInfo.As.PROPERTY
        );
        return objectMapper;
    }

    /**
     * 创建历史读取和 v2 读取共享的反序列化类型白名单。
     *
     * @return 只允许已登记缓存类型的验证器
     */
    private static PolymorphicTypeValidator typeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType(MERCHANT_RUNTIME_PROFILE)
                .allowIfSubType(MERCHANT_KEY_METADATA)
                .allowIfSubType(MERCHANT_ROUTE_PROFILE)
                .allowIfSubType(MERCHANT_ROUTE_OPTION)
                .allowIfSubType(MERCHANT_OPENAPI_ACCESS_POLICY)
                .allowIfSubType(PAYMENT_CARD_BIN_CACHE_ENTRY)
                .allowIfSubType(DICTIONARY_OPTION_SNAPSHOT)
                .allowIfSubType(ENABLED_EMAIL_TEMPLATE_SNAPSHOT)
                .allowIfSubType(MCC_OPTION_SNAPSHOT)
                .allowIfSubType(SYSTEM_CONFIG_SNAPSHOT)
                .allowIfSubType(HOLIDAY_CALENDAR_MONTH)
                .allowIfSubType(HOLIDAY_CALENDAR_YEAR)
                .allowIfSubType(HOLIDAY_CALENDAR_DAY)
                .allowIfSubType(MERCHANT_CURRENT_FEE)
                .allowIfSubType(MERCHANT_FEE_RULE)
                .allowIfSubType(MERCHANT_FEE_TIER)
                .allowIfSubType(ArrayList.class)
                .allowIfSubType(LinkedHashMap.class)
                .allowIfSubType(LinkedHashSet.class)
                .allowIfSubType(LocalDate.class)
                .allowIfSubType(LocalDateTime.class)
                .allowIfSubType(BigDecimal.class)
                .allowIfSubType(Long.class)
                .build();
    }

    /**
     * 从根节点开始检查完整 Redis Value 对象图。
     *
     * @param value 待序列化或已经反序列化的 Value
     */
    private static void validateRegisteredValue(Object value) {
        validateRegisteredValue(value, new IdentityHashMap<>());
    }

    /**
     * 递归检查容器成员和登记 DTO 字段，并拒绝循环引用。
     *
     * @param value    当前对象图节点
     * @param visiting 当前递归路径上的对象身份集合
     */
    private static void validateRegisteredValue(Object value, IdentityHashMap<Object, Boolean> visiting) {
        if (value == null) {
            return;
        }
        String className = value.getClass().getName();
        if (!REGISTERED_VALUE_TYPES.contains(className)) {
            throw new SerializationException("Redis value type is not registered: " + className);
        }
        if (isScalar(value)) {
            return;
        }
        if (visiting.put(value, Boolean.TRUE) != null) {
            throw new SerializationException("Cyclic Redis value graphs are not supported");
        }
        try {
            if (value instanceof Map<?, ?> map) {
                map.forEach((key, item) -> {
                    validateRegisteredValue(key, visiting);
                    validateRegisteredValue(item, visiting);
                });
            } else if (value instanceof Iterable<?> iterable) {
                iterable.forEach(item -> validateRegisteredValue(item, visiting));
            } else {
                validateRegisteredBeanFields(value, visiting);
            }
        } finally {
            visiting.remove(value);
        }
    }

    /**
     * 判断 Value 是否为无需继续遍历的已登记标量。
     *
     * @param value 当前对象图节点
     * @return 标量返回 true，否则返回 false
     */
    private static boolean isScalar(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Integer
                || value instanceof Long
                || value instanceof BigDecimal
                || value instanceof LocalDate
                || value instanceof LocalDateTime;
    }

    /**
     * 检查跨模块登记 DTO 的实例字段，避免其内部夹带未登记的多态值。
     *
     * @param bean     已登记的业务 DTO
     * @param visiting 当前递归路径上的对象身份集合
     */
    private static void validateRegisteredBeanFields(Object bean, IdentityHashMap<Object, Boolean> visiting) {
        Class<?> currentType = bean.getClass();
        while (currentType != Object.class) {
            for (Field field : currentType.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                try {
                    if (!field.trySetAccessible()) {
                        throw new SerializationException(
                                "Cannot inspect registered Redis value field: " + currentType.getName());
                    }
                    validateRegisteredValue(field.get(bean), visiting);
                } catch (IllegalAccessException exception) {
                    throw new SerializationException(
                            "Cannot inspect registered Redis value field: " + currentType.getName(), exception);
                }
            }
            currentType = currentType.getSuperclass();
        }
    }

    private static final class RegisteredTypeRedisSerializer implements RedisSerializer<Object> {

        private static final JacksonObjectReader REGISTERED_READER =
                (mapper, source, type) -> mapper.readValue(source, Object.class);

        private static final JacksonObjectWriter REGISTERED_WRITER =
                (mapper, source) -> mapper.writerFor(Object.class).writeValueAsBytes(source);

        private final GenericJackson2JsonRedisSerializer serializerV2 =
                new GenericJackson2JsonRedisSerializer(
                        objectMapper(ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE),
                        REGISTERED_READER,
                        REGISTERED_WRITER
                );

        private final GenericJackson2JsonRedisSerializer legacyReader =
                new GenericJackson2JsonRedisSerializer(objectMapper(ObjectMapper.DefaultTyping.NON_FINAL));

        /**
         * 校验对象图只包含精确注册类型后，以当前安全格式序列化。
         *
         * @param value 待缓存对象
         * @return JSON 字节；空值行为遵循 Spring RedisSerializer 契约
         * @throws SerializationException 对象包含未注册类型或序列化失败时抛出
         */
        @Override
        public byte[] serialize(Object value) throws SerializationException {
            validateRegisteredValue(value);
            return serializerV2.serialize(value);
        }

        /**
         * 优先读取当前安全格式，并仅在兼容期内回退历史只读格式。
         * <p>
         * 两条路径反序列化后都重新校验对象图；历史 {@code NON_FINAL} 配置不用于写入，防止
         * 扩大新的类型反序列化面。
         * </p>
         *
         * @param source Redis Value 字节
         * @return 已校验的缓存对象
         * @throws SerializationException 当前和历史格式均无法安全读取时抛出
         */
        @Override
        public Object deserialize(byte[] source) throws SerializationException {
            try {
                Object value = serializerV2.deserialize(source);
                validateRegisteredValue(value);
                return value;
            } catch (SerializationException currentFormatFailure) {
                try {
                    Object value = legacyReader.deserialize(source);
                    validateRegisteredValue(value);
                    return value;
                } catch (SerializationException legacyFormatFailure) {
                    currentFormatFailure.addSuppressed(legacyFormatFailure);
                    throw currentFormatFailure;
                }
            }
        }
    }
}
