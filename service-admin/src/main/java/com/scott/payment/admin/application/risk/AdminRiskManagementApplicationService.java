package com.scott.payment.admin.application.risk;

import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.admin.mapper.RiskManagementMapper;
import com.scott.payment.admin.support.risk.RiskFunctionDefinition;
import com.scott.payment.admin.support.risk.RiskListValueNormalizer;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.excel.model.ExcelDynamicExportRequest;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelDynamicColumnDefinition;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRiskManagementApplicationService
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 收单风控管理应用服务，负责管理端名单、规则、交易加黑和风控记录的页面编排，不参与实时交易风控决策。
 * @status : create
 */
@Service
public class AdminRiskManagementApplicationService {

    /**
     * DEFAULT SCOPE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DEFAULT_SCOPE = "GLOBAL";
    /**
     * DEFAULT RISK LEVEL 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DEFAULT_RISK_LEVEL = "MEDIUM";
    /**
     * DEFAULT DECISION ACTION 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DEFAULT_DECISION_ACTION = "REVIEW";
    /**
     * VALIDITY SUPER LONG 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String VALIDITY_SUPER_LONG = "SUPER_LONG";
    /**
     * VALIDITY LONG 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String VALIDITY_LONG = "LONG";
    /**
     * VALIDITY LIMITED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String VALIDITY_LIMITED = "LIMITED";
    /**
     * SOURCE MANUAL 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String SOURCE_MANUAL = "MANUAL";
    /**
     * SOURCE IMPORT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String SOURCE_IMPORT = "IMPORT";
    /**
     * MODULE AML 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String MODULE_AML = "AML";
    /**
     * MODULE BLACK 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String MODULE_BLACK = "BLACK";
    /**
     * MODULE WHITE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String MODULE_WHITE = "WHITE";
    /**
     * MODULE RULE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String MODULE_RULE = "RULE";
    /**
     * MODULE TRADE BLACK 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String MODULE_TRADE_BLACK = "TRADE_BLACK";
    /**
     * FUNCTION REGION 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String FUNCTION_REGION = "region";
    /**
     * FUNCTION SOURCE URL 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String FUNCTION_SOURCE_URL = "sourceUrl";
    /**
     * FUNCTION MERCHANT LIMIT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String FUNCTION_MERCHANT_LIMIT = "merchantLimit";
    /**
     * FUNCTION THREE DS 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String FUNCTION_THREE_DS = "threeDs";
    /**
     * FIXED LIMIT CURRENCY USD 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：ISO 4217 三位币种代码；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String FIXED_LIMIT_CURRENCY_USD = "USD";
    /**
     * MERCHANT LIMIT AMOUNT SCALE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int MERCHANT_LIMIT_AMOUNT_SCALE = 2;
    /**
     * THREE DS ALL DIMENSION 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String THREE_DS_ALL_DIMENSION = "ALL";
    /**
     * THREE DS BANK CARD PAYMENT METHOD 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String THREE_DS_BANK_CARD_PAYMENT_METHOD = "BANK_CARD";
    /**
     * THREE DS DEFAULT PAYMENT METHOD 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String THREE_DS_DEFAULT_PAYMENT_METHOD = THREE_DS_ALL_DIMENSION;
    /**
     * THREE DS RULE TYPE RISK 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String THREE_DS_RULE_TYPE_RISK = "RISK_STRATEGY";
    /**
     * THREE DS AMOUNT ALL 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String THREE_DS_AMOUNT_ALL = "ALL";
    /**
     * THREE DS RISK ANY 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String THREE_DS_RISK_ANY = "ANY";
    /**
     * THREE DS ACTION FORCE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String THREE_DS_ACTION_FORCE = "FORCE_3DS";
    /**
     * THREE DS DEFAULT PRIORITY 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int THREE_DS_DEFAULT_PRIORITY = 100;
    /**
     * THREE DS AMOUNT SCALE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int THREE_DS_AMOUNT_SCALE = 2;
    /**
     * LIMIT TYPE DAILY 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String LIMIT_TYPE_DAILY = "DAILY";
    /**
     * LIMIT TYPE WEEKLY 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String LIMIT_TYPE_WEEKLY = "WEEKLY";
    /**
     * LIMIT TYPE MONTHLY 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String LIMIT_TYPE_MONTHLY = "MONTHLY";
    /**
     * WEEKLY LIMIT MULTIPLIER 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final BigDecimal WEEKLY_LIMIT_MULTIPLIER = BigDecimal.valueOf(7);
    /**
     * MONTHLY LIMIT MULTIPLIER 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final BigDecimal MONTHLY_LIMIT_MULTIPLIER = BigDecimal.valueOf(4);
    /**
     * TRADE BLACK FUNCTION SYSTEM 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRADE_BLACK_FUNCTION_SYSTEM = "system";
    /**
     * TRADE BLACK DISPLAY NAME 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRADE_BLACK_DISPLAY_NAME = "系统交易加黑";
    /**
     * TRADE BLACK RELEASE DISPLAY NAME 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRADE_BLACK_RELEASE_DISPLAY_NAME = "解除系统交易加黑";
    /**
     * TRADE BLACK ACTION ADD 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String TRADE_BLACK_ACTION_ADD = "ADD";
    /**
     * CHANGE OPERATION CREATE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String CHANGE_OPERATION_CREATE = "CREATE";
    /**
     * CHANGE OPERATION UPDATE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：系统时区时间；格式：ISO 日期或日期时间；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String CHANGE_OPERATION_UPDATE = "UPDATE";
    /**
     * CHANGE OPERATION DELETE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String CHANGE_OPERATION_DELETE = "DELETE";
    /**
     * CHANGE OPERATION STATUS 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String CHANGE_OPERATION_STATUS = "STATUS";
    /**
     * CHANGE OPERATION IMPORT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String CHANGE_OPERATION_IMPORT = "IMPORT";
    /**
     * CHANGE OPERATION BATCH 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String CHANGE_OPERATION_BATCH = "BATCH";
    /**
     * CHANGE OPERATION RELEASE 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String CHANGE_OPERATION_RELEASE = "RELEASE";
    /**
     * EMPTY DISPLAY TEXT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String EMPTY_DISPLAY_TEXT = "-";
    /**
     * SNAPSHOT DISPLAY TEXT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String SNAPSHOT_DISPLAY_TEXT = "查看快照";
    /**
     * CONFIG ID DISPLAY PREFIX 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String CONFIG_ID_DISPLAY_PREFIX = "配置ID：";
    /**
     * ENABLED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * LONG VALIDITY MIN DAYS 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int LONG_VALIDITY_MIN_DAYS = 120;
    /**
     * IMPORT ERROR MESSAGE MAX LENGTH 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int IMPORT_ERROR_MESSAGE_MAX_LENGTH = 1000;
    /**
     * IMPORT RAW CONTENT MAX LENGTH 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int IMPORT_RAW_CONTENT_MAX_LENGTH = 4000;
    /**
     * EXCEL IMPORT HEADER SCAN ROWS 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int EXCEL_IMPORT_HEADER_SCAN_ROWS = 10;
    private static final Set<String> VALID_MATCH_MODES = Set.of("EXACT", "DOMAIN", "CONTAINS", "REGEX");
    private static final Set<String> MERCHANT_LIMIT_TYPES = Set.of("SINGLE_MIN", "SINGLE_MAX", "DAILY", "WEEKLY", "MONTHLY");
    private static final Set<String> THREE_DS_RULE_TYPES = Set.of("RISK_STRATEGY", "EXEMPTION_STRATEGY", "CHANNEL_POLICY");
    private static final Set<String> THREE_DS_AMOUNT_MATCH_TYPES = Set.of("ALL", "GE", "LE", "BETWEEN");
    private static final Set<String> THREE_DS_RISK_CONDITIONS = Set.of("ANY", "LOW_AND_ABOVE", "MEDIUM_AND_ABOVE", "HIGH_AND_ABOVE", "CRITICAL_ONLY");
    private static final Set<String> THREE_DS_TRIGGER_ACTIONS = Set.of("FORCE_3DS", "SKIP_3DS", "FOLLOW_DEFAULT");
    private static final Set<String> FREQUENCY_ELEMENT_CODES = Set.of("cardNo", "cardFingerprint", "ip", "email", "phone", "customerId", "deviceFingerprint");
    /**
     * AMOUNT TEXT PATTERN 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：金额单位由关联币种决定，比例字段按业务配置解释；格式：decimal；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final Pattern AMOUNT_TEXT_PATTERN = Pattern.compile("(?<![A-Za-z0-9])\\d+(?:\\.\\d+)?");
    private static final Map<String, String> MODULE_DISPLAY_NAMES = Map.of(
            MODULE_AML, "AML强制拦截",
            MODULE_BLACK, "黑名单管理",
            MODULE_WHITE, "白名单管理",
            MODULE_RULE, "内风控规则管理",
            MODULE_TRADE_BLACK, TRADE_BLACK_DISPLAY_NAME
    );
    private static final Map<String, String> CHANGE_OPERATION_DISPLAY_NAMES = Map.of(
            CHANGE_OPERATION_CREATE, "新增",
            CHANGE_OPERATION_UPDATE, "修改",
            CHANGE_OPERATION_DELETE, "删除",
            CHANGE_OPERATION_STATUS, "状态变更",
            CHANGE_OPERATION_IMPORT, "导入",
            CHANGE_OPERATION_BATCH, "批量操作",
            CHANGE_OPERATION_RELEASE, "解除"
    );
    private static final List<String> IMPORT_COLUMN_KEYS = List.of(
            "merchantScope",
            "merchantId",
            "ruleName",
            "matchValuePlain",
            "matchValueMasked",
            "matchValueHash",
            "matchValueStart",
            "matchValueEnd",
            "ipVersion",
            "cardBrand",
            "countryAlpha2",
            "countryAlpha3",
            "countryNumeric",
            "riskLevel",
            "decisionAction",
            "validityType",
            "validityDays",
            "sourceType",
            "status",
            "remark",
            "regionMatchLevel",
            "stateProvinceName",
            "cityName",
            "matchMode",
            "matchValue",
            "sourceUrl",
            "sourceHost",
            "limitType",
            "limitAmount",
            "amountMin",
            "amountMax",
            "currency",
            "ruleType",
            "channelCode",
            "paymentMethodCardBrand",
            "paymentMethod",
            "amountCondition",
            "amountMatchType",
            "riskCondition",
            "triggerAction",
            "priority",
            "statDimension",
            "elementSet",
            "windowValue",
            "windowUnit",
            "maxTransactionCount",
            "maxSuccessCount",
            "timeWindowSeconds",
            "thresholdCount",
            "elementsJson",
            "effectiveTime",
            "expireTime"
    );

    /**
     * risk Management Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final RiskManagementMapper riskManagementMapper;
    /**
     * risk List Value Normalizer 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final RiskListValueNormalizer riskListValueNormalizer;
    /**
     * import Log Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final AdminRiskImportLogService importLogService;
    /**
     * excel Export Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExcelExportService excelExportService;
    /**
     * excel I18n Message Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExcelI18nMessageResolver excelI18nMessageResolver;
    /**
     * excel Locale Resolver 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final ExcelLocaleResolver excelLocaleResolver;

    /**
     * 创建风控管理应用服务。
     *
     * @param riskManagementMapper     风控管理数据访问接口
     * @param riskListValueNormalizer  名单匹配值归一化组件
     * @param importLogService         导入批次日志服务
     * @param excelExportService       Excel 导出服务
     * @param excelI18nMessageResolver Excel 国际化解析器
     * @param excelLocaleResolver      Excel 语言环境解析器
     */
    public AdminRiskManagementApplicationService(RiskManagementMapper riskManagementMapper,
                                                 RiskListValueNormalizer riskListValueNormalizer,
                                                 AdminRiskImportLogService importLogService,
                                                 ExcelExportService excelExportService,
                                                 ExcelI18nMessageResolver excelI18nMessageResolver,
                                                 ExcelLocaleResolver excelLocaleResolver) {
        this.riskManagementMapper = riskManagementMapper;
        this.riskListValueNormalizer = riskListValueNormalizer;
        this.importLogService = importLogService;
        this.excelExportService = excelExportService;
        this.excelI18nMessageResolver = excelI18nMessageResolver;
        this.excelLocaleResolver = excelLocaleResolver;
    }

    /**
     * 查询全部风险功能定义。
     *
     * @return 功能定义列表
     */
    public List<RiskDTOs.FunctionDefinitionResponse> functions() {
        return RiskFunctionDefinition.all().stream().map(this::toDefinitionResponse).toList();
    }

    /**
     * 查询页面下拉选项。
     *
     * @return 页面下拉选项
     */
    public RiskDTOs.RiskOptionsResponse options() {
        RiskDTOs.RiskOptionsResponse response = new RiskDTOs.RiskOptionsResponse();
        response.setStatusOptions(List.of(option("启用", "1", "success"), option("停用", "0", "info")));
        response.setMerchantScopeOptions(List.of(option("全局风控", "GLOBAL", null), option("商户风控", "MERCHANT", null)));
        response.setRiskLevelOptions(List.of(
                option("低风险", "LOW", "success"),
                option("中风险", "MEDIUM", "warning"),
                option("高风险", "HIGH", "danger"),
                option("严重风险", "CRITICAL", "danger")
        ));
        response.setDecisionActionOptions(List.of(
                option("通过", "PASS", "success"),
                option("拒绝", "REJECT", "danger"),
                option("人工复核", "REVIEW", "warning")
        ));
        response.setCardBrandOptions(toOptions(riskManagementMapper.selectDictOptions("card_brand", "zh-CN")));
        response.setLimitTypeOptions(toOptions(riskManagementMapper.selectDictOptions("channel_limit_type", "zh-CN")));
        response.setCountryOptions(toOptions(riskManagementMapper.selectCountryOptions()));
        response.setCurrencyOptions(toOptions(riskManagementMapper.selectCurrencyOptions()));
        response.setValidityTypeOptions(List.of(
                option("超长期", VALIDITY_SUPER_LONG, "success"),
                option("长期", VALIDITY_LONG, "warning"),
                option("限定有效期", VALIDITY_LIMITED, "info")
        ));
        response.setSourceTypeOptions(List.of(
                option("手工录入", "MANUAL", "primary"),
                option("批量导入", SOURCE_IMPORT, "warning"),
                option("系统生成", "SYSTEM", "info")
        ));
        return response;
    }

    /**
     * 分页查询 AML、黑名单或白名单配置列表。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param request      查询条件，允许为空，为空时使用默认分页
     * @return 名单分页数据，响应值仅返回脱敏展示字段和配置字段
     */
    public PageResult<RiskDTOs.RiskRecordResponse> pageList(String moduleType, String functionCode, RiskDTOs.RiskListQueryRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "list");
        RiskDTOs.RiskListQueryRequest query = request == null ? new RiskDTOs.RiskListQueryRequest() : request;
        applyAmlGlobalScope(definition, query);
        if (definition.isRegionFunction()) {
            String countryAlpha3 = countryAlpha3FromAlpha2(query.getCountryAlpha2());
            long total = riskManagementMapper.countRegion(query.getMerchantScope(), query.getMerchantId(), query.getMatchValue(), countryAlpha3, query.getStatus());
            List<RiskDTOs.RiskRecordResponse> rows = riskManagementMapper.selectRegionPage(
                    query.getMerchantScope(),
                    query.getMerchantId(),
                    query.getMatchValue(),
                    countryAlpha3,
                    query.getStatus(),
                    offset(query.safePageNo(), query.safePageSize()),
                    query.safePageSize()
            ).stream().map(this::toRecordResponse).toList();
            return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
        }
        String cardBinLookupNumber = cardBinLookupNumber(definition, query.getMatchValue());
        String countryAlpha3 = hasCountryFields(definition) ? countryAlpha3FromAlpha2(query.getCountryAlpha2()) : query.getCountryAlpha2();
        long total = riskManagementMapper.countList(definition.getTableName(), query.getMerchantScope(), query.getMerchantId(), query.getMatchValue(), cardBinLookupNumber, countryAlpha3, query.getStatus(), hasCountryFields(definition));
        List<RiskDTOs.RiskRecordResponse> rows = riskManagementMapper.selectListPage(
                definition.getTableName(),
                query.getMerchantScope(),
                query.getMerchantId(),
                query.getMatchValue(),
                cardBinLookupNumber,
                countryAlpha3,
                query.getStatus(),
                offset(query.safePageNo(), query.safePageSize()),
                query.safePageSize(),
                hasCountryFields(definition)
        ).stream().map(this::toRecordResponse).toList();
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 查询 AML、黑名单或白名单配置详情。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param id           配置记录ID
     * @return 名单配置详情
     */
    public RiskDTOs.RiskRecordResponse listDetail(String moduleType, String functionCode, Long id) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "detail");
        return toRecordResponse(requireRecord(definition.getTableName(), id));
    }

    /**
     * 查询名单编辑详情。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param id           配置记录ID
     * @return 编辑详情，敏感明文仅在该接口授权后返回
     */
    public RiskDTOs.RiskRecordResponse listEditDetail(String moduleType, String functionCode, Long id) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "edit");
        Map<String, Object> record = requireRecord(definition.getTableName(), id);
        RiskDTOs.RiskRecordResponse response = toRecordResponse(record);
        String cipherText = asString(record.get("match_value_cipher"));
        response.setMatchValuePlain(StringUtils.hasText(cipherText)
                ? riskListValueNormalizer.decryptPlain(cipherText)
                : asString(record.get("match_value_masked")));
        return response;
    }

    /**
     * 新增 AML、黑名单或白名单配置。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param request      新增请求，敏感元素必须由调用方传入脱敏值或哈希值
     * @return 新增后的配置记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse createList(String moduleType, String functionCode, RiskDTOs.RiskListSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "add");
        List<RiskDTOs.RiskListSaveRequest> requests = expandCountryListRequests(definition, request);
        Map<String, Object> lastData = null;
        for (RiskDTOs.RiskListSaveRequest itemRequest : requests) {
            Map<String, Object> data = listData(definition, itemRequest, SOURCE_MANUAL);
            ensureListNotDuplicated(definition, null, data);
            int rows = riskManagementMapper.insertListRecord(definition.getTableName(), data, currentOperatorName(),
                    hasRangeFields(definition), hasCardBrandField(definition), hasCountryFields(definition),
                    hasCountryNumericField(definition), hasIpVersionField(definition), hasSourceHostField(definition));
            if (rows != 1) {
                throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "新增风控名单失败");
            }
            writeChange(definition, null, CHANGE_OPERATION_CREATE, null, data);
            lastData = data;
        }
        return latestListRecord(definition, lastData == null ? Map.of() : lastData);
    }

    /**
     * 修改 AML、黑名单或白名单配置。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param id           配置记录ID
     * @param request      修改请求，敏感元素必须由调用方传入脱敏值或哈希值
     * @return 修改后的配置记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse updateList(String moduleType, String functionCode, Long id, RiskDTOs.RiskListSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "edit");
        Map<String, Object> before = requireRecord(definition.getTableName(), id);
        Map<String, Object> data = listData(definition, request, SOURCE_MANUAL);
        ensureListNotDuplicated(definition, id, data);
        int rows = riskManagementMapper.updateListRecord(definition.getTableName(), id, data, currentOperatorName(),
                hasRangeFields(definition), hasCardBrandField(definition), hasCountryFields(definition),
                hasCountryNumericField(definition), hasIpVersionField(definition), hasSourceHostField(definition));
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "风控配置记录不存在");
        }
        writeChange(definition, id, CHANGE_OPERATION_UPDATE, before, data);
        return listDetail(moduleType, functionCode, id);
    }

    /**
     * 新增高风险区域黑名单配置。
     *
     * @param request 区域保存请求，支持国家、州省、城市三级区域粒度
     * @return 新增后的区域配置记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse createRegion(RiskDTOs.RegionSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(MODULE_BLACK, FUNCTION_REGION);
        ensureFunctionPermission(definition, "add");
        List<String> countryCodes = regionCreateCountryCodes(request);
        Map<String, Object> lastData = null;
        for (String countryCode : countryCodes) {
            request.setCountryAlpha2(countryCode);
            Map<String, Object> data = regionData(request, SOURCE_MANUAL);
            ensureRegionNotDuplicated(null, data);
            int rows = riskManagementMapper.insertRegion(data, currentOperatorName());
            if (rows != 1) {
                throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "新增高风险区域失败");
            }
            writeChange(definition, null, CHANGE_OPERATION_CREATE, null, data);
            lastData = data;
        }
        return latestListRecord(definition, lastData);
    }

    /**
     * 修改高风险区域黑名单配置。
     *
     * @param id      区域配置记录ID
     * @param request 区域保存请求，支持国家、州省、城市三级区域粒度
     * @return 修改后的区域配置记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse updateRegion(Long id, RiskDTOs.RegionSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(MODULE_BLACK, FUNCTION_REGION);
        ensureFunctionPermission(definition, "edit");
        Map<String, Object> before = requireRecord(definition.getTableName(), id);
        Map<String, Object> data = regionData(request, SOURCE_MANUAL);
        ensureRegionNotDuplicated(id, data);
        int rows = riskManagementMapper.updateRegion(id, data, currentOperatorName());
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "高风险区域记录不存在");
        }
        writeChange(definition, id, CHANGE_OPERATION_UPDATE, before, data);
        return listDetail(MODULE_BLACK, FUNCTION_REGION, id);
    }

    /**
     * 删除名单或规则配置，采用软删除并记录配置变更日志。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param id           配置记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(String moduleType, String functionCode, Long id) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "remove");
        Map<String, Object> before = requireRecord(definition.getTableName(), id);
        int rows = riskManagementMapper.softDelete(definition.getTableName(), id, currentOperatorName());
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "风控配置记录不存在");
        }
        writeChange(definition, id, CHANGE_OPERATION_DELETE, before, null);
    }

    /**
     * 批量删除名单或规则配置，逐条软删除并记录配置变更日志。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param request      批量删除请求，ID 列表不能为空
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchRemove(String moduleType, String functionCode, RiskDTOs.BatchRemoveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "remove");
        List<Long> ids = request == null || request.getIds() == null
                ? List.of()
                : request.getIds().stream().filter(id -> id != null).distinct().toList();
        if (ids.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择需要删除的记录");
        }
        String operator = currentOperatorName();
        for (Long id : ids) {
            Map<String, Object> before = requireRecord(definition.getTableName(), id);
            int rows = riskManagementMapper.softDelete(definition.getTableName(), id, operator);
            if (rows != 1) {
                throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "风控配置记录不存在");
            }
            writeChange(definition, id, CHANGE_OPERATION_DELETE, before, null);
        }
    }

    /**
     * 更新名单或规则状态，状态值只允许启用或停用。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param id           配置记录ID
     * @param request      状态更新请求
     * @return 更新后的配置记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse updateStatus(String moduleType, String functionCode, Long id, RiskDTOs.StatusUpdateRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "status");
        Integer status = request == null ? null : request.getStatus();
        if (status == null || (status != 0 && status != 1)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "状态值不正确");
        }
        Map<String, Object> before = requireRecord(definition.getTableName(), id);
        int rows = riskManagementMapper.updateStatus(definition.getTableName(), id, status, currentOperatorName());
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "风控配置记录不存在");
        }
        writeChange(definition, id, CHANGE_OPERATION_STATUS, before, Map.of("status", status));
        return toRecordResponse(requireRecord(definition.getTableName(), id));
    }

    /**
     * 分页查询内风控规则配置。
     *
     * @param functionCode 规则功能编码，用于解析物理表白名单和功能级权限
     * @param request      查询条件，允许为空，为空时使用默认分页
     * @return 规则配置分页数据
     */
    public PageResult<RiskDTOs.RiskRecordResponse> pageRules(String functionCode, RiskDTOs.RiskRuleQueryRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(MODULE_RULE, functionCode);
        ensureFunctionPermission(definition, "list");
        RiskDTOs.RiskRuleQueryRequest query = request == null ? new RiskDTOs.RiskRuleQueryRequest() : request;
        if (isSourceUrlRule(definition)) {
            String sourceHost = normalizeSourceHostQuery(defaultIfBlank(query.getSourceHost(), query.getMatchValue()));
            long total = riskManagementMapper.countSourceUrlRules(query.getMerchantId(), trim(query.getSourceUrl()), sourceHost, query.getStatus());
            List<RiskDTOs.RiskRecordResponse> rows = riskManagementMapper.selectSourceUrlRulePage(
                    query.getMerchantId(),
                    trim(query.getSourceUrl()),
                    sourceHost,
                    query.getStatus(),
                    offset(query.safePageNo(), query.safePageSize()),
                    query.safePageSize()
            ).stream().map(this::toRecordResponse).toList();
            return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
        }
        String matchValue = normalizeRuleQueryMatchValue(definition, query.getMatchValue());
        if (isMerchantLimitRule(definition)) {
            long total = riskManagementMapper.countMerchantLimitRules(query.getMerchantScope(), query.getMerchantId(), query.getRuleName(), matchValue, query.getLimitType(), query.getStatus());
            List<RiskDTOs.RiskRecordResponse> rows = riskManagementMapper.selectMerchantLimitRulePage(
                    query.getMerchantScope(),
                    query.getMerchantId(),
                    query.getRuleName(),
                    matchValue,
                    query.getLimitType(),
                    query.getStatus(),
                    offset(query.safePageNo(), query.safePageSize()),
                    query.safePageSize()
            ).stream().map(this::toRecordResponse).toList();
            return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
        }
        if (isThreeDsRule(definition)) {
            long total = riskManagementMapper.countThreeDsRules(
                    query.getMerchantScope(),
                    query.getMerchantId(),
                    query.getRuleName(),
                    upper(query.getRuleType()),
                    upper(query.getChannelCode()),
                    upper(query.getPaymentMethod()),
                    upper(query.getCardBrand()),
                    upper(query.getCurrency()),
                    upper(query.getTriggerAction()),
                    query.getStatus()
            );
            List<RiskDTOs.RiskRecordResponse> rows = riskManagementMapper.selectThreeDsRulePage(
                    query.getMerchantScope(),
                    query.getMerchantId(),
                    query.getRuleName(),
                    upper(query.getRuleType()),
                    upper(query.getChannelCode()),
                    upper(query.getPaymentMethod()),
                    upper(query.getCardBrand()),
                    upper(query.getCurrency()),
                    upper(query.getTriggerAction()),
                    query.getStatus(),
                    offset(query.safePageNo(), query.safePageSize()),
                    query.safePageSize()
            ).stream().map(this::toRecordResponse).toList();
            return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
        }
        long total = riskManagementMapper.countRules(definition.getTableName(), query.getMerchantScope(), query.getMerchantId(), query.getRuleName(), matchValue, query.getLimitType(), query.getCurrency(), query.getStatus());
        List<RiskDTOs.RiskRecordResponse> rows = riskManagementMapper.selectRulePage(
                definition.getTableName(),
                query.getMerchantScope(),
                query.getMerchantId(),
                query.getRuleName(),
                matchValue,
                query.getLimitType(),
                query.getCurrency(),
                query.getStatus(),
                offset(query.safePageNo(), query.safePageSize()),
                query.safePageSize()
        ).stream().map(this::toRecordResponse).toList();
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 查询内风控规则详情。
     *
     * @param functionCode 规则功能编码，用于解析物理表白名单和功能级权限
     * @param id           规则记录ID
     * @return 规则详情
     */
    public RiskDTOs.RiskRecordResponse ruleDetail(String functionCode, Long id) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(MODULE_RULE, functionCode);
        ensureFunctionPermission(definition, "detail");
        return toRecordResponse(requireRecord(definition.getTableName(), id));
    }

    /**
     * 新增内风控规则配置。
     *
     * @param functionCode 规则功能编码，用于解析物理表白名单和功能级权限
     * @param request      规则保存请求，金额字段使用 BigDecimal
     * @return 新增后的规则配置
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse createRule(String functionCode, RiskDTOs.RiskRuleSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(MODULE_RULE, functionCode);
        ensureFunctionPermission(definition, "add");
        if (isSourceUrlRule(definition)) {
            List<RiskDTOs.RiskRecordResponse> records = createSourceUrlRules(toSourceUrlBatchRequest(request));
            return records.isEmpty() ? new RiskDTOs.RiskRecordResponse() : records.get(0);
        }
        if (isThreeDsRule(definition) && hasThreeDsCardBrands(request)) {
            List<RiskDTOs.RiskRecordResponse> records = createThreeDsRules(request);
            return records.isEmpty() ? new RiskDTOs.RiskRecordResponse() : records.get(records.size() - 1);
        }
        Map<String, Object> data = ruleData(definition, request);
        validateMerchantLimitAmountRelations(definition, data, null);
        ensureRuleNotDuplicated(definition, null, data);
        int rows;
        if (isMerchantLimitRule(definition)) {
            rows = riskManagementMapper.insertMerchantLimitRule(data, currentOperatorName());
        } else if (isThreeDsRule(definition)) {
            rows = riskManagementMapper.insertThreeDsRule(data, currentOperatorName());
        } else {
            rows = riskManagementMapper.insertRule(definition.getTableName(), data, currentOperatorName());
        }
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "新增风控规则失败");
        }
        writeChange(definition, null, CHANGE_OPERATION_CREATE, null, data);
        return latestListRecord(definition, data);
    }

    /**
     * 批量新增 3DS 规则。卡品牌在交易匹配中仍保持单值索引口径，管理端多选只负责拆分配置。
     *
     * @param request 管理端 3DS 保存请求
     * @return 按卡品牌拆分后的新增记录
     */
    private List<RiskDTOs.RiskRecordResponse> createThreeDsRules(RiskDTOs.RiskRuleSaveRequest request) {
        List<RiskDTOs.RiskRecordResponse> records = new ArrayList<>();
        String ruleGroupNo = defaultIfBlank(request.getRuleGroupNo(), UUID.randomUUID().toString());
        for (String cardBrand : normalizedThreeDsCardBrands(request)) {
            RiskDTOs.RiskRuleSaveRequest itemRequest = copyThreeDsRuleRequest(request, ruleGroupNo, cardBrand);
            Map<String, Object> data = ruleData(RiskFunctionDefinition.RULE_3DS, itemRequest);
            ensureRuleNotDuplicated(RiskFunctionDefinition.RULE_3DS, null, data);
            int rows = riskManagementMapper.insertThreeDsRule(data, currentOperatorName());
            if (rows != 1) {
                throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "新增3DS规则失败");
            }
            writeChange(RiskFunctionDefinition.RULE_3DS, null, CHANGE_OPERATION_CREATE, null, data);
            records.add(latestListRecord(RiskFunctionDefinition.RULE_3DS, data));
        }
        return records;
    }

    /**
     * 判断 has Three Ds Card Brands 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean hasThreeDsCardBrands(RiskDTOs.RiskRuleSaveRequest request) {
        return request != null && request.getCardBrands() != null && !request.getCardBrands().isEmpty();
    }

    /**
     * 归一化 3DS 多选卡品牌。选择 ALL 时代表全品牌，不再拆分其他品牌，避免规则语义重叠。
     */
    private List<String> normalizedThreeDsCardBrands(RiskDTOs.RiskRuleSaveRequest request) {
        if (!THREE_DS_BANK_CARD_PAYMENT_METHOD.equals(upper(request.getPaymentMethod()))) {
            return List.of(THREE_DS_ALL_DIMENSION);
        }
        List<String> cardBrands = request.getCardBrands().stream()
                .map(this::upper)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (cardBrands.isEmpty() || cardBrands.contains(THREE_DS_ALL_DIMENSION)) {
            return List.of(THREE_DS_ALL_DIMENSION);
        }
        return cardBrands;
    }

    /**
     * 复制 3DS 保存请求并替换单个卡品牌，保证后续校验和入库仍沿用单品牌交易匹配口径。
     */
    private RiskDTOs.RiskRuleSaveRequest copyThreeDsRuleRequest(RiskDTOs.RiskRuleSaveRequest source,
                                                                String ruleGroupNo,
                                                                String cardBrand) {
        RiskDTOs.RiskRuleSaveRequest target = new RiskDTOs.RiskRuleSaveRequest();
        target.setMerchantScope(source.getMerchantScope());
        target.setMerchantId(source.getMerchantId());
        target.setMerchantName(source.getMerchantName());
        target.setRuleGroupNo(ruleGroupNo);
        target.setRuleName(source.getRuleName());
        target.setAmountMin(source.getAmountMin());
        target.setAmountMax(source.getAmountMax());
        target.setCurrency(source.getCurrency());
        target.setRuleType(source.getRuleType());
        target.setChannelCode(source.getChannelCode());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setCardBrand(cardBrand);
        target.setAmountMatchType(source.getAmountMatchType());
        target.setRiskCondition(source.getRiskCondition());
        target.setTriggerAction(source.getTriggerAction());
        target.setPriority(source.getPriority());
        target.setEffectiveTime(source.getEffectiveTime());
        target.setExpireTime(source.getExpireTime());
        target.setStatus(source.getStatus());
        target.setRemark(source.getRemark());
        return target;
    }

    /**
     * 修改内风控规则配置。
     *
     * @param functionCode 规则功能编码，用于解析物理表白名单和功能级权限
     * @param id           规则记录ID
     * @param request      规则保存请求，金额字段使用 BigDecimal
     * @return 修改后的规则配置
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.RiskRecordResponse updateRule(String functionCode, Long id, RiskDTOs.RiskRuleSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(MODULE_RULE, functionCode);
        ensureFunctionPermission(definition, "edit");
        Map<String, Object> before = requireRecord(definition.getTableName(), id);
        if (isSourceUrlRule(definition)) {
            Map<String, Object> data = sourceUrlData(request);
            ensureSourceUrlNotDuplicated(id, data);
            int rows = riskManagementMapper.updateSourceUrlRule(id, data, currentOperatorName());
            if (rows != 1) {
                throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "来源网址不存在");
            }
            writeChange(definition, id, CHANGE_OPERATION_UPDATE, before, data);
            return ruleDetail(functionCode, id);
        }
        Map<String, Object> data = ruleData(definition, request);
        validateMerchantLimitAmountRelations(definition, data, id);
        ensureRuleNotDuplicated(definition, id, data);
        int rows;
        if (isMerchantLimitRule(definition)) {
            rows = riskManagementMapper.updateMerchantLimitRule(id, data, currentOperatorName());
        } else if (isThreeDsRule(definition)) {
            rows = riskManagementMapper.updateThreeDsRule(id, data, currentOperatorName());
        } else {
            rows = riskManagementMapper.updateRule(definition.getTableName(), id, data, currentOperatorName());
        }
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "风控规则不存在");
        }
        writeChange(definition, id, CHANGE_OPERATION_UPDATE, before, data);
        return ruleDetail(functionCode, id);
    }

    /**
     * 批量新增商户来源网址限定。该配置按商户号直接生效，交易链路后续按 merchant_id 和 source_host 匹配。
     *
     * @param request 来源网址批量保存请求
     * @return 新增后的来源网址记录列表
     */
    @Transactional(rollbackFor = Exception.class)
    public List<RiskDTOs.RiskRecordResponse> createSourceUrlRules(RiskDTOs.RiskSourceUrlBatchSaveRequest request) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(MODULE_RULE, FUNCTION_SOURCE_URL);
        ensureFunctionPermission(definition, "add");
        List<Map<String, Object>> dataList = sourceUrlBatchData(request);
        String operator = currentOperatorName();
        List<RiskDTOs.RiskRecordResponse> records = new ArrayList<>();
        for (Map<String, Object> data : dataList) {
            ensureSourceUrlNotDuplicated(null, data);
            int rows = riskManagementMapper.insertSourceUrlRule(data, operator);
            if (rows != 1) {
                throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "新增来源网址失败");
            }
            writeChange(definition, null, CHANGE_OPERATION_CREATE, null, data);
            records.add(latestSourceUrlRecord(data));
        }
        return records;
    }

    /**
     * 查询风控工作台概览。
     *
     * @return 各功能配置数量、启用数量和最近配置变更
     */
    public Map<String, Object> dashboard() {
        List<Map<String, Object>> groups = new ArrayList<>();
        for (RiskFunctionDefinition definition : RiskFunctionDefinition.all()) {
            Map<String, Object> stats = riskManagementMapper.selectDashboardStats(definition.getTableName());
            long total = asLong(stats.get("total"));
            long enabled = asLong(stats.get("enabled"));
            long disabled = Math.max(total - enabled, 0);
            Map<String, Object> latestChange = riskManagementMapper.selectLatestChangeLog(definition.getModuleType(), definition.getFunctionCode());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("moduleType", definition.getModuleType());
            item.put("functionCode", definition.getFunctionCode());
            item.put("functionName", definition.getFunctionName());
            item.put("routePath", definition.getRoutePath());
            item.put("permissionPrefix", definition.getPermissionPrefix());
            item.put("regionFunction", definition.isRegionFunction());
            item.put("ruleFunction", definition.isRuleFunction());
            item.put("total", total);
            item.put("enabled", enabled);
            item.put("disabled", disabled);
            item.put("enabledRate", total == 0 ? 0 : Math.round(enabled * 100.0D / total));
            item.put("configured", total > 0);
            item.put("latestUpdateTime", stats.get("latest_update_time"));
            if (latestChange != null) {
                item.put("latestOperationType", latestChange.get("operation_type"));
                item.put("latestOperator", latestChange.get("operator"));
                item.put("latestOperationTime", latestChange.get("operation_time"));
            }
            groups.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("functions", groups);
        result.put("changeLogs", enrichChangeLogRows(riskManagementMapper.selectChangeLogs(0, 10)));
        return result;
    }

    /**
     * 查询今日风险事件，供风险工作台独立页面展示。
     *
     * @return 当日风控评估记录，按决策时间倒序
     */
    public List<Map<String, Object>> todayRiskEvents() {
        return riskManagementMapper.selectTodayRiskEvents(100);
    }

    /**
     * 查询高风险商户排行，供风险工作台独立页面展示。
     *
     * @return 近 30 天商户风险统计，按高风险命中数倒序
     */
    public List<Map<String, Object>> merchantRiskRanking() {
        return riskManagementMapper.selectMerchantRiskRanking(20);
    }

    /**
     * 分页查询配置变更日志。
     *
     * @param request 分页请求，允许为空，为空时使用默认分页
     * @return 配置变更日志分页数据
     */
    public PageResult<Map<String, Object>> pageChangeLogs(PageRequestAdapter request) {
        PageRequestAdapter query = request == null ? new PageRequestAdapter() : request;
        long total = riskManagementMapper.countChangeLogs();
        List<Map<String, Object>> rows = enrichChangeLogRows(riskManagementMapper.selectChangeLogs(offset(query.safePageNo(), query.safePageSize()), query.safePageSize()));
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 分页查询风控评估记录。
     *
     * @param request 查询条件，允许按商户号、商户订单号、平台订单号和决策结果过滤
     * @return 风控评估记录分页数据
     */
    public PageResult<Map<String, Object>> pageEvaluations(RiskDTOs.EvaluationQueryRequest request) {
        RiskDTOs.EvaluationQueryRequest query = request == null ? new RiskDTOs.EvaluationQueryRequest() : request;
        long total = riskManagementMapper.countEvaluations(query.getMerchantId(), query.getMerchantOrderNo(), query.getPaymentOrderNo(), query.getDecisionResult());
        List<Map<String, Object>> rows = riskManagementMapper.selectEvaluations(
                query.getMerchantId(),
                query.getMerchantOrderNo(),
                query.getPaymentOrderNo(),
                query.getDecisionResult(),
                offset(query.safePageNo(), query.safePageSize()),
                query.safePageSize()
        );
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 查询单笔风控评估命中明细。
     *
     * @param riskRecordNo 风控记录号
     * @return 命中明细列表
     */
    public List<Map<String, Object>> evaluationHits(String riskRecordNo) {
        if (!StringUtils.hasText(riskRecordNo)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "风控记录号不能为空");
        }
        return riskManagementMapper.selectEvaluationHits(riskRecordNo);
    }

    /**
     * 分页查询系统交易加黑记录。
     *
     * @param request 查询条件，允许按商户、订单号、加黑对象类型和状态过滤
     * @return 系统交易加黑分页数据
     */
    public PageResult<Map<String, Object>> pageTradeBlack(RiskDTOs.TradeBlackQueryRequest request) {
        RiskDTOs.TradeBlackQueryRequest query = request == null ? new RiskDTOs.TradeBlackQueryRequest() : request;
        long total = riskManagementMapper.countTradeBlack(query.getMerchantId(), query.getMerchantOrderNo(), query.getPaymentOrderNo(), query.getBlackTargetType(), query.getStatus());
        List<Map<String, Object>> rows = riskManagementMapper.selectTradeBlack(
                query.getMerchantId(),
                query.getMerchantOrderNo(),
                query.getPaymentOrderNo(),
                query.getBlackTargetType(),
                query.getStatus(),
                offset(query.safePageNo(), query.safePageSize()),
                query.safePageSize()
        );
        return PageResult.of(total, query.safePageNo(), query.safePageSize(), rows);
    }

    /**
     * 新增系统交易加黑记录。
     *
     * @param request 保存请求，敏感元素必须由调用方传入脱敏值或哈希值
     */
    @Transactional(rollbackFor = Exception.class)
    public void createTradeBlack(RiskDTOs.TradeBlackSaveRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantId", trim(request.getMerchantId()));
        data.put("merchantName", defaultIfBlank(request.getMerchantName(), riskManagementMapper.selectMerchantName(trim(request.getMerchantId()))));
        data.put("merchantOrderNo", trim(request.getMerchantOrderNo()));
        data.put("paymentOrderNo", trim(request.getPaymentOrderNo()));
        data.put("blackTargetType", trim(request.getBlackTargetType()));
        data.put("blackTargetValueMasked", trim(request.getBlackTargetValueMasked()));
        data.put("blackTargetHash", trim(request.getBlackTargetHash()));
        data.put("sourceType", SOURCE_MANUAL);
        data.put("actionType", defaultIfBlank(request.getActionType(), TRADE_BLACK_ACTION_ADD));
        data.put("actionReason", trim(request.getActionReason()));
        data.put("status", defaultStatus(request.getStatus()));
        riskManagementMapper.insertTradeBlack(data, currentOperatorName());
        riskManagementMapper.insertChangeLog(MODULE_TRADE_BLACK, TRADE_BLACK_FUNCTION_SYSTEM, null, CHANGE_OPERATION_CREATE, null, JsonUtils.toJsonString(data), currentOperatorName(), TRADE_BLACK_DISPLAY_NAME);
    }

    /**
     * 解除系统交易加黑记录。
     *
     * @param id     系统交易加黑记录ID
     * @param reason 解除原因，允许为空
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseTradeBlack(Long id, String reason) {
        int rows = riskManagementMapper.releaseTradeBlack(id, reason, currentOperatorName());
        if (rows != 1) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "系统交易加黑记录不存在");
        }
        riskManagementMapper.insertChangeLog(MODULE_TRADE_BLACK, TRADE_BLACK_FUNCTION_SYSTEM, id, CHANGE_OPERATION_RELEASE, null, JsonUtils.toJsonString(Map.of("reason", defaultIfBlank(reason, ""))), currentOperatorName(), TRADE_BLACK_RELEASE_DISPLAY_NAME);
    }

    /**
     * 导出名单或规则 Excel，最多导出前 5000 条配置记录。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param response     HTTP 响应，方法内部写入统一样式 Excel
     */
    public void export(String moduleType, String functionCode, HttpServletResponse response) {
        export(moduleType, functionCode, null, response);
    }

    /**
     * 按当前查询条件导出名单或规则 Excel，最多导出前 5000 条配置记录。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param request      导出筛选条件，允许为空
     * @param response     HTTP 响应，方法内部写入统一样式 Excel
     */
    public void export(String moduleType, String functionCode, RiskDTOs.RiskListQueryRequest request, HttpServletResponse response) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "export");
        if (definition.isRuleFunction()) {
            exportRule(functionCode, null, response);
            return;
        }
        RiskDTOs.RiskListQueryRequest query = request == null ? new RiskDTOs.RiskListQueryRequest() : request;
        applyAmlGlobalScope(definition, query);
        List<Map<String, Object>> rows;
        if (definition.isRegionFunction()) {
            String countryAlpha3 = countryAlpha3FromAlpha2(query.getCountryAlpha2());
            rows = riskManagementMapper.selectRegionPage(query.getMerchantScope(), query.getMerchantId(), query.getMatchValue(), countryAlpha3, query.getStatus(), 0, 5000);
        } else {
            String cardBinLookupNumber = cardBinLookupNumber(definition, query.getMatchValue());
            String countryAlpha3 = hasCountryFields(definition) ? countryAlpha3FromAlpha2(query.getCountryAlpha2()) : query.getCountryAlpha2();
            rows = riskManagementMapper.selectListPage(definition.getTableName(), query.getMerchantScope(), query.getMerchantId(), query.getMatchValue(), cardBinLookupNumber, countryAlpha3, query.getStatus(), 0, 5000, hasCountryFields(definition));
        }
        writeExcel(definition, exportRows(definition, rows), buildListQuerySummary(definition, query), response);
    }

    /**
     * 按当前查询条件导出内风控规则 Excel，最多导出前 5000 条配置记录。
     *
     * @param functionCode 规则功能编码，用于解析物理表白名单和功能级权限
     * @param request      导出筛选条件，允许为空
     * @param response     HTTP 响应，方法内部写入统一样式 Excel
     */
    public void exportRule(String functionCode, RiskDTOs.RiskRuleQueryRequest request, HttpServletResponse response) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(MODULE_RULE, functionCode);
        ensureFunctionPermission(definition, "export");
        RiskDTOs.RiskRuleQueryRequest query = request == null ? new RiskDTOs.RiskRuleQueryRequest() : request;
        List<Map<String, Object>> rows;
        if (isSourceUrlRule(definition)) {
            String sourceHost = normalizeSourceHostQuery(defaultIfBlank(query.getSourceHost(), query.getMatchValue()));
            rows = riskManagementMapper.selectSourceUrlRulePage(query.getMerchantId(), trim(query.getSourceUrl()), sourceHost, query.getStatus(), 0, 5000);
        } else if (isMerchantLimitRule(definition)) {
            String matchValue = normalizeRuleQueryMatchValue(definition, query.getMatchValue());
            rows = riskManagementMapper.selectMerchantLimitRulePage(query.getMerchantScope(), query.getMerchantId(), query.getRuleName(), matchValue, query.getLimitType(), query.getStatus(), 0, 5000);
        } else if (isThreeDsRule(definition)) {
            rows = riskManagementMapper.selectThreeDsRulePage(
                    query.getMerchantScope(),
                    query.getMerchantId(),
                    query.getRuleName(),
                    upper(query.getRuleType()),
                    upper(query.getChannelCode()),
                    upper(query.getPaymentMethod()),
                    upper(query.getCardBrand()),
                    upper(query.getCurrency()),
                    upper(query.getTriggerAction()),
                    query.getStatus(),
                    0,
                    5000
            );
        } else {
            String matchValue = normalizeRuleQueryMatchValue(definition, query.getMatchValue());
            rows = riskManagementMapper.selectRulePage(
                    definition.getTableName(),
                    query.getMerchantScope(),
                    query.getMerchantId(),
                    query.getRuleName(),
                    matchValue,
                    query.getLimitType(),
                    query.getCurrency(),
                    query.getStatus(),
                    0,
                    5000
            );
        }
        writeExcel(definition, exportRows(definition, rows), buildRuleQuerySummary(definition, query), response);
    }

    /**
     * 下载导入模板 Excel。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param response     HTTP 响应，方法内部写入统一样式 Excel
     */
    public void template(String moduleType, String functionCode, HttpServletResponse response) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "template");
        List<Map<String, Object>> rows = List.of(templateRow(definition));
        writeTemplateExcel(definition, rows, response);
    }

    /**
     * 批量导入名单或规则 CSV/Excel。导入过程使用同一事务，任一数据行失败则整体回滚，批次与错误明细使用独立事务留痕。
     *
     * @param moduleType   模块类型，只允许 RiskFunctionDefinition 中声明的固定值
     * @param functionCode 功能编码，用于解析物理表白名单和功能级权限
     * @param file         CSV 或 Excel 文件
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public RiskDTOs.ImportResultResponse importCsv(String moduleType, String functionCode, MultipartFile file) {
        RiskFunctionDefinition definition = RiskFunctionDefinition.require(moduleType, functionCode);
        ensureFunctionPermission(definition, "import");
        if (file == null || file.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择需要导入的文件");
        }
        List<ImportRow> rows = readImportRows(file);
        String batchNo = importBatchNo(definition);
        String operator = currentOperatorName();
        importLogService.createBatch(definition.getModuleType(), definition.getFunctionCode(), batchNo, file.getOriginalFilename(), rows.size(), operator);
        List<AdminRiskImportLogService.ImportRowError> rowErrors = new ArrayList<>();
        int successCount = 0;
        for (ImportRow importRow : rows) {
            Map<String, String> row = importRow.values();
            try {
                if (definition.isRegionFunction()) {
                    Map<String, Object> data = regionData(toRegionRequest(row), SOURCE_IMPORT);
                    ensureRegionNotDuplicated(null, data);
                    riskManagementMapper.insertRegion(data, operator);
                } else if (isSourceUrlRule(definition)) {
                    Map<String, Object> data = sourceUrlData(toRuleRequest(row));
                    ensureSourceUrlNotDuplicated(null, data);
                    riskManagementMapper.insertSourceUrlRule(data, operator);
                } else if (definition.isRuleFunction()) {
                    Map<String, Object> data = ruleData(definition, toRuleRequest(row));
                    validateMerchantLimitAmountRelations(definition, data, null);
                    ensureRuleNotDuplicated(definition, null, data);
                    if (isMerchantLimitRule(definition)) {
                        riskManagementMapper.insertMerchantLimitRule(data, operator);
                    } else if (isThreeDsRule(definition)) {
                        riskManagementMapper.insertThreeDsRule(data, operator);
                    } else {
                        riskManagementMapper.insertRule(definition.getTableName(), data, operator);
                    }
                } else {
                    Map<String, Object> data = listData(definition, toListRequest(row), SOURCE_IMPORT);
                    ensureListNotDuplicated(definition, null, data);
                    riskManagementMapper.insertListRecord(definition.getTableName(), data, operator,
                            hasRangeFields(definition), hasCardBrandField(definition), hasCountryFields(definition),
                            hasCountryNumericField(definition), hasIpVersionField(definition), hasSourceHostField(definition));
                }
                successCount++;
            } catch (RuntimeException exception) {
                rowErrors.add(new AdminRiskImportLogService.ImportRowError(importRow.rowNo(), sanitizeImportRow(row), sanitizeImportErrorMessage(exception)));
            }
        }
        if (!rowErrors.isEmpty()) {
            importLogService.markFailed(batchNo, rowErrors.size(), rowErrors);
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), importErrorSummary(batchNo, rowErrors));
        }
        markImportBatchSuccessAfterCommit(batchNo, successCount);
        riskManagementMapper.insertChangeLog(definition.getModuleType(), definition.getFunctionCode(), null, CHANGE_OPERATION_IMPORT, null, JsonUtils.toJsonString(Map.of("successCount", successCount, "batchNo", batchNo)), operator, definition.getFunctionName());
        RiskDTOs.ImportResultResponse response = new RiskDTOs.ImportResultResponse();
        response.setSuccessCount(successCount);
        response.setFailureCount(0);
        response.setErrors(List.of());
        return response;
    }

    /**
     * 将后端功能白名单转换为前端可用的功能定义响应。
     *
     * @param definition 风控功能白名单定义
     * @return 前端路由、权限和功能类型信息
     */
    private RiskDTOs.FunctionDefinitionResponse toDefinitionResponse(RiskFunctionDefinition definition) {
        RiskDTOs.FunctionDefinitionResponse response = new RiskDTOs.FunctionDefinitionResponse();
        response.setModuleType(definition.getModuleType());
        response.setFunctionCode(definition.getFunctionCode());
        response.setFunctionName(definition.getFunctionName());
        response.setRoutePath(definition.getRoutePath());
        response.setPermissionPrefix(definition.getPermissionPrefix());
        response.setRegionFunction(definition.isRegionFunction());
        response.setRuleFunction(definition.isRuleFunction());
        return response;
    }

    /**
     * 完成 list Data 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param sourceType source Type 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private Map<String, Object> listData(RiskFunctionDefinition definition, RiskDTOs.RiskListSaveRequest request, String sourceType) {
        if (isCountryListFunction(definition)) {
            normalizeCountryListRequest(request);
        }
        RiskListValueNormalizer.NormalizedValue normalizedValue = riskListValueNormalizer.normalize(definition, request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantScope", defaultIfBlank(request.getMerchantScope(), DEFAULT_SCOPE));
        data.put("merchantId", trim(request.getMerchantId()));
        if (isMerchantWhitelist(definition)) {
            data.put("merchantScope", "MERCHANT");
            data.put("merchantId", normalizedValue.matchValueMasked());
        }
        applyAmlGlobalScope(definition, data);
        data.put("matchValueMasked", normalizedValue.matchValueMasked());
        data.put("matchValueHash", normalizedValue.matchValueHash());
        data.put("matchValueCipher", normalizedValue.matchValueCipher());
        data.put("matchValueStart", normalizedValue.matchValueStart());
        data.put("matchValueEnd", normalizedValue.matchValueEnd());
        data.put("matchValueStartNumber", normalizedValue.matchValueStartNumber());
        data.put("matchValueEndNumber", normalizedValue.matchValueEndNumber());
        if (isAmlSourceUrlFunction(definition)) {
            data.put("sourceHost", parseSourceUrl(normalizedValue.matchValueMasked()).sourceHost());
        }
        data.put("ipVersion", normalizedValue.ipVersion());
        data.put("cardBrand", resolveCardBrand(definition, request));
        data.put("countryAlpha2", upper(request.getCountryAlpha2()));
        data.put("countryAlpha3", upper(request.getCountryAlpha3()));
        data.put("countryNumeric", trim(request.getCountryNumeric()));
        data.put("riskLevel", defaultIfBlank(request.getRiskLevel(), defaultRiskLevel(definition)));
        data.put("decisionAction", defaultIfBlank(request.getDecisionAction(), defaultDecisionAction(definition)));
        data.put("effectiveTime", defaultEffectiveTime(request.getEffectiveTime()));
        applyValidity(data, request);
        data.put("sourceType", sourceType);
        data.put("status", defaultStatus(request.getStatus()));
        data.put("remark", trim(request.getRemark()));
        normalizeScope(data);
        applyAmlGlobalScope(definition, data);
        if (data.get("merchantId") == null) {
            data.put("merchantId", "");
        }
        return data;
    }

    /**
     * 完成 expand Country List Requests 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    private List<RiskDTOs.RiskListSaveRequest> expandCountryListRequests(RiskFunctionDefinition definition, RiskDTOs.RiskListSaveRequest request) {
        if (!isCountryListFunction(definition) || request.getCountryAlpha2List() == null || request.getCountryAlpha2List().isEmpty()) {
            return List.of(request);
        }
        return request.getCountryAlpha2List().stream()
                .map(this::upper)
                .filter(StringUtils::hasText)
                .distinct()
                .map(countryAlpha2 -> copyCountryListRequest(request, countryAlpha2))
                .toList();
    }

    /**
     * 完成 copy Country List Request 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @param countryAlpha2 country Alpha2 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private RiskDTOs.RiskListSaveRequest copyCountryListRequest(RiskDTOs.RiskListSaveRequest source, String countryAlpha2) {
        RiskDTOs.RiskListSaveRequest target = new RiskDTOs.RiskListSaveRequest();
        target.setMerchantScope(source.getMerchantScope());
        target.setMerchantId(source.getMerchantId());
        target.setRuleName(source.getRuleName());
        target.setMatchValuePlain(countryAlpha2);
        target.setMatchValueMasked(source.getMatchValueMasked());
        target.setMatchValueHash(source.getMatchValueHash());
        target.setMatchValueStart(source.getMatchValueStart());
        target.setMatchValueEnd(source.getMatchValueEnd());
        target.setIpVersion(source.getIpVersion());
        target.setCardBrand(source.getCardBrand());
        target.setCountryAlpha2(countryAlpha2);
        target.setCountryAlpha3(source.getCountryAlpha3());
        target.setCountryNumeric(source.getCountryNumeric());
        target.setRiskLevel(source.getRiskLevel());
        target.setDecisionAction(source.getDecisionAction());
        target.setEffectiveTime(source.getEffectiveTime());
        target.setValidityType(source.getValidityType());
        target.setValidityDays(source.getValidityDays());
        target.setSourceType(source.getSourceType());
        target.setStatus(source.getStatus());
        target.setRemark(source.getRemark());
        return target;
    }

    /**
     * 标准化 normalize Country List Request 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     */
    private void normalizeCountryListRequest(RiskDTOs.RiskListSaveRequest request) {
        String countryAlpha2 = upper(defaultIfBlank(request.getCountryAlpha2(), request.getMatchValuePlain()));
        if (!StringUtils.hasText(countryAlpha2)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择国家/地区");
        }
        Map<String, Object> country = riskManagementMapper.selectCountryOptionByAlpha2(countryAlpha2);
        if (country == null || country.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "国家/地区不存在或已停用");
        }
        String countryAlpha3 = upper(asString(country.get("extra")));
        request.setCountryAlpha2(countryAlpha2);
        request.setCountryAlpha3(countryAlpha3);
        request.setCountryNumeric(asString(country.get("numericCode")));
        request.setMatchValuePlain(countryAlpha3);
    }

    /**
     * 完成 region Data 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @param sourceType source Type 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private Map<String, Object> regionData(RiskDTOs.RegionSaveRequest request, String sourceType) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantScope", defaultIfBlank(request.getMerchantScope(), DEFAULT_SCOPE));
        data.put("merchantId", trim(request.getMerchantId()));
        data.put("regionMatchLevel", defaultIfBlank(request.getRegionMatchLevel(), "COUNTRY").toUpperCase(Locale.ROOT));
        data.put("countryAlpha2", upper(request.getCountryAlpha2()));
        applyCountryMetadata(data);
        data.put("stateProvinceName", trim(request.getStateProvinceName()));
        data.put("cityName", trim(request.getCityName()));
        normalizeRegionLevelFields(data);
        data.put("riskLevel", defaultIfBlank(request.getRiskLevel(), "HIGH"));
        data.put("decisionAction", defaultIfBlank(request.getDecisionAction(), "REJECT"));
        data.put("effectiveTime", defaultEffectiveTime(request.getEffectiveTime()));
        applyRegionValidity(data, request);
        data.put("sourceType", sourceType);
        data.put("status", defaultStatus(request.getStatus()));
        data.put("remark", trim(request.getRemark()));
        normalizeScope(data);
        if (data.get("merchantId") == null) {
            data.put("merchantId", "");
        }
        return data;
    }

    /**
     * 完成 region Create Country Codes 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    private List<String> regionCreateCountryCodes(RiskDTOs.RegionSaveRequest request) {
        String regionMatchLevel = defaultIfBlank(request.getRegionMatchLevel(), "COUNTRY").toUpperCase(Locale.ROOT);
        List<String> sourceCodes = "COUNTRY".equals(regionMatchLevel) && request.getCountryAlpha2List() != null && !request.getCountryAlpha2List().isEmpty()
                ? request.getCountryAlpha2List()
                : List.of(defaultIfBlank(request.getCountryAlpha2(), ""));
        List<String> countryCodes = sourceCodes.stream()
                .map(this::upper)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (countryCodes.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择国家/地区");
        }
        return countryCodes;
    }

    /**
     * 组装内风控规则保存字段。该方法会先统一收敛页面、导入传入的字段，再按功能裁剪无关字段并校验执行语义。
     *
     * @param definition 风控规则功能定义
     * @param request    管理端保存请求
     * @return 可直接写入规则表的字段映射
     */
    private Map<String, Object> ruleData(RiskFunctionDefinition definition, RiskDTOs.RiskRuleSaveRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantScope", defaultIfBlank(request.getMerchantScope(), DEFAULT_SCOPE));
        data.put("merchantId", trim(request.getMerchantId()));
        data.put("ruleName", trim(request.getRuleName()));
        data.put("matchMode", csvCode("matchMode", defaultIfBlank(request.getMatchMode(), defaultRuleMatchMode(definition))));
        data.put("matchValue", normalizeRuleMatchValue(definition, request.getMatchValue()));
        data.put("limitType", csvCode("limitType", request.getLimitType()));
        data.put("amountMin", request.getAmountMin());
        data.put("amountMax", request.getAmountMax());
        data.put("currency", FUNCTION_MERCHANT_LIMIT.equals(definition.getFunctionCode()) ? FIXED_LIMIT_CURRENCY_USD : upper(request.getCurrency()));
        data.put("timeWindowSeconds", request.getTimeWindowSeconds());
        data.put("thresholdCount", request.getThresholdCount());
        data.put("elementsJson", defaultIfBlank(request.getElementsJson(), "{}"));
        data.put("riskLevel", defaultIfBlank(request.getRiskLevel(), defaultRiskLevel(definition)));
        data.put("decisionAction", defaultIfBlank(request.getDecisionAction(), defaultDecisionAction(definition)));
        if (isThreeDsRule(definition)) {
            data.put("ruleGroupNo", defaultIfBlank(request.getRuleGroupNo(), UUID.randomUUID().toString()));
            data.put("merchantName", trim(request.getMerchantName()));
            data.put("ruleType", csvCode("threeDsRuleType", defaultIfBlank(request.getRuleType(), THREE_DS_RULE_TYPE_RISK)));
            data.put("channelCode", upper(defaultIfBlank(request.getChannelCode(), THREE_DS_ALL_DIMENSION)));
            data.put("paymentMethod", upper(defaultIfBlank(request.getPaymentMethod(), THREE_DS_DEFAULT_PAYMENT_METHOD)));
            data.put("cardBrand", upper(defaultIfBlank(request.getCardBrand(), THREE_DS_ALL_DIMENSION)));
            data.put("amountMatchType", csvCode("threeDsAmountMatchType", defaultIfBlank(request.getAmountMatchType(), THREE_DS_AMOUNT_ALL)));
            data.put("riskCondition", csvCode("threeDsRiskCondition", defaultIfBlank(request.getRiskCondition(), THREE_DS_RISK_ANY)));
            data.put("triggerAction", csvCode("threeDsTriggerAction", defaultIfBlank(request.getTriggerAction(), THREE_DS_ACTION_FORCE)));
            data.put("priority", request.getPriority() == null ? THREE_DS_DEFAULT_PRIORITY : request.getPriority());
        }
        data.put("effectiveTime", request.getEffectiveTime());
        data.put("expireTime", request.getExpireTime());
        data.put("status", defaultStatus(request.getStatus()));
        data.put("remark", trim(request.getRemark()));
        normalizeScope(data);
        fillThreeDsMerchantName(definition, data);
        pruneRuleFields(definition, data);
        validateRuleData(definition, data);
        return data;
    }

    /**
     * 将单条来源网址保存请求转换为批量请求，兼容原有内风控规则新增入口。
     *
     * @param request 规则保存请求
     * @return 来源网址批量保存请求
     */
    private RiskDTOs.RiskSourceUrlBatchSaveRequest toSourceUrlBatchRequest(RiskDTOs.RiskRuleSaveRequest request) {
        RiskDTOs.RiskSourceUrlBatchSaveRequest batchRequest = new RiskDTOs.RiskSourceUrlBatchSaveRequest();
        batchRequest.setMerchantId(request.getMerchantId());
        String sourceUrl = defaultIfBlank(request.getSourceUrl(), request.getMatchValue());
        batchRequest.setSourceUrls(request.getSourceUrls() == null || request.getSourceUrls().isEmpty()
                ? List.of(defaultIfBlank(sourceUrl, ""))
                : request.getSourceUrls());
        batchRequest.setRiskLevel(request.getRiskLevel());
        batchRequest.setDecisionAction(request.getDecisionAction());
        batchRequest.setEffectiveTime(request.getEffectiveTime());
        batchRequest.setExpireTime(request.getExpireTime());
        batchRequest.setStatus(request.getStatus());
        batchRequest.setRemark(request.getRemark());
        return batchRequest;
    }

    /**
     * 组装来源网址单条保存字段。来源网址只按商户号和 host 生效，不使用规则名称、生效范围或匹配方式。
     *
     * @param request 规则保存请求
     * @return 来源网址字段映射
     */
    private Map<String, Object> sourceUrlData(RiskDTOs.RiskRuleSaveRequest request) {
        String sourceUrl = defaultIfBlank(request.getSourceUrl(), request.getMatchValue());
        return sourceUrlData(request.getMerchantId(), sourceUrl, request.getRiskLevel(), request.getDecisionAction(),
                request.getEffectiveTime(), request.getExpireTime(), request.getStatus(), request.getRemark());
    }

    /**
     * 组装来源网址批量保存字段，并在同批次内按 host 去重。
     *
     * @param request 来源网址批量保存请求
     * @return 来源网址字段映射列表
     */
    private List<Map<String, Object>> sourceUrlBatchData(RiskDTOs.RiskSourceUrlBatchSaveRequest request) {
        String merchantId = trim(request.getMerchantId());
        if (!StringUtils.hasText(merchantId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请输入商户号");
        }
        List<String> sourceUrls = request.getSourceUrls() == null ? List.of() : request.getSourceUrls();
        if (sourceUrls.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请输入允许来源网址");
        }
        Set<String> hosts = new HashSet<>();
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (String sourceUrl : sourceUrls) {
            Map<String, Object> data = sourceUrlData(merchantId, sourceUrl, request.getRiskLevel(), request.getDecisionAction(),
                    request.getEffectiveTime(), request.getExpireTime(), request.getStatus(), request.getRemark());
            if (!hosts.add((String) data.get("sourceHost"))) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "来源网址已存在");
            }
            dataList.add(data);
        }
        return dataList;
    }

    /**
     * 组装来源网址入库字段，保存商户原始 URL 和统一小写的 host。
     *
     * @param merchantId      商户号
     * @param sourceUrl       商户录入来源网址
     * @param riskLevel       风险等级
     * @param decisionAction  决策动作
     * @param effectiveTime   生效时间
     * @param expireTime      失效时间
     * @param status          状态
     * @param remark          备注
     * @return 来源网址字段映射
     */
    private Map<String, Object> sourceUrlData(String merchantId,
                                              String sourceUrl,
                                              String riskLevel,
                                              String decisionAction,
                                              LocalDateTime effectiveTime,
                                              LocalDateTime expireTime,
                                              Integer status,
                                              String remark) {
        String normalizedMerchantId = trim(merchantId);
        if (!StringUtils.hasText(normalizedMerchantId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请输入商户号");
        }
        SourceUrlParts parts = parseSourceUrl(sourceUrl);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("merchantId", normalizedMerchantId);
        data.put("sourceUrl", parts.sourceUrl());
        data.put("sourceHost", parts.sourceHost());
        data.put("riskLevel", defaultIfBlank(riskLevel, defaultRiskLevel(RiskFunctionDefinition.RULE_SOURCE_URL)));
        data.put("decisionAction", defaultIfBlank(decisionAction, defaultDecisionAction(RiskFunctionDefinition.RULE_SOURCE_URL)));
        data.put("effectiveTime", effectiveTime);
        data.put("expireTime", expireTime);
        data.put("status", defaultStatus(status));
        data.put("remark", trim(remark));
        return data;
    }

    /**
     * 解析并校验来源网址，确保交易侧使用稳定 host 匹配。
     *
     * @param sourceUrl 商户录入来源网址
     * @return 原始 URL 和 host
     */
    private SourceUrlParts parseSourceUrl(String sourceUrl) {
        String text = trim(sourceUrl);
        if (!StringUtils.hasText(text)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请输入允许来源网址");
        }
        String lowerText = text.toLowerCase(Locale.ROOT);
        if (!lowerText.startsWith("http://") && !lowerText.startsWith("https://")) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "允许来源网址必须以 http:// 或 https:// 开头");
        }
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!List.of("http", "https").contains(defaultIfBlank(scheme, "").toLowerCase(Locale.ROOT))
                    || !StringUtils.hasText(host)) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "来源网址格式不正确");
            }
            return new SourceUrlParts(text, host.toLowerCase(Locale.ROOT));
        } catch (URISyntaxException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "来源网址格式不正确");
        }
    }

    /**
     * 归一化来源网址查询 host。查询允许输入完整 URL 或 host，非法 URL 保持原值模糊查询。
     *
     * @param value 查询值
     * @return host 或原查询值
     */
    private String normalizeSourceHostQuery(String value) {
        String text = trim(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (text.toLowerCase(Locale.ROOT).startsWith("http://") || text.toLowerCase(Locale.ROOT).startsWith("https://")) {
            try {
                return parseSourceUrl(text).sourceHost();
            } catch (RuntimeException ignored) {
                return text;
            }
        }
        return text.toLowerCase(Locale.ROOT);
    }

    /**
     * 按规则功能裁剪无关字段。管理端当前共用一套规则表结构，但每类规则只允许保存自己可解释的字段，
     * 避免后续实时风控服务接入时出现“限额规则带匹配值”“频率规则带金额”等歧义配置。
     *
     * @param definition 风控规则功能定义
     * @param data       待保存的规则字段映射
     */
    private void pruneRuleFields(RiskFunctionDefinition definition, Map<String, Object> data) {
        switch (definition.getFunctionCode()) {
            case "sourceUrl" -> clearRuleAmountAndFrequency(data);
            case FUNCTION_THREE_DS -> {
                data.put("matchMode", null);
                data.put("matchValue", null);
                data.put("limitType", null);
                clearRuleFrequency(data);
            }
            case FUNCTION_MERCHANT_LIMIT -> {
                data.put("matchMode", null);
                data.put("matchValue", defaultIfBlank(asString(data.get("matchValue")), ""));
                clearRuleFrequency(data);
            }
            case "frequency" -> {
                data.put("matchMode", null);
                data.put("matchValue", null);
                clearRuleAmount(data);
            }
            default -> clearRuleAmountAndFrequency(data);
        }
    }

    /**
     * 3DS 商户规则保存商户名称快照；全局规则清空商户维度，避免交易侧匹配口径歧义。
     *
     * @param definition 风控规则功能定义
     * @param data       待保存的规则字段映射
     */
    private void fillThreeDsMerchantName(RiskFunctionDefinition definition, Map<String, Object> data) {
        if (!isThreeDsRule(definition)) {
            return;
        }
        if (DEFAULT_SCOPE.equals(data.get("merchantScope"))) {
            data.put("merchantId", "");
            data.put("merchantName", null);
            return;
        }
        String merchantId = asString(data.get("merchantId"));
        if (!StringUtils.hasText(merchantId)) {
            return;
        }
        data.put("merchantName", defaultIfBlank(asString(data.get("merchantName")), riskManagementMapper.selectMerchantName(merchantId)));
    }

    /**
     * 清理规则金额字段和币种字段。
     *
     * @param data 待保存的规则字段映射
     */
    private void clearRuleAmount(Map<String, Object> data) {
        data.put("limitType", null);
        data.put("amountMin", null);
        data.put("amountMax", null);
        data.put("currency", null);
    }

    /**
     * 清理规则频率字段。
     *
     * @param data 待保存的规则字段映射
     */
    private void clearRuleFrequency(Map<String, Object> data) {
        data.put("timeWindowSeconds", null);
        data.put("thresholdCount", null);
        data.put("elementsJson", "{}");
    }

    /**
     * 同时清理金额和频率字段，适用于来源网址、国家、BIN、3DS 等匹配类规则。
     *
     * @param data 待保存的规则字段映射
     */
    private void clearRuleAmountAndFrequency(Map<String, Object> data) {
        clearRuleAmount(data);
        clearRuleFrequency(data);
    }

    /**
     * 查询未删除配置记录。表名由功能白名单传入，禁止外部请求直接控制。
     *
     * @param tableName 物理表名，必须来自 RiskFunctionDefinition
     * @param id        配置记录ID
     * @return 未删除记录
     */
    private Map<String, Object> requireRecord(String tableName, Long id) {
        if (id == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "记录ID不能为空");
        }
        Map<String, Object> record = riskManagementMapper.selectById(tableName, id);
        if (record == null || record.isEmpty()) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "风控配置记录不存在");
        }
        return record;
    }

    /**
     * 根据刚保存的数据反查最新记录，保证返回给页面的数据包含数据库默认时间和脱敏展示字段。
     *
     * @param definition 风控功能定义
     * @param data       已保存字段
     * @return 页面展示记录
     */
    private RiskDTOs.RiskRecordResponse latestListRecord(RiskFunctionDefinition definition, Map<String, Object> data) {
        List<Map<String, Object>> rows;
        if (isSourceUrlRule(definition)) {
            rows = riskManagementMapper.selectSourceUrlRulePage((String) data.get("merchantId"), null, (String) data.get("sourceHost"), (Integer) data.get("status"), 0, 1);
        } else if (isMerchantLimitRule(definition)) {
            rows = riskManagementMapper.selectMerchantLimitRulePage((String) data.get("merchantScope"), (String) data.get("merchantId"), (String) data.get("ruleName"), (String) data.get("matchValue"), (String) data.get("limitType"), (Integer) data.get("status"), 0, 1);
        } else if (isThreeDsRule(definition)) {
            rows = riskManagementMapper.selectThreeDsRulePage(
                    (String) data.get("merchantScope"),
                    (String) data.get("merchantId"),
                    (String) data.get("ruleName"),
                    (String) data.get("ruleType"),
                    (String) data.get("channelCode"),
                    (String) data.get("paymentMethod"),
                    (String) data.get("cardBrand"),
                    (String) data.get("currency"),
                    (String) data.get("triggerAction"),
                    (Integer) data.get("status"),
                    0,
                    1
            );
        } else if (definition.isRuleFunction()) {
            rows = riskManagementMapper.selectRulePage(definition.getTableName(), (String) data.get("merchantScope"), (String) data.get("merchantId"), (String) data.get("ruleName"), (String) data.get("matchValue"), (String) data.get("limitType"), (String) data.get("currency"), (Integer) data.get("status"), 0, 1);
        } else if (definition.isRegionFunction()) {
            rows = riskManagementMapper.selectRegionPage((String) data.get("merchantScope"), (String) data.get("merchantId"), null, (String) data.get("countryAlpha3"), (Integer) data.get("status"), 0, 1);
        } else {
            rows = riskManagementMapper.selectListPage(definition.getTableName(), (String) data.get("merchantScope"), (String) data.get("merchantId"), (String) data.get("matchValueMasked"), null, (String) data.get("countryAlpha3"), (Integer) data.get("status"), 0, 1, hasCountryFields(definition));
        }
        return rows.isEmpty() ? new RiskDTOs.RiskRecordResponse() : toRecordResponse(rows.get(0));
    }

    /**
     * 根据来源网址保存字段查询最新记录。
     *
     * @param data 已保存字段
     * @return 页面展示记录
     */
    private RiskDTOs.RiskRecordResponse latestSourceUrlRecord(Map<String, Object> data) {
        return latestListRecord(RiskFunctionDefinition.RULE_SOURCE_URL, data);
    }

    /**
     * 写入配置变更日志。快照会移除哈希、密文、区间数值等不适合页面展示或审计泄露的字段。
     *
     * @param definition    风控功能定义
     * @param businessId    业务记录ID，导入类批量操作可为空
     * @param operationType 操作类型
     * @param before        变更前快照
     * @param after         变更后快照
     */
    private void writeChange(RiskFunctionDefinition definition, Long businessId, String operationType, Map<String, Object> before, Map<String, Object> after) {
        riskManagementMapper.insertChangeLog(
                definition.getModuleType(),
                definition.getFunctionCode(),
                businessId,
                operationType,
                before == null ? null : JsonUtils.toJsonString(sanitizeSnapshot(before)),
                after == null ? null : JsonUtils.toJsonString(sanitizeSnapshot(after)),
                currentOperatorName(),
                definition.getFunctionName()
        );
    }

    /**
     * 为配置变更日志补充页面展示文案，避免前端直接解释后端内部模块码和操作码。
     *
     * @param rows Mapper 查询出的变更日志原始行
     * @return 已补充模块、功能、操作和业务标签的日志行
     */
    private List<Map<String, Object>> enrichChangeLogRows(List<Map<String, Object>> rows) {
        return rows.stream().map(this::enrichChangeLogRow).toList();
    }

    /**
     * 补充单条配置变更日志的可读字段，保留原始编码字段供排查和兼容旧页面使用。
     *
     * @param row Mapper 查询出的变更日志原始行
     * @return 已补充展示字段的日志行
     */
    private Map<String, Object> enrichChangeLogRow(Map<String, Object> row) {
        Map<String, Object> copy = new LinkedHashMap<>(row);
        String moduleType = asString(row.get("module_type"));
        String functionCode = asString(row.get("function_code"));
        String operationType = asString(row.get("operation_type"));
        copy.put("moduleName", moduleDisplayName(moduleType));
        copy.put("functionName", functionDisplayName(moduleType, functionCode));
        copy.put("operationName", operationDisplayName(operationType));
        copy.put("businessLabel", changeBusinessLabel(row));
        return copy;
    }

    /**
     * 将风控模块码转换为管理端展示名称。未知模块保留原始值，便于兼容历史数据和排查异常日志。
     *
     * @param moduleType 风控模块码
     * @return 管理端展示名称
     */
    private String moduleDisplayName(String moduleType) {
        if (!StringUtils.hasText(moduleType)) {
            return EMPTY_DISPLAY_TEXT;
        }
        return MODULE_DISPLAY_NAMES.getOrDefault(moduleType.toUpperCase(Locale.ROOT), moduleType);
    }

    /**
     * 将风控功能码转换为管理端展示名称。功能定义表未覆盖的历史记录回退展示原始功能码。
     *
     * @param moduleType   风控模块码
     * @param functionCode 风控功能码
     * @return 管理端展示名称
     */
    private String functionDisplayName(String moduleType, String functionCode) {
        if (MODULE_TRADE_BLACK.equalsIgnoreCase(moduleType) && TRADE_BLACK_FUNCTION_SYSTEM.equals(functionCode)) {
            return TRADE_BLACK_DISPLAY_NAME;
        }
        for (RiskFunctionDefinition definition : RiskFunctionDefinition.all()) {
            if (definition.getModuleType().equalsIgnoreCase(defaultIfBlank(moduleType, ""))
                    && definition.getFunctionCode().equals(functionCode)) {
                return definition.getFunctionName();
            }
        }
        return StringUtils.hasText(functionCode) ? functionCode : EMPTY_DISPLAY_TEXT;
    }

    /**
     * 将配置变更操作码转换为管理端展示名称。未知操作保留原始值，避免历史日志不可读。
     *
     * @param operationType 配置变更操作码
     * @return 管理端展示名称
     */
    private String operationDisplayName(String operationType) {
        if (!StringUtils.hasText(operationType)) {
            return EMPTY_DISPLAY_TEXT;
        }
        return CHANGE_OPERATION_DISPLAY_NAMES.getOrDefault(operationType.toUpperCase(Locale.ROOT), operationType);
    }

    /**
     * 生成配置变更日志的业务对象标签，优先使用快照中的业务主键或可读名称，缺失时回退为配置 ID。
     *
     * @param row Mapper 查询出的变更日志原始行
     * @return 页面列表展示的业务对象标签
     */
    private String changeBusinessLabel(Map<String, Object> row) {
        Map<String, Object> snapshot = firstReadableSnapshot(row);
        String label = snapshotBusinessLabel(snapshot);
        if (StringUtils.hasText(label)) {
            return label;
        }
        Long businessId = asLong(row.get("business_id"));
        return businessId == null ? SNAPSHOT_DISPLAY_TEXT : CONFIG_ID_DISPLAY_PREFIX + businessId;
    }

    /**
     * 获取配置变更中优先用于展示的快照。变更日志同时保存前后快照时，页面业务对象应优先展示变更后的配置。
     *
     * @param row 配置变更日志行
     * @return 可读快照，解析失败时返回 null
     */
    private Map<String, Object> firstReadableSnapshot(Map<String, Object> row) {
        Map<String, Object> afterSnapshot = parseSnapshot(row.get("after_snapshot"));
        if (afterSnapshot != null && !afterSnapshot.isEmpty()) {
            return afterSnapshot;
        }
        return parseSnapshot(row.get("before_snapshot"));
    }

    /**
     * 解析配置快照 JSON。快照只用于管理端展示，解析失败不应影响变更日志列表查询。
     *
     * @param snapshotText 快照 JSON 字符串
     * @return 快照 Map，空字符串或非法 JSON 返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSnapshot(Object snapshotText) {
        String text = asString(snapshotText);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(text, Map.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 从快照中提取面向运营人员的业务对象名称，避免配置变更页面只显示内部业务ID。
     *
     * @param snapshot 已脱敏快照
     * @return 业务对象标签
     */
    private String snapshotBusinessLabel(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return null;
        }
        String batchNo = firstText(snapshot, "batchNo", "batch_no");
        if (StringUtils.hasText(batchNo)) {
            return "导入批次：" + batchNo;
        }
        String ruleName = firstText(snapshot, "ruleName", "rule_name");
        if (StringUtils.hasText(ruleName)) {
            return "规则：" + ruleName;
        }
        String matchValue = firstText(snapshot, "matchValueMasked", "match_value_masked", "matchValuePlain", "matchValue", "match_value");
        if (StringUtils.hasText(matchValue)) {
            return "名单值：" + matchValue;
        }
        String region = regionSnapshotLabel(snapshot);
        if (StringUtils.hasText(region)) {
            return "区域：" + region;
        }
        String tradeTarget = firstText(snapshot, "blackTargetValueMasked", "black_target_value_masked", "paymentOrderNo", "payment_order_no", "merchantOrderNo", "merchant_order_no");
        if (StringUtils.hasText(tradeTarget)) {
            return "交易加黑：" + tradeTarget;
        }
        String status = firstText(snapshot, "status");
        if (StringUtils.hasText(status)) {
            return "状态：" + status;
        }
        return null;
    }

    /**
     * 拼接高风险区域快照的展示名称，按国家、州省、城市层级展示。
     *
     * @param snapshot 高风险区域快照
     * @return 区域展示名称
     */
    private String regionSnapshotLabel(Map<String, Object> snapshot) {
        List<String> parts = Stream.of(
                        firstText(snapshot, "countryAlpha3", "country_alpha3"),
                        firstText(snapshot, "stateProvinceName", "state_province_name"),
                        firstText(snapshot, "cityName", "city_name")
                )
                .filter(StringUtils::hasText)
                .toList();
        return parts.isEmpty() ? null : String.join(" / ", parts);
    }

    /**
     * 按兼容字段名顺序读取第一个非空文本，兼容驼峰和下划线快照字段。
     *
     * @param source 快照 Map
     * @param keys   候选字段名
     * @return 第一个非空文本
     */
    private String firstText(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String value = asString(source.get(key));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 转换生成 to Record Response 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private RiskDTOs.RiskRecordResponse toRecordResponse(Map<String, Object> row) {
        RiskDTOs.RiskRecordResponse response = new RiskDTOs.RiskRecordResponse();
        response.setId(asLong(row.get("id")));
        response.setMerchantScope(asString(row.get("merchant_scope")));
        response.setMerchantId(asString(row.get("merchant_id")));
        response.setMerchantName(defaultIfBlank(asString(row.get("merchant_name")), merchantName(response.getMerchantId())));
        response.setRuleName(asString(row.get("rule_name")));
        response.setRuleGroupNo(asString(row.get("rule_group_no")));
        response.setMatchValueMasked(asString(row.get("match_value_masked")));
        response.setMatchValueStart(asString(row.get("match_value_start")));
        response.setMatchValueEnd(asString(row.get("match_value_end")));
        response.setIpVersion(asString(row.get("ip_version")));
        response.setCardBrand(asString(row.get("card_brand")));
        response.setRuleType(asString(row.get("rule_type")));
        response.setChannelCode(asString(row.get("channel_code")));
        response.setPaymentMethod(asString(row.get("payment_method")));
        response.setAmountMatchType(asString(row.get("amount_match_type")));
        response.setRiskCondition(asString(row.get("risk_condition")));
        response.setTriggerAction(asString(row.get("trigger_action")));
        response.setPriority(asInteger(row.get("priority")));
        response.setCountryAlpha2(asString(row.get("country_alpha2")));
        response.setCountryAlpha3(asString(row.get("country_alpha3")));
        response.setCountryNumeric(asString(row.get("country_numeric")));
        if (!StringUtils.hasText(response.getCountryAlpha2()) && StringUtils.hasText(response.getCountryAlpha3())) {
            Map<String, Object> country = riskManagementMapper.selectCountryOptionByAlpha3(response.getCountryAlpha3());
            response.setCountryAlpha2(asString(country == null ? null : country.get("value")));
        }
        response.setRiskLevel(asString(row.get("risk_level")));
        response.setDecisionAction(asString(row.get("decision_action")));
        response.setEffectiveTime(asLocalDateTime(row.get("effective_time")));
        response.setExpireTime(asLocalDateTime(row.get("expire_time")));
        response.setValidityType(asString(row.get("validity_type")));
        response.setValidityDays(asInteger(row.get("validity_days")));
        response.setSourceType(asString(row.get("source_type")));
        response.setStatus(asInteger(row.get("status")));
        response.setRemark(asString(row.get("remark")));
        response.setCreateBy(asString(row.get("create_by")));
        response.setUpdateBy(asString(row.get("update_by")));
        response.setCreateTime(asLocalDateTime(row.get("create_time")));
        response.setUpdateTime(asLocalDateTime(row.get("update_time")));
        response.setRegionMatchLevel(asString(row.get("region_match_level")));
        response.setStateProvinceCode(asString(row.get("state_province_code")));
        response.setStateProvinceName(asString(row.get("state_province_name")));
        response.setCityCode(asString(row.get("city_code")));
        response.setCityName(asString(row.get("city_name")));
        response.setMatchMode(asString(row.get("match_mode")));
        response.setMatchValue(asString(row.get("match_value")));
        response.setSourceUrl(asString(row.get("source_url")));
        response.setSourceHost(asString(row.get("source_host")));
        response.setLimitType(asString(row.get("limit_type")));
        response.setAmountMin(asBigDecimal(row.get("amount_min")));
        response.setAmountMax(asBigDecimal(row.get("amount_max")));
        response.setCurrency(asString(row.get("currency")));
        if (row.containsKey("limit_type") && !StringUtils.hasText(response.getCurrency())) {
            response.setCurrency(FIXED_LIMIT_CURRENCY_USD);
        }
        response.setTimeWindowSeconds(asInteger(row.get("time_window_seconds")));
        response.setThresholdCount(asInteger(row.get("threshold_count")));
        response.setElementsJson(asString(row.get("elements_json")));
        return response;
    }

    /**
     * 校验名单唯一性。唯一口径是生效范围、商户号和归一化后的匹配值哈希，已软删除记录不参与判断。
     *
     * @param definition 风控功能定义
     * @param excludeId  编辑时排除的当前记录ID，新增时为空
     * @param data       待保存的名单数据
     */
    private void ensureListNotDuplicated(RiskFunctionDefinition definition, Long excludeId, Map<String, Object> data) {
        if (isAmlSourceUrlFunction(definition)) {
            long duplicateCount = riskManagementMapper.countAmlSourceUrlHostDuplicate((String) data.get("sourceHost"), excludeId);
            if (duplicateCount > 0) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "来源网址已存在");
            }
            return;
        }
        long duplicateCount = riskManagementMapper.countListDuplicate(
                definition.getTableName(),
                (String) data.get("merchantScope"),
                (String) data.get("merchantId"),
                (String) data.get("matchValueHash"),
                excludeId
        );
        if (duplicateCount > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "同一生效范围下已存在相同风控名单记录");
        }
    }

    /**
     * 校验高风险区域唯一性，避免同一生效范围重复录入相同国家、州省或城市粒度配置。
     *
     * @param excludeId 编辑时排除的当前记录ID，新增时为空
     * @param data      待保存的区域数据
     */
    private void ensureRegionNotDuplicated(Long excludeId, Map<String, Object> data) {
        long duplicateCount = riskManagementMapper.countRegionDuplicate(
                (String) data.get("merchantScope"),
                (String) data.get("merchantId"),
                (String) data.get("regionMatchLevel"),
                (String) data.get("countryAlpha3"),
                (String) data.get("stateProvinceName"),
                (String) data.get("cityName"),
                excludeId
        );
        if (duplicateCount > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "同一生效范围下已存在相同高风险区域");
        }
    }

    /**
     * 校验内风控规则唯一性，避免同一生效范围重复录入语义完全一致的规则。
     *
     * @param definition 风控规则功能定义
     * @param excludeId  编辑时排除的当前记录ID，新增时为空
     * @param data       待保存的规则数据
     */
    private void ensureRuleNotDuplicated(RiskFunctionDefinition definition, Long excludeId, Map<String, Object> data) {
        if (isMerchantLimitRule(definition)) {
            long duplicateCount = riskManagementMapper.countMerchantLimitDuplicate(
                    (String) data.get("merchantScope"),
                    (String) data.get("merchantId"),
                    (String) data.get("ruleName"),
                    (String) data.get("matchValue"),
                    (String) data.get("limitType"),
                    (BigDecimal) data.get("amountMin"),
                    (BigDecimal) data.get("amountMax"),
                    excludeId
            );
            if (duplicateCount > 0) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "同一生效范围下已存在相同商户交易限额");
            }
            return;
        }
        if (isThreeDsRule(definition)) {
            long duplicateCount = riskManagementMapper.countThreeDsDuplicate(
                    (String) data.get("merchantScope"),
                    (String) data.get("merchantId"),
                    (String) data.get("channelCode"),
                    (String) data.get("paymentMethod"),
                    (String) data.get("cardBrand"),
                    (String) data.get("amountMatchType"),
                    (BigDecimal) data.get("amountMin"),
                    (BigDecimal) data.get("amountMax"),
                    (String) data.get("currency"),
                    (String) data.get("riskCondition"),
                    (String) data.get("triggerAction"),
                    excludeId
            );
            if (duplicateCount > 0) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "同一商户和交易维度下已存在相同3DS规则");
            }
            return;
        }
        long duplicateCount = riskManagementMapper.countRuleDuplicate(
                definition.getTableName(),
                (String) data.get("merchantScope"),
                (String) data.get("merchantId"),
                (String) data.get("matchMode"),
                (String) data.get("matchValue"),
                (String) data.get("limitType"),
                (BigDecimal) data.get("amountMin"),
                (BigDecimal) data.get("amountMax"),
                (String) data.get("currency"),
                (Integer) data.get("timeWindowSeconds"),
                (Integer) data.get("thresholdCount"),
                (String) data.get("elementsJson"),
                excludeId
        );
        if (duplicateCount > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "同一生效范围下已存在相同风控规则");
        }
    }

    /**
     * 校验商户来源网址唯一性，唯一口径为商户号和来源 host。
     *
     * @param excludeId 编辑时排除的当前记录ID，新增时为空
     * @param data      待保存的来源网址数据
     */
    private void ensureSourceUrlNotDuplicated(Long excludeId, Map<String, Object> data) {
        long duplicateCount = riskManagementMapper.countSourceUrlDuplicate(
                (String) data.get("merchantId"),
                (String) data.get("sourceHost"),
                excludeId
        );
        if (duplicateCount > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "来源网址已存在");
        }
    }

    /**
     * 校验内风控规则的通用字段和功能专属字段。这里仅约束管理端配置语义，实时交易决策仍由后续风控服务执行。
     *
     * @param definition 风控规则功能定义
     * @param data       已归一化的规则数据
     */
    private void validateRuleData(RiskFunctionDefinition definition, Map<String, Object> data) {
        if (!StringUtils.hasText(asString(data.get("ruleName")))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请输入规则名称");
        }
        BigDecimal amountMin = (BigDecimal) data.get("amountMin");
        BigDecimal amountMax = (BigDecimal) data.get("amountMax");
        if (amountMin != null && amountMin.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "最小金额必须大于 0");
        }
        if (amountMax != null && amountMax.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "最大金额必须大于 0");
        }
        if (amountMin != null && amountMax != null && amountMin.compareTo(amountMax) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "最小金额不能大于最大金额");
        }
        Integer timeWindowSeconds = (Integer) data.get("timeWindowSeconds");
        if (timeWindowSeconds != null && timeWindowSeconds <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "时间窗口秒数必须大于 0");
        }
        Integer thresholdCount = (Integer) data.get("thresholdCount");
        if (thresholdCount != null && thresholdCount <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "阈值次数必须大于 0");
        }
        String elementsJson = (String) data.get("elementsJson");
        try {
            Map<String, Object> elements = JsonUtils.parseObject(elementsJson, Map.class);
            validateRuleByFunction(definition, data, elements == null ? Map.of() : elements);
        } catch (RuntimeException exception) {
            if (exception instanceof ServiceException serviceException) {
                throw serviceException;
            }
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "组合元素 JSON 必须是合法对象");
        }
    }

    /**
     * 按规则功能校验配置项，保证管理端录入的数据能被后续交易风控服务直接解释。
     *
     * @param definition 风控规则功能定义
     * @param data       已归一化的规则数据
     * @param elements   频率规则元素 JSON
     */
    private void validateRuleByFunction(RiskFunctionDefinition definition, Map<String, Object> data, Map<String, Object> elements) {
        String code = definition.getFunctionCode();
        String matchMode = asString(data.get("matchMode"));
        if (StringUtils.hasText(matchMode) && !VALID_MATCH_MODES.contains(matchMode)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "匹配方式不正确");
        }
        if ("threeDs".equals(code)) {
            validateThreeDsRule(data);
        }
        if (FUNCTION_MERCHANT_LIMIT.equals(code)) {
            if (!StringUtils.hasText((String) data.get("limitType"))) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择限额类型");
            }
            data.put("currency", FIXED_LIMIT_CURRENCY_USD);
            if (data.get("amountMin") == null && data.get("amountMax") == null) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请至少配置一个限额金额");
            }
            validateMerchantLimitAmountByType(data);
        }
        if ("frequency".equals(code)) {
            validateFrequencyRule(data, elements);
        }
    }

    /**
     * 校验 3DS 结构化策略。该配置面向交易匹配，禁止再用自由文本表达式承载渠道、金额或风险条件。
     *
     * @param data 已归一化的规则数据
     */
    private void validateThreeDsRule(Map<String, Object> data) {
        validateThreeDsCode("规则类型", asString(data.get("ruleType")), THREE_DS_RULE_TYPES);
        validateThreeDsCode("金额匹配类型", asString(data.get("amountMatchType")), THREE_DS_AMOUNT_MATCH_TYPES);
        validateThreeDsCode("风险条件", asString(data.get("riskCondition")), THREE_DS_RISK_CONDITIONS);
        validateThreeDsCode("触发动作", asString(data.get("triggerAction")), THREE_DS_TRIGGER_ACTIONS);
        requireThreeDsDimension("收单渠道", asString(data.get("channelCode")));
        requireThreeDsDimension("支付方式", asString(data.get("paymentMethod")));
        normalizeThreeDsCardBrand(data);
        requireThreeDsDimension("卡品牌", asString(data.get("cardBrand")));
        validateThreeDsAmount(data);
        Integer priority = asInteger(data.get("priority"));
        if (priority == null || priority <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "规则优先级必须大于 0");
        }
        data.put("priority", priority);
    }

    /**
     * 校验 validate Three Ds Code 相关输入，发现不满足业务约束时抛出明确异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param label label 输入值，含义由调用方法名称和所属业务对象限定
     * @param value 待校验或转换的原始值
     * @param allowedValues allowed Values 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void validateThreeDsCode(String label, String value, Set<String> allowedValues) {
        if (!StringUtils.hasText(value) || !allowedValues.contains(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), label + "不正确");
        }
    }

    /**
     * 强制校验 require Three Ds Dimension 必填值，缺失时中断当前业务流程。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param label label 输入值，含义由调用方法名称和所属业务对象限定
     * @param value 待校验或转换的原始值
     */
    private void requireThreeDsDimension(String label, String value) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择" + label);
        }
    }

    /**
     * 非银行卡支付方式不具备卡品牌维度，统一折叠为 ALL，保证交易匹配维度可解释。
     *
     * @param data 已归一化的规则数据
     */
    private void normalizeThreeDsCardBrand(Map<String, Object> data) {
        String paymentMethod = asString(data.get("paymentMethod"));
        if (!THREE_DS_BANK_CARD_PAYMENT_METHOD.equals(paymentMethod)) {
            data.put("cardBrand", THREE_DS_ALL_DIMENSION);
        }
    }

    /**
     * 校验 3DS 金额条件。当前系统固定使用 USD，ALL 表示不限制金额，其余类型必须使用正数边界。
     *
     * @param data 已归一化的规则数据
     */
    private void validateThreeDsAmount(Map<String, Object> data) {
        String amountMatchType = asString(data.get("amountMatchType"));
        BigDecimal amountMin = normalizeThreeDsAmount((BigDecimal) data.get("amountMin"));
        BigDecimal amountMax = normalizeThreeDsAmount((BigDecimal) data.get("amountMax"));
        if (THREE_DS_AMOUNT_ALL.equals(amountMatchType)) {
            data.put("amountMin", null);
            data.put("amountMax", null);
            data.put("currency", FIXED_LIMIT_CURRENCY_USD);
            return;
        }
        data.put("currency", FIXED_LIMIT_CURRENCY_USD);
        switch (amountMatchType) {
            case "GE" -> {
                if (amountMin == null) {
                    throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请输入最小金额");
                }
                data.put("amountMin", amountMin);
                data.put("amountMax", null);
            }
            case "LE" -> {
                if (amountMax == null) {
                    throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请输入最大金额");
                }
                data.put("amountMin", null);
                data.put("amountMax", amountMax);
            }
            case "BETWEEN" -> {
                if (amountMin == null || amountMax == null) {
                    throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请输入完整金额区间");
                }
                if (amountMin.compareTo(amountMax) > 0) {
                    throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "最小金额不能大于最大金额");
                }
                data.put("amountMin", amountMin);
                data.put("amountMax", amountMax);
            }
            default -> throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "金额匹配类型不正确");
        }
    }

    /**
     * 按限额类型校验金额字段，避免单笔最低和周期限额同时混用最小/最大金额。
     *
     * @param data 已归一化的规则数据
     */
    private void validateMerchantLimitAmountByType(Map<String, Object> data) {
        String limitType = asString(data.get("limitType"));
        if (!MERCHANT_LIMIT_TYPES.contains(limitType)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "限额类型不正确");
        }
        data.put("amountMin", normalizeMerchantLimitAmount((BigDecimal) data.get("amountMin")));
        data.put("amountMax", normalizeMerchantLimitAmount((BigDecimal) data.get("amountMax")));
        if ("SINGLE_MIN".equals(limitType) && data.get("amountMin") == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "单笔最低限额必须填写最小金额");
        }
        if ("SINGLE_MIN".equals(limitType)) {
            data.put("amountMax", null);
        }
        if (List.of("SINGLE_MAX", "DAILY", "WEEKLY", "MONTHLY").contains(limitType) && data.get("amountMax") == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "当前限额类型必须填写最大金额");
        }
        if (List.of("SINGLE_MAX", "DAILY", "WEEKLY", "MONTHLY").contains(limitType)) {
            data.put("amountMin", null);
        }
    }

    /**
     * 校验商户交易限额的周期金额关系。按同一生效范围、商户号和限额场景归组，避免配置出不可解释的周期限额。
     *
     * @param definition 风控规则功能定义
     * @param data       本次保存的限额规则
     * @param excludeId  编辑时排除的当前记录ID，新增时为空
     */
    private void validateMerchantLimitAmountRelations(RiskFunctionDefinition definition, Map<String, Object> data, Long excludeId) {
        if (!FUNCTION_MERCHANT_LIMIT.equals(definition.getFunctionCode())) {
            return;
        }
        Map<String, BigDecimal> amounts = existingMerchantLimitAmounts(definition.getTableName(), data, excludeId);
        String limitType = asString(data.get("limitType"));
        BigDecimal amount = merchantLimitAmount(data);
        if (StringUtils.hasText(limitType) && amount != null) {
            amounts.put(limitType, amount);
        }
        BigDecimal daily = amounts.get(LIMIT_TYPE_DAILY);
        BigDecimal weekly = amounts.get(LIMIT_TYPE_WEEKLY);
        BigDecimal monthly = amounts.get(LIMIT_TYPE_MONTHLY);
        if (daily != null && weekly != null && weekly.compareTo(daily.multiply(WEEKLY_LIMIT_MULTIPLIER)) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "周限额不能超过日限额的7倍");
        }
        if (weekly != null && monthly != null && monthly.compareTo(weekly.multiply(MONTHLY_LIMIT_MULTIPLIER)) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "月限额不能超过周限额的4倍");
        }
    }

    /**
     * 完成 existing Merchant Limit Amounts 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param tableName table Name 输入值，含义由调用方法名称和所属业务对象限定
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param data data 输入值，含义由调用方法名称和所属业务对象限定
     * @param excludeId exclude Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 按渠道协议格式化后的金额字符串或金额计算结果
     */
    private Map<String, BigDecimal> existingMerchantLimitAmounts(String tableName, Map<String, Object> data, Long excludeId) {
        List<Map<String, Object>> rows = riskManagementMapper.selectMerchantLimitAmounts(
                tableName,
                (String) data.get("merchantScope"),
                (String) data.get("merchantId"),
                (String) data.get("matchValue"),
                excludeId
        );
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String limitType = asString(row.get("limit_type"));
            BigDecimal amount = merchantLimitAmount(row);
            if (StringUtils.hasText(limitType) && amount != null) {
                amounts.put(limitType, amount);
            }
        }
        return amounts;
    }

    /**
     * 完成 merchant Limit Amount 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param data data 输入值，含义由调用方法名称和所属业务对象限定
     * @return 按渠道协议格式化后的金额字符串或金额计算结果
     */
    private BigDecimal merchantLimitAmount(Map<String, Object> data) {
        return "SINGLE_MIN".equals(asString(data.get("limitType"))) || "SINGLE_MIN".equals(asString(data.get("limit_type")))
                ? (BigDecimal) data.getOrDefault("amountMin", data.get("amount_min"))
                : (BigDecimal) data.getOrDefault("amountMax", data.get("amount_max"));
    }

    /**
     * 商户交易限额固定 USD，配置金额只允许保留 2 位小数；尾随 0 会归一化，不做四舍五入。
     *
     * @param amount 管理端录入或导入的限额金额
     * @return 保留 2 位小数的金额，空值原样返回
     */
    private BigDecimal normalizeMerchantLimitAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        try {
            return amount.setScale(MERCHANT_LIMIT_AMOUNT_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户交易限额金额最多保留 2 位小数");
        }
    }

    /**
     * 3DS 金额当前固定 USD，只允许保留 2 位小数；不做静默四舍五入，避免管理端展示和交易匹配金额不一致。
     *
     * @param amount 管理端录入或导入的 3DS 金额
     * @return 保留 2 位小数的金额，空值原样返回
     */
    private BigDecimal normalizeThreeDsAmount(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        try {
            return amount.setScale(THREE_DS_AMOUNT_SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "3DS规则金额最多保留 2 位小数");
        }
    }

    /**
     * 校验交易频率限定规则。元素白名单只包含交易请求中稳定可取的字段，避免管理端配置不可执行规则。
     *
     * @param data     已归一化的规则数据
     * @param elements 频率规则元素 JSON
     */
    private void validateFrequencyRule(Map<String, Object> data, Map<String, Object> elements) {
        Object selectedElements = elements.get("elements");
        if (!(selectedElements instanceof List<?> list) || list.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请至少选择一个频率统计元素");
        }
        List<String> normalizedElements = list.stream()
                .map(this::asString)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
        if (normalizedElements.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请至少选择一个频率统计元素");
        }
        for (String element : normalizedElements) {
            if (!FREQUENCY_ELEMENT_CODES.contains(element)) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "频率统计元素不正确");
            }
        }
        String statisticDimension = asString(elements.get("statisticDimension"));
        if (!List.of("ANY_ELEMENT", "ELEMENT_COMBINATION").contains(statisticDimension)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "统计维度不正确");
        }
        String windowUnit = asString(elements.get("windowUnit"));
        if (!List.of("MINUTE", "HOUR", "DAY").contains(windowUnit)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "时间窗口单位不正确");
        }
        Integer windowValue = asInteger(elements.get("windowValue"));
        if (windowValue == null || windowValue <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "时间窗口必须大于 0");
        }
        Integer allowedCount = asInteger(elements.get("allowedCount"));
        if (allowedCount == null || allowedCount <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "允许交易次数必须大于 0");
        }
        Integer successCount = asInteger(elements.get("successCount"));
        if (successCount != null && (successCount < 0 || successCount > allowedCount)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "成功交易次数必须大于等于 0 且不能超过允许交易次数");
        }
        if (!allowedCount.equals(data.get("thresholdCount"))) {
            data.put("thresholdCount", allowedCount);
        }
        data.put("timeWindowSeconds", frequencyWindowSeconds(windowUnit, windowValue));
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("elements", normalizedElements);
        canonical.put("statisticDimension", statisticDimension);
        canonical.put("windowUnit", windowUnit);
        canonical.put("windowValue", windowValue);
        canonical.put("allowedCount", allowedCount);
        canonical.put("successCount", successCount == null ? 0 : successCount);
        data.put("elementsJson", JsonUtils.toJsonString(canonical));
    }

    /**
     * 将频率规则窗口单位和值统一折算为秒数，作为后续交易统计窗口的唯一执行字段。
     *
     * @param windowUnit  窗口单位：MINUTE、HOUR、DAY
     * @param windowValue 窗口数值
     * @return 窗口秒数
     */
    private int frequencyWindowSeconds(String windowUnit, int windowValue) {
        int multiplier = switch (windowUnit) {
            case "DAY" -> 86400;
            case "HOUR" -> 3600;
            default -> 60;
        };
        return windowValue * multiplier;
    }

    /**
     * 按规则类型归一化匹配值，保证页面录入值和后续交易检索值使用同一口径。
     *
     * @param definition 风控规则功能定义
     * @param value      管理端录入的匹配值
     * @return 归一化后的匹配值；空输入返回 null
     */
    private String normalizeRuleMatchValue(RiskFunctionDefinition definition, String value) {
        String text = trim(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text;
    }

    /**
     * 归一化规则查询条件。查询场景对非法输入降级为原值模糊查询，避免用户误输导致整个列表不可查。
     *
     * @param definition 风控规则功能定义
     * @param value      查询输入值
     * @return 查询使用的匹配值
     */
    private String normalizeRuleQueryMatchValue(RiskFunctionDefinition definition, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return normalizeRuleMatchValue(definition, value);
        } catch (RuntimeException ignored) {
            return trim(value);
        }
    }

    /**
     * 返回规则默认匹配方式。结构化规则不再依赖自由匹配方式，保留方法仅兼容通用规则表字段。
     *
     * @param definition 风控规则功能定义
     * @return 默认匹配方式
     */
    private String defaultRuleMatchMode(RiskFunctionDefinition definition) {
        return null;
    }

    /**
     * 根据 Alpha-2 国家编码补齐 Alpha-3 编码，名单交易匹配统一使用 Alpha-3。
     *
     * @param data 待保存的数据映射
     */
    private void applyCountryMetadata(Map<String, Object> data) {
        String countryAlpha2 = (String) data.get("countryAlpha2");
        if (!StringUtils.hasText(countryAlpha2)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择国家/地区");
        }
        Map<String, Object> country = riskManagementMapper.selectCountryOptionByAlpha2(countryAlpha2);
        if (country == null || country.isEmpty()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "国家/地区不存在或已停用");
        }
        data.put("countryAlpha3", upper(asString(country.get("extra"))));
    }

    /**
     * 完成 country Alpha3 From Alpha2 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param countryAlpha2 country Alpha2 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String countryAlpha3FromAlpha2(String countryAlpha2) {
        if (!StringUtils.hasText(countryAlpha2)) {
            return null;
        }
        Map<String, Object> country = riskManagementMapper.selectCountryOptionByAlpha2(upper(countryAlpha2));
        if (country == null || country.isEmpty()) {
            return "__INVALID_COUNTRY__";
        }
        return upper(asString(country.get("extra")));
    }

    /**
     * 标准化 normalize Region Level Fields 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param data data 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void normalizeRegionLevelFields(Map<String, Object> data) {
        String level = (String) data.get("regionMatchLevel");
        if (!List.of("COUNTRY", "STATE", "CITY").contains(level)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "区域级别不正确");
        }
        if ("COUNTRY".equals(level)) {
            data.put("stateProvinceName", "");
            data.put("cityName", "");
            return;
        }
        if (!StringUtils.hasText((String) data.get("stateProvinceName"))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择或输入州/省");
        }
        if ("STATE".equals(level)) {
            data.put("cityName", "");
            return;
        }
        if (!StringUtils.hasText((String) data.get("cityName"))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请选择或输入城市");
        }
    }

    /**
     * 解析 resolve Card Brand 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 解析或查询得到的业务值
     */
    private String resolveCardBrand(RiskFunctionDefinition definition, RiskDTOs.RiskListSaveRequest request) {
        if (!hasCardBrandField(definition)) {
            return trim(request.getCardBrand());
        }
        String cardValue = "cardBin".equals(definition.getFunctionCode())
                ? defaultIfBlank(request.getMatchValueStart(), request.getMatchValuePlain())
                : defaultIfBlank(request.getMatchValuePlain(), request.getMatchValueMasked());
        if (!StringUtils.hasText(cardValue)) {
            return null;
        }
        String digits = cardValue.replaceAll("\\s+", "");
        if (!digits.matches("\\d{6,19}")) {
            return null;
        }
        return detectCardBrand(digits);
    }

    /**
     * 完成 detect Card Brand 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param cardNo 卡相关输入，属于敏感或可识别数据，禁止直接写入日志
     * @return 当前方法计算或转换后的业务结果
     */
    private String detectCardBrand(String cardNo) {
        if (cardNo.matches("^4.*")) {
            return "VISA";
        }
        if (cardNo.matches("^(5[1-5]|2[2-7]).*")) {
            return "MASTERCARD";
        }
        if (cardNo.matches("^3[47].*")) {
            return "AMEX";
        }
        if (cardNo.matches("^35.*")) {
            return "JCB";
        }
        if (cardNo.matches("^62.*")) {
            return "UNIONPAY";
        }
        if (cardNo.matches("^(6011|65|64[4-9]).*")) {
            return "DISCOVER";
        }
        if (cardNo.matches("^(30[0-5]|36|38|39).*")) {
            return "DINERS_CLUB";
        }
        if (cardNo.matches("^(50|5[6-9]|6[0-9]).*")) {
            return "MAESTRO";
        }
        return null;
    }

    /**
     * 标准化 normalize Scope 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param data data 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void normalizeScope(Map<String, Object> data) {
        String merchantScope = defaultIfBlank((String) data.get("merchantScope"), DEFAULT_SCOPE).toUpperCase(Locale.ROOT);
        data.put("merchantScope", merchantScope);
        if (DEFAULT_SCOPE.equals(merchantScope)) {
            data.put("merchantId", null);
            return;
        }
        if (!StringUtils.hasText((String) data.get("merchantId"))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "商户风控必须选择商户号");
        }
    }

    /**
     * 判断是否为 AML 强制拦截功能。AML 配置为全局合规名单，不存在商户生效范围。
     *
     * @param definition 风控功能定义
     * @return true 表示 AML 强制拦截功能
     */
    private boolean isAmlFunction(RiskFunctionDefinition definition) {
        return MODULE_AML.equalsIgnoreCase(definition.getModuleType());
    }

    /**
     * AML 强制拦截不接受页面、导入或接口传入的商户维度，统一落库为全局配置。
     *
     * @param definition 风控功能定义
     * @param data       待写入字段映射
     */
    private void applyAmlGlobalScope(RiskFunctionDefinition definition, Map<String, Object> data) {
        if (!isAmlFunction(definition)) {
            return;
        }
        data.put("merchantScope", DEFAULT_SCOPE);
        data.put("merchantId", "");
    }

    /**
     * AML 强制拦截不接受页面查询传入的商户维度，只查询全局名单。
     *
     * @param definition 风控功能定义
     * @param query      查询请求
     */
    private void applyAmlGlobalScope(RiskFunctionDefinition definition, RiskDTOs.RiskListQueryRequest query) {
        if (!isAmlFunction(definition)) {
            return;
        }
        query.setMerchantScope(DEFAULT_SCOPE);
        query.setMerchantId("");
    }

    /**
     * 判断列表、导出和导入模板是否需要展示商户维度列。
     *
     * @param definition 风控功能定义
     * @return true 表示展示生效范围和商户号
     */
    private boolean showListMerchantDimension(RiskFunctionDefinition definition) {
        return !isAmlFunction(definition) && !isMerchantWhitelist(definition);
    }

    /**
     * 完成 merchant Name 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 当前方法计算或转换后的业务结果
     */
    private String merchantName(String merchantId) {
        return StringUtils.hasText(merchantId) ? riskManagementMapper.selectMerchantName(merchantId) : null;
    }

    /**
     * 完成 default Effective Time 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param effectiveTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 当前方法计算或转换后的业务结果
     */
    private LocalDateTime defaultEffectiveTime(LocalDateTime effectiveTime) {
        return effectiveTime == null ? LocalDateTime.now() : effectiveTime;
    }

    /**
     * 完成 apply Validity 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param data data 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     */
    private void applyValidity(Map<String, Object> data, RiskDTOs.RiskListSaveRequest request) {
        String validityType = defaultIfBlank(request.getValidityType(), VALIDITY_SUPER_LONG).toUpperCase(Locale.ROOT);
        data.put("validityType", validityType);
        if (VALIDITY_SUPER_LONG.equals(validityType)) {
            data.put("validityDays", null);
            data.put("expireTime", null);
            return;
        }
        Integer validityDays = request.getValidityDays();
        if (validityDays == null || validityDays <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请输入有效天数");
        }
        if (VALIDITY_LONG.equals(validityType) && validityDays < LONG_VALIDITY_MIN_DAYS) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "长期有效期至少 120 天");
        }
        if (!VALIDITY_LONG.equals(validityType) && !VALIDITY_LIMITED.equals(validityType)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "有效期类型不正确");
        }
        LocalDateTime effectiveTime = (LocalDateTime) data.get("effectiveTime");
        data.put("validityDays", validityDays);
        data.put("expireTime", effectiveTime.plusDays(validityDays));
    }

    /**
     * 完成 apply Region Validity 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param data data 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     */
    private void applyRegionValidity(Map<String, Object> data, RiskDTOs.RegionSaveRequest request) {
        String validityType = defaultIfBlank(request.getValidityType(), VALIDITY_SUPER_LONG).toUpperCase(Locale.ROOT);
        data.put("validityType", validityType);
        if (VALIDITY_SUPER_LONG.equals(validityType)) {
            data.put("validityDays", null);
            data.put("expireTime", null);
            return;
        }
        Integer validityDays = request.getValidityDays();
        if (validityDays == null || validityDays <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "请输入有效天数");
        }
        if (VALIDITY_LONG.equals(validityType) && validityDays < LONG_VALIDITY_MIN_DAYS) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "长期有效期至少 120 天");
        }
        if (!VALIDITY_LONG.equals(validityType) && !VALIDITY_LIMITED.equals(validityType)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "有效期类型不正确");
        }
        LocalDateTime effectiveTime = (LocalDateTime) data.get("effectiveTime");
        data.put("validityDays", validityDays);
        data.put("expireTime", effectiveTime.plusDays(validityDays));
    }

    /**
     * 完成 default Risk Level 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String defaultRiskLevel(RiskFunctionDefinition definition) {
        if ("RULE".equalsIgnoreCase(definition.getModuleType())) {
            if ("frequency".equals(definition.getFunctionCode()) || FUNCTION_MERCHANT_LIMIT.equals(definition.getFunctionCode())) {
                return "HIGH";
            }
            return DEFAULT_RISK_LEVEL;
        }
        if ("AML".equalsIgnoreCase(definition.getModuleType())) {
            return "CRITICAL";
        }
        if ("BLACK".equalsIgnoreCase(definition.getModuleType())) {
            return "HIGH";
        }
        if ("WHITE".equalsIgnoreCase(definition.getModuleType())) {
            return "LOW";
        }
        return DEFAULT_RISK_LEVEL;
    }

    /**
     * 完成 default Decision Action 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String defaultDecisionAction(RiskFunctionDefinition definition) {
        if ("WHITE".equalsIgnoreCase(definition.getModuleType())) {
            return "PASS";
        }
        if ("AML".equalsIgnoreCase(definition.getModuleType()) || "BLACK".equalsIgnoreCase(definition.getModuleType())) {
            return "REJECT";
        }
        if ("RULE".equalsIgnoreCase(definition.getModuleType())
                && List.of(FUNCTION_SOURCE_URL, FUNCTION_MERCHANT_LIMIT, "frequency").contains(definition.getFunctionCode())) {
            return "REJECT";
        }
        return DEFAULT_DECISION_ACTION;
    }

    /**
     * 完成 sanitize Snapshot 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param source source 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private Map<String, Object> sanitizeSnapshot(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>(source);
        copy.remove("match_value_cipher");
        copy.remove("matchValueCipher");
        copy.remove("match_value_hash");
        copy.remove("matchValueHash");
        copy.remove("match_value_start_number");
        copy.remove("matchValueStartNumber");
        copy.remove("match_value_end_number");
        copy.remove("matchValueEndNumber");
        copy.remove("ip_version");
        copy.remove("ipVersion");
        return copy;
    }

    /**
     * 转换生成 to Options 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param List List 输入值，含义由调用方法名称和所属业务对象限定
     * @param rows rows 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private List<RiskDTOs.OptionItem> toOptions(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            RiskDTOs.OptionItem item = option(asString(row.get("label")), asString(row.get("value")), asString(row.get("extra")));
            item.setNumericCode(asString(row.get("numericCode")));
            item.setFlagEmoji(asString(row.get("flagEmoji")));
            item.setContinentCode(asString(row.get("continentCode")));
            item.setContinentName(asString(row.get("continentName")));
            return item;
        }).toList();
    }

    /**
     * 完成 option 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param label label 输入值，含义由调用方法名称和所属业务对象限定
     * @param value 待校验或转换的原始值
     * @param extra extra 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private RiskDTOs.OptionItem option(String label, String value, String extra) {
        RiskDTOs.OptionItem item = new RiskDTOs.OptionItem();
        item.setLabel(label);
        item.setValue(value);
        item.setExtra(extra);
        return item;
    }

    /**
     * 完成 ensure Function Permission 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @param action action 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void ensureFunctionPermission(RiskFunctionDefinition definition, String action) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        List<String> permissions = account.getPermissions();
        if (permissions.contains("*:*:*")) {
            return;
        }
        String requiredPermission = definition.getPermissionPrefix() + ":" + action;
        if (!permissions.contains(requiredPermission)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
    }

    /**
     * 完成 template Row 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private Map<String, Object> templateRow(RiskFunctionDefinition definition) {
        Map<String, Object> row = new LinkedHashMap<>();
        if (definition.isRegionFunction()) {
            row.put("merchantScope", "GLOBAL");
            row.put("merchantId", "");
            row.put("regionMatchLevel", "CITY");
            row.put("countryAlpha2", "US");
            row.put("stateProvinceName", "California");
            row.put("cityName", "Los Angeles");
            row.put("validityType", VALIDITY_SUPER_LONG);
            row.put("validityDays", "");
            row.put("sourceType", SOURCE_IMPORT);
        } else if (definition.isRuleFunction()) {
            row.put("ruleName", templateSampleRuleName());
            row.put("merchantScope", "GLOBAL");
            row.put("merchantId", "");
            row.put("matchMode", "EXACT");
            row.put("matchValue", "");
            row.put("limitType", "SINGLE_MAX");
            row.put("amountMin", "");
            row.put("amountMax", BigDecimal.ZERO);
            row.put("currency", "USD");
            row.put("timeWindowSeconds", "");
            row.put("thresholdCount", "");
            row.put("elementsJson", "{}");
            fillRuleTemplateValue(definition, row);
        } else {
            row.put("merchantScope", "GLOBAL");
            row.put("merchantId", "");
            fillListTemplateValue(definition, row);
            row.put("validityType", VALIDITY_SUPER_LONG);
            row.put("validityDays", "");
            row.put("sourceType", SOURCE_IMPORT);
            pruneListTemplateRow(definition, row);
        }
        row.put("riskLevel", defaultRiskLevel(definition));
        row.put("decisionAction", defaultDecisionAction(definition));
        row.put("status", ENABLED);
        row.put("remark", templateSampleRemark());
        return toCsvRow(definition, row);
    }

    /**
     * 完成 template Sample Rule Name 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    private String templateSampleRuleName() {
        return isEnglishLocale() ? "Sample Rule" : "示例规则";
    }

    /**
     * 完成 template Sample Remark 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
     */
    private String templateSampleRemark() {
        return isEnglishLocale() ? "Delete this sample row before import" : "导入时请删除示例行";
    }

    /**
     * 填充 fill Rule Template Value 相关字段，保持来源对象与目标对象的业务含义一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void fillRuleTemplateValue(RiskFunctionDefinition definition, Map<String, Object> row) {
        String code = definition.getFunctionCode();
        if ("sourceUrl".equals(code)) {
            row.put("ruleName", "");
            row.put("merchantScope", "");
            row.put("merchantId", "200001");
            row.put("matchMode", "");
            row.put("matchValue", "");
            row.put("sourceUrl", "https://example.com");
            row.put("sourceHost", "example.com");
            row.put("limitType", "");
            row.put("amountMax", "");
            row.put("currency", "");
        } else if (FUNCTION_MERCHANT_LIMIT.equals(code)) {
            row.put("ruleName", isEnglishLocale() ? "Single transaction max amount" : "单笔交易最大限额");
            row.put("limitType", "SINGLE_MAX");
            row.put("limitAmount", "1000.00 USD");
            row.put("currency", FIXED_LIMIT_CURRENCY_USD);
        } else if ("frequency".equals(code)) {
            row.put("ruleName", isEnglishLocale() ? "Card and IP frequency limit" : "卡号和IP频率限制");
            row.put("timeWindowSeconds", 3600);
            row.put("thresholdCount", 5);
            row.put("elementsJson", "{\"elements\":[\"cardFingerprint\",\"ip\"],\"statisticDimension\":\"ELEMENT_COMBINATION\",\"windowUnit\":\"HOUR\",\"windowValue\":1,\"allowedCount\":5,\"successCount\":3}");
            row.put("statDimension", "ELEMENT_COMBINATION");
            row.put("elementSet", isEnglishLocale() ? "Card Fingerprint, IP Address" : "卡指纹、IP地址");
            row.put("windowValue", 1);
            row.put("windowUnit", isEnglishLocale() ? "hour(s)" : "小时");
            row.put("maxTransactionCount", 5);
            row.put("maxSuccessCount", 3);
            row.put("limitType", "");
            row.put("amountMax", "");
            row.put("currency", "");
        } else if ("threeDs".equals(code)) {
            row.put("ruleName", isEnglishLocale() ? "High risk transaction force 3DS" : "高风险交易强制3DS");
            row.put("matchMode", "");
            row.put("matchValue", "");
            row.put("limitType", "");
            row.put("ruleType", THREE_DS_RULE_TYPE_RISK);
            row.put("channelCode", THREE_DS_ALL_DIMENSION);
            row.put("paymentMethod", THREE_DS_DEFAULT_PAYMENT_METHOD);
            row.put("cardBrand", THREE_DS_ALL_DIMENSION);
            row.put("amountCondition", isEnglishLocale() ? "Greater or Equal 100.00 USD" : "大于等于 100.00 USD");
            row.put("amountMatchType", "GE");
            row.put("amountMin", new BigDecimal("100.00"));
            row.put("amountMax", "");
            row.put("currency", "USD");
            row.put("riskCondition", "HIGH_AND_ABOVE");
            row.put("triggerAction", THREE_DS_ACTION_FORCE);
            row.put("priority", THREE_DS_DEFAULT_PRIORITY);
        }
    }

    /**
     * 填充 fill List Template Value 相关字段，保持来源对象与目标对象的业务含义一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void fillListTemplateValue(RiskFunctionDefinition definition, Map<String, Object> row) {
        String code = definition.getFunctionCode();
        row.put("matchValuePlain", "example");
        row.put("matchValueStart", "");
        row.put("matchValueEnd", "");
        if ("cardNo".equals(code) || "card".equals(code)) {
            row.put("matchValuePlain", "4111111111111111");
        } else if ("cardBin".equals(code)) {
            row.put("matchValuePlain", "");
            row.put("matchValueStart", "411111");
            row.put("matchValueEnd", "411111");
        } else if ("ip".equals(code)) {
            row.put("matchValuePlain", "");
            row.put("matchValueStart", "203.0.113.10");
            row.put("matchValueEnd", "WHITE".equals(definition.getModuleType()) ? "203.0.113.10" : "203.0.113.20");
        } else if ("country".equals(code) || code.endsWith("Country") || code.contains("Country")) {
            row.put("matchValuePlain", "US");
            row.put("countryAlpha2", "US");
            row.put("countryAlpha3", "USA");
            row.put("countryNumeric", "840");
        } else if ("email".equals(code)) {
            row.put("matchValuePlain", "risk@example.com");
        } else if ("emailDomain".equals(code)) {
            row.put("matchValuePlain", "example.com");
        } else if ("phone".equals(code)) {
            row.put("matchValuePlain", "+12025550123");
        } else if ("legalPerson".equals(code)) {
            row.put("matchValuePlain", "John Smith");
        } else if ("enterprise".equals(code)) {
            row.put("matchValuePlain", "Example Trading LLC");
        } else if ("merchantBillingAddress".equals(code)) {
            row.put("matchValuePlain", "100 Market Street, San Francisco, CA");
        } else if ("sourceUrl".equals(code)) {
            row.put("matchValuePlain", "example.com");
        }
    }

    /**
     * 完成 prune List Template Row 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void pruneListTemplateRow(RiskFunctionDefinition definition, Map<String, Object> row) {
        if (!hasRangeFields(definition)) {
            row.remove("matchValueStart");
            row.remove("matchValueEnd");
        }
        if (!hasCardBrandField(definition)) {
            row.remove("cardBrand");
        }
        if (!hasCountryFields(definition)) {
            row.remove("countryAlpha2");
            row.remove("countryAlpha3");
            row.remove("countryNumeric");
        }
    }

    /**
     * 完成 import Batch No 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String importBatchNo(RiskFunctionDefinition definition) {
        return "RISK-" + definition.getModuleType() + "-" + definition.getFunctionCode() + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 推进 mark Import Batch Success After Commit 对应的状态或处理结果，并保留后续查询所需信息。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param batchNo batch No 输入值，含义由调用方法名称和所属业务对象限定
     * @param successCount success Count 输入值，含义由调用方法名称和所属业务对象限定
     */
    private void markImportBatchSuccessAfterCommit(String batchNo, int successCount) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            importLogService.markSuccess(batchNo, successCount);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                importLogService.markSuccess(batchNo, successCount);
            }
        });
    }

    /**
     * 完成 import Error Summary 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param batchNo batch No 输入值，含义由调用方法名称和所属业务对象限定
     * @param errors errors 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String importErrorSummary(String batchNo, List<AdminRiskImportLogService.ImportRowError> errors) {
        List<String> messages = errors.stream()
                .limit(5)
                .map(error -> "line " + error.rowNo() + ": " + error.errorMessage())
                .toList();
        String suffix = errors.size() > 5 ? "; ..." : "";
        return "导入失败，批次号：" + batchNo + "；" + String.join("; ", messages) + suffix;
    }

    /**
     * 判断 is Source Url Rule 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isSourceUrlRule(RiskFunctionDefinition definition) {
        return definition == RiskFunctionDefinition.RULE_SOURCE_URL;
    }

    /**
     * 判断 is Merchant Limit Rule 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isMerchantLimitRule(RiskFunctionDefinition definition) {
        return definition == RiskFunctionDefinition.RULE_MERCHANT_LIMIT;
    }

    /**
     * 判断 is Three Ds Rule 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param definition definition 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isThreeDsRule(RiskFunctionDefinition definition) {
        return FUNCTION_THREE_DS.equals(definition.getFunctionCode());
    }

    /**
     * 完成 import Error Message 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param exception exception 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String importErrorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return StringUtils.hasText(message) ? message : exception.getClass().getSimpleName();
    }

    /**
     * 完成 sanitize Import Error Message 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param exception exception 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String sanitizeImportErrorMessage(RuntimeException exception) {
        String message = importErrorMessage(exception)
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+", "***")
                .replaceAll("\\d{12,19}", "***");
        return truncate(message, IMPORT_ERROR_MESSAGE_MAX_LENGTH);
    }

    /**
     * 完成 sanitize Import Row 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String sanitizeImportRow(Map<String, String> row) {
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : row.entrySet()) {
            sanitized.put(entry.getKey(), sensitiveImportColumn(entry.getKey()) ? "***" : trim(entry.getValue()));
        }
        return truncate(JsonUtils.toJsonString(sanitized), IMPORT_RAW_CONTENT_MAX_LENGTH);
    }

    /**
     * 完成 sensitive Import Column 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param columnName column Name 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private boolean sensitiveImportColumn(String columnName) {
        String normalized = columnName == null ? "" : columnName.toLowerCase(Locale.ROOT).replace("_", "");
        return normalized.contains("matchvalue")
                || normalized.contains("masked")
                || normalized.contains("hash")
                || normalized.contains("cipher")
                || normalized.contains("card")
                || normalized.contains("phone")
                || normalized.contains("email")
                || normalized.contains("address")
                || normalized.contains("fingerprint")
                || normalized.contains("持卡人")
                || normalized.contains("卡号")
                || normalized.contains("手机号")
                || normalized.contains("邮箱")
                || normalized.contains("地址")
                || normalized.contains("指纹");
    }

    /**
     * 完成 read Import Rows 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param file file 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private List<ImportRow> readImportRows(MultipartFile file) {
        return isExcelImportFile(file) ? readExcel(file) : readCsv(file);
    }

    /**
     * 判断 is Excel Import File 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param file file 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isExcelImportFile(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        String normalizedFileName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        return normalizedFileName.endsWith(".xlsx")
                || normalizedFileName.endsWith(".xls")
                || normalizedContentType.contains("spreadsheet")
                || normalizedContentType.contains("excel");
    }

    /**
     * 完成 read Csv 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param file file 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private List<ImportRow> readCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (!StringUtils.hasText(headerLine)) {
                return List.of();
            }
            String[] headers = parseCsvLine(stripBom(headerLine)).toArray(String[]::new);
            List<ImportRow> rows = new ArrayList<>();
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                List<String> values = parseCsvLine(line);
                Map<String, String> row = new LinkedHashMap<>();
                for (int index = 0; index < headers.length; index++) {
                    row.put(headers[index], index < values.size() ? values.get(index) : null);
                }
                rows.add(new ImportRow(lineNo, row));
            }
            return rows;
        } catch (IOException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "读取CSV导入文件失败");
        }
    }

    /**
     * 完成 read Excel 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param file file 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private List<ImportRow> readExcel(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }
            Sheet sheet = workbook.getSheetAt(0);
            int headerRowIndex = findExcelHeaderRow(sheet);
            if (headerRowIndex < 0) {
                return List.of();
            }
            DataFormatter formatter = new DataFormatter();
            List<String> headers = excelHeaders(sheet.getRow(headerRowIndex), formatter);
            List<ImportRow> rows = new ArrayList<>();
            for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row excelRow = sheet.getRow(rowIndex);
                if (excelRow == null || isBlankExcelRow(excelRow, headers.size(), formatter)) {
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                    String header = headers.get(columnIndex);
                    if (StringUtils.hasText(header)) {
                        row.put(header, excelCellText(excelRow.getCell(columnIndex), formatter));
                    }
                }
                rows.add(new ImportRow(rowIndex + 1, row));
            }
            return rows;
        } catch (IOException | RuntimeException exception) {
            throw new ServiceException(ApiResultEnum.COMMON_FAILED.getCode(), "读取Excel导入文件失败");
        }
    }

    /**
     * 查询 find Excel Header Row 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param sheet sheet 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private int findExcelHeaderRow(Sheet sheet) {
        DataFormatter formatter = new DataFormatter();
        int maxRowIndex = Math.min(sheet.getLastRowNum(), EXCEL_IMPORT_HEADER_SCAN_ROWS - 1);
        for (int rowIndex = 0; rowIndex <= maxRowIndex; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            int matchedHeaders = 0;
            int lastCellNum = Math.max(row.getLastCellNum(), 0);
            for (int columnIndex = 0; columnIndex < lastCellNum; columnIndex++) {
                if (isImportHeader(excelCellText(row.getCell(columnIndex), formatter))) {
                    matchedHeaders++;
                }
            }
            if (matchedHeaders >= 2) {
                return rowIndex;
            }
        }
        return -1;
    }

    /**
     * 判断 is Import Header 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param header header 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isImportHeader(String header) {
        if (!StringUtils.hasText(header)) {
            return false;
        }
        String normalized = header.trim();
        for (String key : IMPORT_COLUMN_KEYS) {
            if (normalized.equals(key) || csvHeaderAliases(key).contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 完成 excel Headers 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param headerRow header Row 输入值，含义由调用方法名称和所属业务对象限定
     * @param formatter formatter 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private List<String> excelHeaders(Row headerRow, DataFormatter formatter) {
        if (headerRow == null) {
            return List.of();
        }
        int lastCellNum = Math.max(headerRow.getLastCellNum(), 0);
        List<String> headers = new ArrayList<>(lastCellNum);
        for (int columnIndex = 0; columnIndex < lastCellNum; columnIndex++) {
            headers.add(stripBom(excelCellText(headerRow.getCell(columnIndex), formatter)));
        }
        return headers;
    }

    /**
     * 判断 is Blank Excel Row 条件是否成立，用于控制后续业务分支。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @param columnSize column Size 输入值，含义由调用方法名称和所属业务对象限定
     * @param formatter formatter 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isBlankExcelRow(Row row, int columnSize, DataFormatter formatter) {
        for (int columnIndex = 0; columnIndex < columnSize; columnIndex++) {
            if (StringUtils.hasText(excelCellText(row.getCell(columnIndex), formatter))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 完成 excel Cell Text 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param cell cell 输入值，含义由调用方法名称和所属业务对象限定
     * @param formatter formatter 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    private String excelCellText(Cell cell, DataFormatter formatter) {
        return cell == null ? null : trim(formatter.formatCellValue(cell));
    }

    /**
     * 解析 parse Csv Line 输入文本并转换为内部可校验的数据结构。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param line line 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析后的内部数据结构或业务值
     */
    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private RiskDTOs.RiskListSaveRequest toListRequest(Map<String, String> row) {
        RiskDTOs.RiskListSaveRequest request = new RiskDTOs.RiskListSaveRequest();
        request.setMerchantScope(csvCode("merchantScope", value(row, "merchantScope")));
        request.setMerchantId(value(row, "merchantId"));
        request.setRuleName(value(row, "ruleName"));
        request.setMatchValuePlain(value(row, "matchValuePlain"));
        request.setMatchValueMasked(value(row, "matchValueMasked"));
        request.setMatchValueHash(value(row, "matchValueHash"));
        request.setMatchValueStart(value(row, "matchValueStart"));
        request.setMatchValueEnd(value(row, "matchValueEnd"));
        request.setIpVersion(value(row, "ipVersion"));
        request.setCardBrand(value(row, "cardBrand"));
        request.setCountryAlpha2(value(row, "countryAlpha2"));
        request.setCountryAlpha3(value(row, "countryAlpha3"));
        request.setCountryNumeric(value(row, "countryNumeric"));
        request.setRiskLevel(csvCode("riskLevel", value(row, "riskLevel")));
        request.setDecisionAction(csvCode("decisionAction", value(row, "decisionAction")));
        request.setValidityType(csvCode("validityType", value(row, "validityType")));
        request.setValidityDays(intValue(row, "validityDays"));
        request.setSourceType(SOURCE_IMPORT);
        request.setStatus(statusValue(row, "status"));
        request.setRemark(value(row, "remark"));
        return request;
    }

    private RiskDTOs.RegionSaveRequest toRegionRequest(Map<String, String> row) {
        RiskDTOs.RegionSaveRequest request = new RiskDTOs.RegionSaveRequest();
        request.setMerchantScope(csvCode("merchantScope", value(row, "merchantScope")));
        request.setMerchantId(value(row, "merchantId"));
        request.setRuleName(value(row, "ruleName"));
        request.setRegionMatchLevel(csvCode("regionMatchLevel", required(row, "regionMatchLevel")));
        request.setCountryAlpha2(required(row, "countryAlpha2"));
        request.setStateProvinceName(value(row, "stateProvinceName"));
        request.setCityName(value(row, "cityName"));
        request.setRiskLevel(csvCode("riskLevel", value(row, "riskLevel")));
        request.setDecisionAction(csvCode("decisionAction", value(row, "decisionAction")));
        request.setValidityType(csvCode("validityType", value(row, "validityType")));
        request.setValidityDays(intValue(row, "validityDays"));
        request.setSourceType(SOURCE_IMPORT);
        request.setStatus(statusValue(row, "status"));
        request.setRemark(value(row, "remark"));
        return request;
    }

    private RiskDTOs.RiskRuleSaveRequest toRuleRequest(Map<String, String> row) {
        RiskDTOs.RiskRuleSaveRequest request = new RiskDTOs.RiskRuleSaveRequest();
        request.setMerchantScope(csvCode("merchantScope", value(row, "merchantScope")));
        request.setMerchantId(value(row, "merchantId"));
        request.setRuleName(value(row, "ruleName"));
        request.setMatchMode(value(row, "matchMode"));
        request.setMatchValue(value(row, "matchValue"));
        request.setSourceUrl(value(row, "sourceUrl"));
        request.setSourceHost(value(row, "sourceHost"));
        request.setLimitType(value(row, "limitType"));
        applyMerchantLimitAmount(row, request);
        request.setRuleType(csvCode("threeDsRuleType", value(row, "ruleType")));
        request.setChannelCode(value(row, "channelCode"));
        applyThreeDsPaymentScope(row, request);
        applyThreeDsAmountCondition(row, request);
        request.setRiskCondition(csvCode("threeDsRiskCondition", value(row, "riskCondition")));
        request.setTriggerAction(csvCode("threeDsTriggerAction", value(row, "triggerAction")));
        request.setPriority(intValue(row, "priority"));
        applyFrequencyPolicy(row, request);
        request.setRiskLevel(csvCode("riskLevel", value(row, "riskLevel")));
        request.setDecisionAction(csvCode("decisionAction", value(row, "decisionAction")));
        request.setEffectiveTime(localDateTimeValue(row, "effectiveTime"));
        request.setExpireTime(localDateTimeValue(row, "expireTime"));
        request.setStatus(statusValue(row, "status"));
        request.setRemark(value(row, "remark"));
        return request;
    }

    private void applyMerchantLimitAmount(Map<String, String> row, RiskDTOs.RiskRuleSaveRequest request) {
        String limitAmount = value(row, "limitAmount");
        if (StringUtils.hasText(limitAmount)) {
            BigDecimal amount = parseAmountWithCurrency(limitAmount);
            request.setAmountMin(amount);
            request.setAmountMax(amount);
            request.setCurrency(FIXED_LIMIT_CURRENCY_USD);
            return;
        }
        request.setAmountMin(decimalValue(row, "amountMin"));
        request.setAmountMax(decimalValue(row, "amountMax"));
        request.setCurrency(defaultIfBlank(value(row, "currency"), null));
    }

    private BigDecimal parseAmountWithCurrency(String value) {
        List<BigDecimal> amounts = extractAmounts(value);
        return amounts.isEmpty() ? null : amounts.get(0);
    }

    private void applyThreeDsPaymentScope(Map<String, String> row, RiskDTOs.RiskRuleSaveRequest request) {
        String combinedScope = value(row, "paymentMethodCardBrand");
        if (StringUtils.hasText(combinedScope)) {
            ThreeDsPaymentScope scope = parseThreeDsPaymentScope(combinedScope);
            request.setPaymentMethod(scope.paymentMethod());
            request.setCardBrand(scope.cardBrand());
            return;
        }
        request.setPaymentMethod(value(row, "paymentMethod"));
        request.setCardBrand(value(row, "cardBrand"));
    }

    private ThreeDsPaymentScope parseThreeDsPaymentScope(String value) {
        String[] parts = value.split("[/／]", 2);
        String paymentMethod = csvCode("paymentMethod", parts[0]);
        String cardBrand = parts.length > 1 ? csvCode("cardBrand", parts[1]) : null;
        return new ThreeDsPaymentScope(paymentMethod, cardBrand);
    }

    private String threeDsPaymentScopeForCsv(Object paymentMethodValue, Object cardBrandValue) {
        String paymentMethod = csvLabel("paymentMethod", paymentMethodValue);
        String paymentCode = csvCode("paymentMethod", paymentMethod);
        if (!THREE_DS_BANK_CARD_PAYMENT_METHOD.equals(paymentCode)) {
            return paymentMethod;
        }
        return paymentMethod + " / " + csvLabel("cardBrand", cardBrandValue);
    }

    private void applyThreeDsAmountCondition(Map<String, String> row, RiskDTOs.RiskRuleSaveRequest request) {
        String amountCondition = value(row, "amountCondition");
        if (StringUtils.hasText(amountCondition)) {
            ThreeDsAmountCondition condition = parseThreeDsAmountCondition(amountCondition);
            request.setAmountMatchType(condition.amountMatchType());
            request.setAmountMin(condition.amountMin());
            request.setAmountMax(condition.amountMax());
            request.setCurrency("USD");
            return;
        }
        request.setAmountMatchType(csvCode("threeDsAmountMatchType", value(row, "amountMatchType")));
        request.setAmountMin(decimalValue(row, "amountMin"));
        request.setAmountMax(decimalValue(row, "amountMax"));
        request.setCurrency(value(row, "currency"));
    }

    private ThreeDsAmountCondition parseThreeDsAmountCondition(String value) {
        String normalized = trim(value).replace("，", ",");
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized) || normalized.contains("全部金额") || upper.contains("ANY AMOUNT") || upper.equals("ALL")) {
            return new ThreeDsAmountCondition("ALL", null, null);
        }
        if (upper.contains("BETWEEN") || normalized.contains("区间") || normalized.contains("至") || normalized.contains("-")) {
            List<BigDecimal> amounts = extractAmounts(normalized);
            return new ThreeDsAmountCondition("BETWEEN", amounts.isEmpty() ? null : amounts.get(0), amounts.size() > 1 ? amounts.get(1) : null);
        }
        List<BigDecimal> amounts = extractAmounts(normalized);
        BigDecimal amount = amounts.isEmpty() ? null : amounts.get(0);
        if (upper.contains("<=") || upper.contains("LESS") || normalized.contains("小于等于")) {
            return new ThreeDsAmountCondition("LE", null, amount);
        }
        return new ThreeDsAmountCondition("GE", amount, null);
    }

    private String threeDsAmountConditionForCsv(Map<String, Object> row) {
        String preparedCondition = asString(firstValue(row, "amountCondition", "amount_condition"));
        if (StringUtils.hasText(preparedCondition)) {
            return preparedCondition;
        }
        String amountMatchType = defaultIfBlank(asString(firstValue(row, "amountMatchType", "amount_match_type")), "ALL");
        String currency = defaultIfBlank(asString(firstValue(row, "currency")), "USD");
        BigDecimal amountMin = asBigDecimal(firstValue(row, "amountMin", "amount_min"));
        BigDecimal amountMax = asBigDecimal(firstValue(row, "amountMax", "amount_max"));
        boolean english = isEnglishLocale();
        return switch (amountMatchType) {
            case "GE" -> (english ? "Greater or Equal " : "大于等于 ") + amountForCsv(amountMin) + " " + currency;
            case "LE" -> (english ? "Less or Equal " : "小于等于 ") + amountForCsv(amountMax) + " " + currency;
            case "BETWEEN" -> amountForCsv(amountMin) + " " + currency + (english ? " to " : " 至 ") + amountForCsv(amountMax) + " " + currency;
            default -> english ? "Any Amount >= 0 USD" : "全部金额大于等于 0 USD";
        };
    }

    private String merchantLimitAmountForCsv(Map<String, Object> row) {
        String preparedAmount = asString(firstValue(row, "limitAmount", "limit_amount"));
        if (StringUtils.hasText(preparedAmount)) {
            return preparedAmount;
        }
        String limitType = asString(firstValue(row, "limitType", "limit_type"));
        Object amount = "SINGLE_MIN".equals(limitType)
                ? firstValue(row, "amountMin", "amount_min")
                : firstValue(row, "amountMax", "amount_max");
        String currency = defaultIfBlank(asString(firstValue(row, "currency")), FIXED_LIMIT_CURRENCY_USD);
        return amountForCsv(asBigDecimal(amount)) + " " + currency;
    }

    private String amountForCsv(BigDecimal amount) {
        return amount == null ? "-" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private List<BigDecimal> extractAmounts(String value) {
        List<BigDecimal> amounts = new ArrayList<>();
        Matcher matcher = AMOUNT_TEXT_PATTERN.matcher(defaultIfBlank(value, ""));
        while (matcher.find()) {
            amounts.add(new BigDecimal(matcher.group()));
        }
        return amounts;
    }

    private void applyFrequencyPolicy(Map<String, String> row, RiskDTOs.RiskRuleSaveRequest request) {
        request.setTimeWindowSeconds(intValue(row, "timeWindowSeconds"));
        request.setThresholdCount(intValue(row, "thresholdCount"));
        String elementsJson = value(row, "elementsJson");
        if (StringUtils.hasText(elementsJson)) {
            request.setElementsJson(elementsJson);
            return;
        }
        if (!hasAnyValue(row, "statDimension", "elementSet", "windowValue", "windowUnit", "maxTransactionCount", "maxSuccessCount")) {
            return;
        }
        String windowUnit = csvCode("frequencyWindowUnit", defaultIfBlank(value(row, "windowUnit"), "HOUR"));
        Integer windowValue = intValue(row, "windowValue");
        Integer maxTransactionCount = intValue(row, "maxTransactionCount");
        Integer maxSuccessCount = intValue(row, "maxSuccessCount");
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("elements", parseFrequencyElements(value(row, "elementSet")));
        policy.put("statisticDimension", csvCode("frequencyDimension", defaultIfBlank(value(row, "statDimension"), "ANY_ELEMENT")));
        policy.put("windowUnit", windowUnit);
        policy.put("windowValue", windowValue == null ? 1 : windowValue);
        policy.put("allowedCount", maxTransactionCount == null ? request.getThresholdCount() : maxTransactionCount);
        policy.put("successCount", maxSuccessCount == null ? 0 : maxSuccessCount);
        request.setElementsJson(JsonUtils.toJsonString(policy));
    }

    private boolean hasAnyValue(Map<String, String> row, String... keys) {
        for (String key : keys) {
            if (StringUtils.hasText(value(row, key))) {
                return true;
            }
        }
        return false;
    }

    private List<String> parseFrequencyElements(String value) {
        String text = defaultIfBlank(value, "");
        if (!StringUtils.hasText(text)) {
            return List.of("cardFingerprint", "ip");
        }
        return Stream.of(text.split("[,，/／+、;；]+"))
                .map(String::trim)
                .map(item -> csvCode("frequencyElement", item))
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    private String frequencyElementSetForCsv(Map<String, Object> row) {
        Map<String, Object> policy = parseFrequencyPolicy(firstValue(row, "elementsJson", "elements_json"));
        Object elementValue = policy.get("elements");
        if (!(elementValue instanceof List<?> elements) || elements.isEmpty()) {
            return "-";
        }
        return elements.stream()
                .map(item -> frequencyElementLabel(asString(item)))
                .reduce((left, right) -> left + "、" + right)
                .orElse("-");
    }

    private String frequencyDimensionForCsv(Map<String, Object> row) {
        return frequencyDimensionLabel(asString(parseFrequencyPolicy(firstValue(row, "elementsJson", "elements_json")).get("statisticDimension")));
    }

    private Object frequencyWindowValueForCsv(Map<String, Object> row) {
        Map<String, Object> policy = parseFrequencyPolicy(firstValue(row, "elementsJson", "elements_json"));
        Integer value = asInteger(policy.get("windowValue"));
        return value == null ? frequencyWindowFromSeconds(firstValue(row, "timeWindowSeconds", "time_window_seconds")).windowValue() : value;
    }

    private String frequencyWindowUnitForCsv(Map<String, Object> row) {
        Map<String, Object> policy = parseFrequencyPolicy(firstValue(row, "elementsJson", "elements_json"));
        String unit = asString(policy.get("windowUnit"));
        return frequencyWindowUnitLabel(StringUtils.hasText(unit) ? unit : frequencyWindowFromSeconds(firstValue(row, "timeWindowSeconds", "time_window_seconds")).windowUnit());
    }

    private Object frequencyAllowedCountForCsv(Map<String, Object> row) {
        Object allowedCount = parseFrequencyPolicy(firstValue(row, "elementsJson", "elements_json")).get("allowedCount");
        return allowedCount == null ? firstValue(row, "thresholdCount", "threshold_count") : allowedCount;
    }

    private Object frequencySuccessCountForCsv(Map<String, Object> row) {
        Object successCount = parseFrequencyPolicy(firstValue(row, "elementsJson", "elements_json")).get("successCount");
        return successCount == null ? 0 : successCount;
    }

    private Map<String, Object> parseFrequencyPolicy(Object value) {
        String json = asString(value);
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> policy = JsonUtils.parseObject(json, Map.class);
            return policy == null ? Map.of() : policy;
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }

    private FrequencyWindow frequencyWindowFromSeconds(Object value) {
        Integer seconds = asInteger(value);
        if (seconds == null || seconds <= 0) {
            return new FrequencyWindow(1, "HOUR");
        }
        if (seconds % 86400 == 0) {
            return new FrequencyWindow(seconds / 86400, "DAY");
        }
        if (seconds % 3600 == 0) {
            return new FrequencyWindow(seconds / 3600, "HOUR");
        }
        return new FrequencyWindow(Math.max(seconds / 60, 1), "MINUTE");
    }

    private String stripBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\ufeff' ? value.substring(1) : value;
    }

    private String required(Map<String, String> row, String key) {
        String text = value(row, key);
        if (!StringUtils.hasText(text)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), csvHeader(key) + "不能为空");
        }
        return text;
    }

    private String value(Map<String, String> row, String key) {
        String value = trim(row.get(key));
        if (StringUtils.hasText(value)) {
            return value;
        }
        for (String alias : csvHeaderAliases(key)) {
            value = trim(row.get(alias));
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private Integer intValue(Map<String, String> row, String key) {
        String text = value(row, key);
        return StringUtils.hasText(text) ? Integer.valueOf(text) : null;
    }

    private Integer statusValue(Map<String, String> row, String key) {
        String text = csvCode("status", value(row, key));
        return StringUtils.hasText(text) ? Integer.valueOf(text) : null;
    }

    private BigDecimal decimalValue(Map<String, String> row, String key) {
        String text = value(row, key);
        return StringUtils.hasText(text) ? new BigDecimal(text) : null;
    }

    private LocalDateTime localDateTimeValue(Map<String, String> row, String key) {
        String text = value(row, key);
        return StringUtils.hasText(text) ? LocalDateTime.parse(text.replace(" ", "T")) : null;
    }

    private void writeExcel(RiskFunctionDefinition definition,
                            List<Map<String, Object>> rows,
                            String querySummary,
                            HttpServletResponse response) {
        writeRiskExcel(definition, rows, querySummary, "excel.risk.exportSuffix", response);
    }

    private void writeTemplateExcel(RiskFunctionDefinition definition,
                                    List<Map<String, Object>> rows,
                                    HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        String templateHint = excelI18nMessageResolver.resolve("excel.risk.templateHint", locale);
        writeRiskExcel(definition, rows, templateHint, "excel.risk.templateSuffix", response);
    }

    private void writeRiskExcel(RiskFunctionDefinition definition,
                                List<Map<String, Object>> rows,
                                String querySummary,
                                String titleSuffixKey,
                                HttpServletResponse response) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        String exportTitle = riskExcelTitle(definition, locale, titleSuffixKey);
        excelExportService.exportDynamic(
                ExcelDynamicExportRequest.builder()
                        .fileName(exportTitle + "_" + timestampSuffix())
                        .sheetName(exportTitle)
                        .title(exportTitle)
                        .operator(currentOperatorName())
                        .exportTime(LocalDateTime.now())
                        .locale(locale)
                        .querySummary(querySummary)
                        .columns(excelColumns(definition))
                        .dataList(rows)
                        .build(),
                response
        );
    }

    /**
     * 生成风控导出或模板标题。标题后缀走 Excel 组件国际化，功能名保持当前业务配置名称。
     *
     * @param definition 风控功能定义
     * @param locale     当前导出语言
     * @param suffixKey  标题后缀国际化 key
     * @return Excel 文件名、Sheet 名和标题使用的基础文案
     */
    private String riskExcelTitle(RiskFunctionDefinition definition, Locale locale, String suffixKey) {
        String suffix = excelI18nMessageResolver.resolve(suffixKey, locale);
        return definition.getFunctionName() + suffix;
    }

    private String timestampSuffix() {
        return java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
    }

    private List<ExcelDynamicColumnDefinition> excelColumns(RiskFunctionDefinition definition) {
        return csvColumns(definition).stream()
                .map(column -> new ExcelDynamicColumnDefinition(column.key(), column.header(), excelColumnWidth(column.key()), excelColumnAlign(column.key())))
                .toList();
    }

    private int excelColumnWidth(String key) {
        return switch (key) {
            case "merchantScope", "status", "riskLevel", "decisionAction", "validityType", "regionMatchLevel" -> 14;
            case "validityDays", "currency", "amountMin", "amountMax", "thresholdCount", "windowValue", "maxTransactionCount", "maxSuccessCount" -> 12;
            case "merchantId", "matchValueStart", "matchValueEnd", "phone", "cardNo", "cardFingerprint" -> 22;
            case "remark", "elementsJson", "elementSet", "amountCondition", "paymentMethodCardBrand" -> 32;
            case "effectiveTime", "expireTime" -> 20;
            default -> 18;
        };
    }

    private HorizontalAlignment excelColumnAlign(String key) {
        return switch (key) {
            case "validityDays", "amountMin", "amountMax", "timeWindowSeconds", "thresholdCount", "windowValue", "maxTransactionCount", "maxSuccessCount" -> HorizontalAlignment.RIGHT;
            case "merchantScope", "status", "riskLevel", "decisionAction", "validityType", "regionMatchLevel", "currency", "windowUnit", "statDimension" -> HorizontalAlignment.CENTER;
            default -> HorizontalAlignment.LEFT;
        };
    }

    private String buildListQuerySummary(RiskFunctionDefinition definition, RiskDTOs.RiskListQueryRequest request) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        StringBuilder builder = new StringBuilder();
        if (showListMerchantDimension(definition)) {
            appendCondition(builder, csvHeader("merchantScope"), csvLabel("merchantScope", request.getMerchantScope()));
            appendCondition(builder, csvHeader("merchantId"), request.getMerchantId());
        }
        appendCondition(builder, matchValueCsvHeader(definition), request.getMatchValue());
        appendCondition(builder, csvHeader("countryAlpha2"), request.getCountryAlpha2());
        appendCondition(builder, csvHeader("status"), request.getStatus() == null ? null : csvLabel("status", request.getStatus()));
        return builder.isEmpty() ? excelI18nMessageResolver.resolve("excel.common.noCondition", locale) : builder.toString();
    }

    private String buildRuleQuerySummary(RiskFunctionDefinition definition, RiskDTOs.RiskRuleQueryRequest request) {
        Locale locale = excelLocaleResolver.resolveCurrentLocale();
        StringBuilder builder = new StringBuilder();
        if (isSourceUrlRule(definition)) {
            appendCondition(builder, csvHeader("merchantId"), request.getMerchantId());
            appendCondition(builder, csvHeader("sourceUrl"), request.getSourceUrl());
            appendCondition(builder, csvHeader("sourceHost"), defaultIfBlank(request.getSourceHost(), request.getMatchValue()));
            appendCondition(builder, csvHeader("status"), request.getStatus() == null ? null : csvLabel("status", request.getStatus()));
            return builder.isEmpty() ? excelI18nMessageResolver.resolve("excel.common.noCondition", locale) : builder.toString();
        }
        appendCondition(builder, csvHeader("merchantScope"), csvLabel("merchantScope", request.getMerchantScope()));
        appendCondition(builder, csvHeader("merchantId"), request.getMerchantId());
        appendCondition(builder, csvHeader("ruleName"), request.getRuleName());
        if (isThreeDsRule(definition)) {
            appendCondition(builder, csvHeader("ruleType"), csvLabel("threeDsRuleType", request.getRuleType()));
            appendCondition(builder, csvHeader("channelCode"), request.getChannelCode());
            appendCondition(builder, csvHeader("paymentMethodCardBrand"), threeDsPaymentScopeForCsv(request.getPaymentMethod(), request.getCardBrand()));
            appendCondition(builder, csvHeader("currency"), request.getCurrency());
            appendCondition(builder, csvHeader("triggerAction"), csvLabel("threeDsTriggerAction", request.getTriggerAction()));
            appendCondition(builder, csvHeader("status"), request.getStatus() == null ? null : csvLabel("status", request.getStatus()));
            return builder.isEmpty() ? excelI18nMessageResolver.resolve("excel.common.noCondition", locale) : builder.toString();
        }
        appendCondition(builder, csvHeader("ruleMatchValue"), request.getMatchValue());
        appendCondition(builder, csvHeader("limitType"), csvLabel("limitType", request.getLimitType()));
        appendCondition(builder, csvHeader("currency"), request.getCurrency());
        appendCondition(builder, csvHeader("status"), request.getStatus() == null ? null : csvLabel("status", request.getStatus()));
        return builder.isEmpty() ? excelI18nMessageResolver.resolve("excel.common.noCondition", locale) : builder.toString();
    }

    private void appendCondition(StringBuilder builder, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append("; ");
        }
        builder.append(label).append("=").append(value.trim());
    }

    private List<Map<String, Object>> exportRows(RiskFunctionDefinition definition, List<Map<String, Object>> rows) {
        return rows.stream().map(row -> toCsvRow(definition, row)).toList();
    }

    private Map<String, Object> toCsvRow(RiskFunctionDefinition definition, Map<String, Object> source) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (CsvColumn column : csvColumns(definition)) {
            row.put(column.key(), column.extractor().apply(source));
        }
        return row;
    }

    private List<CsvColumn> csvColumns(RiskFunctionDefinition definition) {
        if (definition.isRuleFunction()) {
            return ruleCsvColumns(definition);
        }
        List<CsvColumn> columns = new ArrayList<>();
        if (showListMerchantDimension(definition)) {
            columns.add(column("merchantScope", csvHeader("merchantScope"), row -> csvLabel("merchantScope", firstValue(row, "merchantScope", "merchant_scope"))));
            columns.add(column("merchantId", csvHeader("merchantId"), "merchant_id"));
        }
        if (definition.isRegionFunction()) {
            columns.add(column("regionMatchLevel", csvHeader("regionMatchLevel"), row -> csvLabel("regionMatchLevel", firstValue(row, "regionMatchLevel", "region_match_level"))));
            columns.add(column("countryAlpha2", csvHeader("countryAlpha2"), row -> countryAlpha2ForCsv(row)));
            columns.add(column("stateProvinceName", csvHeader("stateProvinceName"), "state_province_name"));
            columns.add(column("cityName", csvHeader("cityName"), "city_name"));
        } else if (hasRangeFields(definition)) {
            String startHeader = csvHeader("cardBin".equals(definition.getFunctionCode()) ? "matchValueStartBin" : "matchValueStartIp");
            String endHeader = csvHeader("cardBin".equals(definition.getFunctionCode()) ? "matchValueEndBin" : "matchValueEndIp");
            columns.add(column("matchValueStart", startHeader, "match_value_start"));
            columns.add(column("matchValueEnd", endHeader, "match_value_end"));
        } else if (hasCountryFields(definition)) {
            columns.add(column("countryAlpha2", csvHeader("countryAlpha2"), row -> countryAlpha2ForCsv(row)));
        } else {
            columns.add(column("matchValuePlain", matchValueCsvHeader(definition), "match_value_masked"));
        }
        columns.add(column("riskLevel", csvHeader("riskLevel"), row -> csvLabel("riskLevel", firstValue(row, "riskLevel", "risk_level"))));
        columns.add(column("decisionAction", csvHeader("decisionAction"), row -> csvLabel("decisionAction", firstValue(row, "decisionAction", "decision_action"))));
        columns.add(column("validityType", csvHeader("validityType"), row -> csvLabel("validityType", firstValue(row, "validityType", "validity_type"))));
        columns.add(column("validityDays", csvHeader("validityDays"), "validity_days"));
        columns.add(column("status", csvHeader("status"), row -> csvLabel("status", firstValue(row, "status"))));
        columns.add(column("remark", csvHeader("remark"), "remark"));
        return columns;
    }

    private List<CsvColumn> ruleCsvColumns(RiskFunctionDefinition definition) {
        if (isSourceUrlRule(definition)) {
            return sourceUrlCsvColumns();
        }
        List<CsvColumn> columns = new ArrayList<>();
        columns.add(column("ruleName", csvHeader("ruleName"), "rule_name"));
        columns.add(column("merchantScope", csvHeader("merchantScope"), row -> csvLabel("merchantScope", firstValue(row, "merchantScope", "merchant_scope"))));
        columns.add(column("merchantId", csvHeader("merchantId"), "merchant_id"));
        if (isThreeDsRule(definition)) {
            columns.add(column("ruleType", csvHeader("ruleType"), row -> csvLabel("threeDsRuleType", firstValue(row, "ruleType", "rule_type"))));
            columns.add(column("channelCode", csvHeader("channelCode"), "channel_code"));
            columns.add(column("paymentMethodCardBrand", csvHeader("paymentMethodCardBrand"), row -> threeDsPaymentScopeForCsv(firstValue(row, "paymentMethod", "payment_method"), firstValue(row, "cardBrand", "card_brand"))));
            columns.add(column("amountCondition", csvHeader("amountCondition"), this::threeDsAmountConditionForCsv));
            columns.add(column("riskCondition", csvHeader("riskCondition"), row -> csvLabel("threeDsRiskCondition", firstValue(row, "riskCondition", "risk_condition"))));
            columns.add(column("triggerAction", csvHeader("triggerAction"), row -> csvLabel("threeDsTriggerAction", firstValue(row, "triggerAction", "trigger_action"))));
            columns.add(column("priority", csvHeader("priority"), "priority"));
        }
        if (!FUNCTION_MERCHANT_LIMIT.equals(definition.getFunctionCode()) && !"frequency".equals(definition.getFunctionCode()) && !isThreeDsRule(definition)) {
            columns.add(column("matchValue", ruleMatchCsvHeader(definition), "match_value"));
        }
        if (FUNCTION_MERCHANT_LIMIT.equals(definition.getFunctionCode())) {
            columns.add(column("limitType", csvHeader("limitType"), row -> csvLabel("limitType", firstValue(row, "limitType", "limit_type"))));
            columns.add(column("limitAmount", csvHeader("limitAmount"), this::merchantLimitAmountForCsv));
        }
        if ("frequency".equals(definition.getFunctionCode())) {
            columns.add(column("statDimension", csvHeader("statDimension"), this::frequencyDimensionForCsv));
            columns.add(column("elementSet", csvHeader("elementSet"), this::frequencyElementSetForCsv));
            columns.add(column("windowValue", csvHeader("windowValue"), this::frequencyWindowValueForCsv));
            columns.add(column("windowUnit", csvHeader("windowUnit"), this::frequencyWindowUnitForCsv));
            columns.add(column("maxTransactionCount", csvHeader("maxTransactionCount"), this::frequencyAllowedCountForCsv));
            columns.add(column("maxSuccessCount", csvHeader("maxSuccessCount"), this::frequencySuccessCountForCsv));
        }
        columns.add(column("riskLevel", csvHeader("riskLevel"), row -> csvLabel("riskLevel", firstValue(row, "riskLevel", "risk_level"))));
        columns.add(column("decisionAction", csvHeader("decisionAction"), row -> csvLabel("decisionAction", firstValue(row, "decisionAction", "decision_action"))));
        columns.add(column("effectiveTime", csvHeader("effectiveTime"), "effective_time"));
        columns.add(column("expireTime", csvHeader("expireTime"), "expire_time"));
        columns.add(column("status", csvHeader("status"), row -> csvLabel("status", firstValue(row, "status"))));
        columns.add(column("remark", csvHeader("remark"), "remark"));
        return columns;
    }

    private List<CsvColumn> sourceUrlCsvColumns() {
        List<CsvColumn> columns = new ArrayList<>();
        columns.add(column("merchantId", csvHeader("merchantId"), "merchant_id"));
        columns.add(column("sourceUrl", csvHeader("sourceUrl"), "source_url"));
        columns.add(column("sourceHost", csvHeader("sourceHost"), "source_host"));
        columns.add(column("riskLevel", csvHeader("riskLevel"), row -> csvLabel("riskLevel", firstValue(row, "riskLevel", "risk_level"))));
        columns.add(column("decisionAction", csvHeader("decisionAction"), row -> csvLabel("decisionAction", firstValue(row, "decisionAction", "decision_action"))));
        columns.add(column("effectiveTime", csvHeader("effectiveTime"), "effective_time"));
        columns.add(column("expireTime", csvHeader("expireTime"), "expire_time"));
        columns.add(column("status", csvHeader("status"), row -> csvLabel("status", firstValue(row, "status"))));
        columns.add(column("remark", csvHeader("remark"), "remark"));
        return columns;
    }

    private String ruleMatchCsvHeader(RiskFunctionDefinition definition) {
        return switch (definition.getFunctionCode()) {
            case "sourceUrl" -> csvHeader("sourceUrl");
            default -> csvHeader("ruleMatchValue");
        };
    }

    private String frequencyPolicyForCsv(Object value) {
        String json = asString(value);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            Map<String, Object> policy = JsonUtils.parseObject(json, Map.class);
            if (policy == null || policy.isEmpty()) {
                return null;
            }
            Object elementValue = policy.get("elements");
            String elements = elementValue instanceof List<?> list && !list.isEmpty()
                    ? list.stream().map(item -> frequencyElementLabel(asString(item))).toList().toString()
                    : "-";
            String dimension = frequencyDimensionLabel(asString(policy.get("statisticDimension")));
            String unit = frequencyWindowUnitLabel(asString(policy.get("windowUnit")));
            Integer windowValue = asInteger(policy.get("windowValue"));
            Integer allowedCount = asInteger(policy.get("allowedCount"));
            Integer successCount = asInteger(policy.get("successCount"));
            boolean english = isEnglishLocale();
            return String.format(
                    "%s / %s / %s%s / %s%s / %s%s",
                    elements.replace("[", "").replace("]", ""),
                    dimension,
                    windowValue == null ? "-" : windowValue,
                    unit,
                    english ? "Allowed " : "允许",
                    allowedCount == null ? "-" : allowedCount,
                    english ? "Success " : "成功",
                    successCount == null ? 0 : successCount
            );
        } catch (RuntimeException exception) {
            return json;
        }
    }

    private String frequencyElementLabel(String value) {
        boolean english = isEnglishLocale();
        return switch (defaultIfBlank(value, "")) {
            case "cardNo" -> english ? "Card Number" : "卡号";
            case "cardFingerprint" -> english ? "Card Fingerprint" : "卡指纹";
            case "ip" -> english ? "IP Address" : "IP地址";
            case "email" -> english ? "Email" : "邮箱";
            case "phone" -> english ? "Phone" : "手机号";
            case "customerId" -> english ? "Customer ID" : "Customer ID";
            case "deviceFingerprint" -> english ? "Device Fingerprint" : "设备指纹";
            default -> value;
        };
    }

    private String frequencyDimensionLabel(String value) {
        boolean english = isEnglishLocale();
        return switch (defaultIfBlank(value, "ANY_ELEMENT")) {
            case "ELEMENT_COMBINATION" -> english ? "Element Combination" : "元素组合";
            default -> english ? "Any Element" : "任一元素";
        };
    }

    private String frequencyWindowUnitLabel(String value) {
        boolean english = isEnglishLocale();
        return switch (defaultIfBlank(value, "MINUTE")) {
            case "HOUR" -> english ? "hour(s)" : "小时";
            case "DAY" -> english ? "day(s)" : "天";
            default -> english ? "minute(s)" : "分钟";
        };
    }

    private CsvColumn column(String key, String header, String sourceKey) {
        return column(key, header, row -> firstValue(row, key, sourceKey));
    }

    private CsvColumn column(String key, String header, Function<Map<String, Object>, Object> extractor) {
        return new CsvColumn(key, header, extractor);
    }

    private Object firstValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String countryAlpha2ForCsv(Map<String, Object> row) {
        String alpha2 = asString(firstValue(row, "countryAlpha2", "country_alpha2"));
        if (StringUtils.hasText(alpha2)) {
            return alpha2;
        }
        String alpha3 = asString(firstValue(row, "countryAlpha3", "country_alpha3"));
        if (!StringUtils.hasText(alpha3)) {
            return null;
        }
        Map<String, Object> country = riskManagementMapper.selectCountryOptionByAlpha3(alpha3);
        return asString(country == null ? null : country.get("value"));
    }

    private String matchValueCsvHeader(RiskFunctionDefinition definition) {
        String code = definition.getFunctionCode();
        if ("cardNo".equals(code) || "card".equals(code)) {
            return csvHeader("cardNo");
        }
        if ("cardFingerprint".equals(code)) {
            return csvHeader("cardFingerprint");
        }
        if ("cardholderName".equals(code)) {
            return csvHeader("cardholderName");
        }
        if ("legalPerson".equals(code)) {
            return csvHeader("legalPerson");
        }
        if ("enterprise".equals(code)) {
            return csvHeader("enterprise");
        }
        if ("merchantBillingAddress".equals(code)) {
            return csvHeader("merchantBillingAddress");
        }
        if ("phone".equals(code)) {
            return csvHeader("phone");
        }
        if ("email".equals(code)) {
            return csvHeader("email");
        }
        if ("emailUsername".equals(code)) {
            return csvHeader("emailUsername");
        }
        if ("emailDomain".equals(code)) {
            return csvHeader("emailDomain");
        }
        if ("billingAddress".equals(code)) {
            return csvHeader("billingAddress");
        }
        if ("billingZip".equals(code)) {
            return csvHeader("billingZip");
        }
        if ("shippingAddress".equals(code)) {
            return csvHeader("shippingAddress");
        }
        if ("shippingZip".equals(code)) {
            return csvHeader("shippingZip");
        }
        if ("deviceFingerprint".equals(code)) {
            return csvHeader("deviceFingerprint");
        }
        if ("merchant".equals(code)) {
            return csvHeader("merchantId");
        }
        if ("sourceUrl".equals(code)) {
            return csvHeader("sourceUrl");
        }
        return csvHeader("matchValuePlain");
    }

    private String csvHeader(String key) {
        boolean english = isEnglishLocale();
        return switch (key) {
            case "merchantScope" -> english ? "Scope" : "生效范围";
            case "merchantId" -> english ? "Merchant ID" : "商户号";
            case "ruleName" -> english ? "Rule Name" : "规则名称";
            case "matchValuePlain" -> english ? "Match Value" : "匹配值";
            case "matchValueStartBin" -> english ? "Start BIN" : "起始BIN";
            case "matchValueEndBin" -> english ? "End BIN" : "截止BIN";
            case "matchValueStartIp" -> english ? "Start IP" : "起始IP";
            case "matchValueEndIp" -> english ? "End IP" : "截止IP";
            case "cardNo" -> english ? "Card Number" : "卡号";
            case "cardFingerprint" -> english ? "Card Fingerprint" : "卡指纹";
            case "cardholderName" -> english ? "Cardholder Name" : "持卡人姓名";
            case "legalPerson" -> english ? "Legal Person" : "法人";
            case "enterprise" -> english ? "Enterprise" : "企业";
            case "merchantBillingAddress" -> english ? "Merchant Billing Address" : "商户账单地址";
            case "phone" -> english ? "Phone Number" : "手机号";
            case "email" -> english ? "Email Address" : "邮箱地址";
            case "emailUsername" -> english ? "Email Username" : "邮箱用户名";
            case "emailDomain" -> english ? "Email Domain" : "邮箱域名";
            case "billingAddress" -> english ? "Billing Address" : "账单地址";
            case "billingZip" -> english ? "Billing Postal Code" : "账单邮编";
            case "shippingAddress" -> english ? "Shipping Address" : "收货地址";
            case "shippingZip" -> english ? "Shipping Postal Code" : "收货邮编";
            case "deviceFingerprint" -> english ? "Device Fingerprint" : "设备指纹";
            case "sourceUrl" -> english ? "Source URL" : "来源网址";
            case "sourceHost" -> english ? "Source Host" : "来源网址Host";
            case "issuerCountry" -> english ? "Issuer Country/Region" : "发卡行国家/地区";
            case "cardBin" -> english ? "Card BIN Range" : "卡BIN区间";
            case "ruleType" -> english ? "Rule Type" : "规则类型";
            case "channelCode" -> english ? "Channel Code" : "渠道编码";
            case "paymentMethod" -> english ? "Payment Method" : "支付方式";
            case "paymentMethodCardBrand" -> english ? "Payment Method / Card Brand" : "支付方式/卡品牌";
            case "amountCondition" -> english ? "Amount Condition" : "金额条件";
            case "amountMatchType" -> english ? "Amount Match Type" : "金额匹配类型";
            case "riskCondition" -> english ? "Risk Condition" : "风险条件";
            case "triggerAction" -> english ? "Trigger Action" : "触发动作";
            case "priority" -> english ? "Priority" : "优先级";
            case "matchMode" -> english ? "Match Mode" : "匹配方式";
            case "ruleMatchValue" -> english ? "Rule Match Value" : "规则匹配值";
            case "limitType" -> english ? "Limit Type" : "限额类型";
            case "limitAmount" -> english ? "Limit Amount" : "限额金额";
            case "amountMin" -> english ? "Minimum Amount" : "最小金额";
            case "amountMax" -> english ? "Maximum Amount" : "最大金额";
            case "currency" -> english ? "Currency" : "币种";
            case "timeWindowSeconds" -> english ? "Time Window Seconds" : "时间窗口秒数";
            case "thresholdCount" -> english ? "Threshold Count" : "阈值次数";
            case "elementsJson" -> english ? "Elements JSON" : "组合元素JSON";
            case "frequencyPolicy" -> english ? "Frequency Policy" : "频率策略";
            case "statDimension" -> english ? "Statistic Dimension" : "统计维度";
            case "elementSet" -> english ? "Element Set" : "元素集合";
            case "windowValue" -> english ? "Time Window" : "时间窗口";
            case "windowUnit" -> english ? "Window Unit" : "窗口单位";
            case "maxTransactionCount" -> english ? "Max Transactions" : "最大交易次数";
            case "maxSuccessCount" -> english ? "Max Successes" : "最大成功次数";
            case "effectiveTime" -> english ? "Effective Time" : "生效时间";
            case "expireTime" -> english ? "Expire Time" : "失效时间";
            case "regionMatchLevel" -> english ? "Region Level" : "区域级别";
            case "countryAlpha2" -> english ? "Country/Region" : "国家/地区";
            case "stateProvinceName" -> english ? "State/Province" : "州/省";
            case "cityName" -> english ? "City" : "城市";
            case "riskLevel" -> english ? "Risk Level" : "风险等级";
            case "decisionAction" -> english ? "Decision Action" : "决策动作";
            case "validityType" -> english ? "Validity Type" : "有效期类型";
            case "validityDays" -> english ? "Validity Days" : "有效天数";
            case "sourceType" -> english ? "Source" : "来源";
            case "status" -> english ? "Status" : "状态";
            case "remark" -> english ? "Remark" : "备注";
            default -> key;
        };
    }

    private String csvLabel(String type, Object value) {
        String text = asString(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim().toUpperCase(Locale.ROOT);
        boolean english = isEnglishLocale();
        return switch (type) {
            case "merchantScope" -> switch (normalized) {
                case "GLOBAL", "全局风控", "GLOBAL RISK" -> english ? "Global Risk" : "全局风控";
                case "MERCHANT", "商户风控", "MERCHANT RISK" -> english ? "Merchant Risk" : "商户风控";
                default -> text;
            };
            case "riskLevel" -> switch (normalized) {
                case "LOW", "低风险", "LOW RISK" -> english ? "Low Risk" : "低风险";
                case "MEDIUM", "中风险", "MEDIUM RISK" -> english ? "Medium Risk" : "中风险";
                case "HIGH", "高风险", "HIGH RISK" -> english ? "High Risk" : "高风险";
                case "CRITICAL", "严重风险", "CRITICAL RISK" -> english ? "Critical Risk" : "严重风险";
                default -> text;
            };
            case "decisionAction" -> switch (normalized) {
                case "PASS", "通过", "ALLOW" -> english ? "Pass" : "通过";
                case "REJECT", "拒绝", "DECLINE" -> english ? "Reject" : "拒绝";
                case "REVIEW", "人工复核", "MANUAL REVIEW" -> english ? "Manual Review" : "人工复核";
                default -> text;
            };
            case "validityType" -> switch (normalized) {
                case VALIDITY_SUPER_LONG, "超长期", "NEVER EXPIRE", "NEVER EXPIRES" -> english ? "Never Expires" : "超长期";
                case VALIDITY_LONG, "长期", "LONG TERM" -> english ? "Long Term" : "长期";
                case VALIDITY_LIMITED, "限定", "限定有效期", "LIMITED VALIDITY" -> english ? "Limited Validity" : "限定有效期";
                default -> text;
            };
            case "sourceType" -> switch (normalized) {
                case SOURCE_MANUAL, "手工录入", "MANUAL ENTRY" -> english ? "Manual Entry" : "手工录入";
                case SOURCE_IMPORT, "批量导入", "BATCH IMPORT" -> english ? "Batch Import" : "批量导入";
                case "SYSTEM", "系统生成", "SYSTEM GENERATED" -> english ? "System Generated" : "系统生成";
                default -> text;
            };
            case "status" -> switch (normalized) {
                case "1", "启用", "ENABLED", "ENABLE" -> english ? "Enabled" : "启用";
                case "0", "停用", "禁用", "DISABLED", "DISABLE" -> english ? "Disabled" : "停用";
                default -> text;
            };
            case "regionMatchLevel" -> switch (normalized) {
                case "COUNTRY", "国家/地区", "COUNTRY/REGION" -> english ? "Country/Region" : "国家/地区";
                case "STATE", "州/省", "STATE/PROVINCE" -> english ? "State/Province" : "州/省";
                case "CITY", "城市" -> english ? "City" : "城市";
                default -> text;
            };
            case "matchMode" -> switch (normalized) {
                case "EXACT", "精确匹配", "EXACT MATCH" -> english ? "Exact Match" : "精确匹配";
                case "DOMAIN", "域名匹配", "DOMAIN MATCH" -> english ? "Domain Match" : "域名匹配";
                case "CONTAINS", "包含匹配", "CONTAINS MATCH" -> english ? "Contains Match" : "包含匹配";
                case "REGEX", "正则匹配", "REGEX MATCH" -> english ? "Regex Match" : "正则匹配";
                default -> text;
            };
            case "limitType" -> switch (normalized) {
                case "SINGLE_MIN", "单笔最低限额", "SINGLE MINIMUM" -> english ? "Single Minimum" : "单笔最低限额";
                case "SINGLE_MAX", "单笔最高限额", "SINGLE MAXIMUM" -> english ? "Single Maximum" : "单笔最高限额";
                case "DAILY", "日限额", "DAILY LIMIT" -> english ? "Daily Limit" : "日限额";
                case "WEEKLY", "周限额", "WEEKLY LIMIT" -> english ? "Weekly Limit" : "周限额";
                case "MONTHLY", "月限额", "MONTHLY LIMIT" -> english ? "Monthly Limit" : "月限额";
                default -> text;
            };
            case "threeDsRuleType" -> switch (normalized) {
                case "RISK_STRATEGY", "风险策略", "RISK STRATEGY" -> english ? "Risk Strategy" : "风险策略";
                case "EXEMPTION_STRATEGY", "豁免策略", "EXEMPTION STRATEGY" -> english ? "Exemption Strategy" : "豁免策略";
                case "CHANNEL_POLICY", "渠道策略", "CHANNEL POLICY" -> english ? "Channel Policy" : "渠道策略";
                default -> text;
            };
            case "paymentMethod" -> switch (normalized) {
                case "ALL", "全部", "ALL PAYMENT METHODS" -> english ? "All" : "全部";
                case "BANK_CARD", "卡支付", "CARD PAYMENT", "BANK CARD" -> english ? "Card Payment" : "卡支付";
                case "PAYPAL" -> "PayPal";
                case "APPLE_PAY", "APPLE PAY" -> "Apple Pay";
                default -> text;
            };
            case "cardBrand" -> switch (normalized) {
                case "ALL", "全部", "ALL CARD BRANDS" -> english ? "All" : "全部";
                case "VISA" -> "Visa";
                case "MASTERCARD", "MASTER" -> "Mastercard";
                case "JCB" -> "JCB";
                case "DINERS_CLUB", "DINERS CLUB" -> "Diners Club";
                case "AMEX", "AMERICAN EXPRESS" -> "American Express";
                case "DISCOVER" -> "Discover";
                case "UNIONPAY", "UNION PAY" -> "UnionPay";
                case "MAESTRO" -> "Maestro";
                default -> text;
            };
            case "threeDsAmountMatchType" -> switch (normalized) {
                case "ALL", "全部金额", "ANY AMOUNT" -> english ? "Any Amount" : "全部金额";
                case "GE", "大于等于", "GREATER OR EQUAL" -> english ? "Greater or Equal" : "大于等于";
                case "LE", "小于等于", "LESS OR EQUAL" -> english ? "Less or Equal" : "小于等于";
                case "BETWEEN", "区间", "BETWEEN RANGE" -> english ? "Between" : "区间";
                default -> text;
            };
            case "threeDsRiskCondition" -> switch (normalized) {
                case "ANY", "任意风险", "ANY RISK" -> english ? "Any Risk" : "任意风险";
                case "LOW_AND_ABOVE", "低风险及以上", "LOW AND ABOVE" -> english ? "Low and Above" : "低风险及以上";
                case "MEDIUM_AND_ABOVE", "中风险及以上", "MEDIUM AND ABOVE" -> english ? "Medium and Above" : "中风险及以上";
                case "HIGH_AND_ABOVE", "高风险及以上", "HIGH AND ABOVE" -> english ? "High and Above" : "高风险及以上";
                case "CRITICAL_ONLY", "仅严重风险", "CRITICAL ONLY" -> english ? "Critical Only" : "仅严重风险";
                default -> text;
            };
            case "threeDsTriggerAction" -> switch (normalized) {
                case "FORCE_3DS", "强制3DS", "FORCE 3DS" -> english ? "Force 3DS" : "强制3DS";
                case "SKIP_3DS", "跳过3DS", "SKIP 3DS" -> english ? "Skip 3DS" : "跳过3DS";
                case "FOLLOW_DEFAULT", "跟随默认", "FOLLOW DEFAULT" -> english ? "Follow Default" : "跟随默认";
                default -> text;
            };
            case "frequencyDimension" -> switch (normalized) {
                case "ELEMENT_COMBINATION", "元素组合", "ELEMENT COMBINATION" -> english ? "Element Combination" : "元素组合";
                case "ANY_ELEMENT", "任一元素", "ANY ELEMENT" -> english ? "Any Element" : "任一元素";
                default -> text;
            };
            case "frequencyWindowUnit" -> switch (normalized) {
                case "MINUTE", "分钟", "MINUTE(S)", "MINUTES" -> english ? "minute(s)" : "分钟";
                case "HOUR", "小时", "HOUR(S)", "HOURS" -> english ? "hour(s)" : "小时";
                case "DAY", "天", "DAY(S)", "DAYS" -> english ? "day(s)" : "天";
                default -> text;
            };
            case "frequencyElement" -> frequencyElementLabel(text);
            default -> text;
        };
    }

    private String csvCode(String type, String value) {
        String text = trim(value);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.toUpperCase(Locale.ROOT);
        return switch (type) {
            case "merchantScope" -> switch (normalized) {
                case "GLOBAL", "全局风控", "GLOBAL RISK" -> "GLOBAL";
                case "MERCHANT", "商户风控", "MERCHANT RISK" -> "MERCHANT";
                default -> text;
            };
            case "riskLevel" -> switch (normalized) {
                case "LOW", "低风险", "LOW RISK" -> "LOW";
                case "MEDIUM", "中风险", "MEDIUM RISK" -> "MEDIUM";
                case "HIGH", "高风险", "HIGH RISK" -> "HIGH";
                case "CRITICAL", "严重风险", "CRITICAL RISK" -> "CRITICAL";
                default -> text;
            };
            case "decisionAction" -> switch (normalized) {
                case "PASS", "通过", "ALLOW" -> "PASS";
                case "REJECT", "拒绝", "DECLINE" -> "REJECT";
                case "REVIEW", "人工复核", "MANUAL REVIEW" -> "REVIEW";
                default -> text;
            };
            case "validityType" -> switch (normalized) {
                case VALIDITY_SUPER_LONG, "超长期", "NEVER EXPIRE", "NEVER EXPIRES" -> VALIDITY_SUPER_LONG;
                case VALIDITY_LONG, "长期", "LONG TERM" -> VALIDITY_LONG;
                case VALIDITY_LIMITED, "限定", "限定有效期", "LIMITED VALIDITY" -> VALIDITY_LIMITED;
                default -> text;
            };
            case "status" -> switch (normalized) {
                case "1", "启用", "ENABLED", "ENABLE" -> "1";
                case "0", "停用", "禁用", "DISABLED", "DISABLE" -> "0";
                default -> text;
            };
            case "regionMatchLevel" -> switch (normalized) {
                case "COUNTRY", "国家/地区", "COUNTRY/REGION" -> "COUNTRY";
                case "STATE", "州/省", "STATE/PROVINCE" -> "STATE";
                case "CITY", "城市" -> "CITY";
                default -> text;
            };
            case "matchMode" -> switch (normalized) {
                case "EXACT", "精确匹配", "EXACT MATCH" -> "EXACT";
                case "DOMAIN", "域名匹配", "DOMAIN MATCH" -> "DOMAIN";
                case "CONTAINS", "包含匹配", "CONTAINS MATCH" -> "CONTAINS";
                case "REGEX", "正则匹配", "REGEX MATCH" -> "REGEX";
                default -> text;
            };
            case "limitType" -> switch (normalized) {
                case "SINGLE_MIN", "单笔最低限额", "SINGLE MINIMUM" -> "SINGLE_MIN";
                case "SINGLE_MAX", "单笔最高限额", "SINGLE MAXIMUM" -> "SINGLE_MAX";
                case "DAILY", "日限额", "DAILY LIMIT" -> "DAILY";
                case "WEEKLY", "周限额", "WEEKLY LIMIT" -> "WEEKLY";
                case "MONTHLY", "月限额", "MONTHLY LIMIT" -> "MONTHLY";
                default -> text;
            };
            case "threeDsRuleType" -> switch (normalized) {
                case "RISK_STRATEGY", "风险策略", "RISK STRATEGY" -> "RISK_STRATEGY";
                case "EXEMPTION_STRATEGY", "豁免策略", "EXEMPTION STRATEGY" -> "EXEMPTION_STRATEGY";
                case "CHANNEL_POLICY", "渠道策略", "CHANNEL POLICY" -> "CHANNEL_POLICY";
                default -> text;
            };
            case "paymentMethod" -> switch (normalized) {
                case "ALL", "全部", "ALL PAYMENT METHODS" -> "ALL";
                case "BANK_CARD", "卡支付", "CARD PAYMENT", "BANK CARD" -> "BANK_CARD";
                case "PAYPAL" -> "PAYPAL";
                case "APPLE_PAY", "APPLE PAY" -> "APPLE_PAY";
                default -> text;
            };
            case "cardBrand" -> switch (normalized) {
                case "ALL", "全部", "ALL CARD BRANDS" -> "ALL";
                case "VISA" -> "VISA";
                case "MASTERCARD", "MASTER" -> "MASTERCARD";
                case "JCB" -> "JCB";
                case "DINERS_CLUB", "DINERS CLUB" -> "DINERS_CLUB";
                case "AMEX", "AMERICAN EXPRESS" -> "AMEX";
                case "DISCOVER" -> "DISCOVER";
                case "UNIONPAY", "UNION PAY" -> "UNIONPAY";
                case "MAESTRO" -> "MAESTRO";
                default -> text;
            };
            case "threeDsAmountMatchType" -> switch (normalized) {
                case "ALL", "全部金额", "ANY AMOUNT" -> "ALL";
                case "GE", "大于等于", "GREATER OR EQUAL" -> "GE";
                case "LE", "小于等于", "LESS OR EQUAL" -> "LE";
                case "BETWEEN", "区间", "BETWEEN RANGE" -> "BETWEEN";
                default -> text;
            };
            case "threeDsRiskCondition" -> switch (normalized) {
                case "ANY", "任意风险", "ANY RISK" -> "ANY";
                case "LOW_AND_ABOVE", "低风险及以上", "LOW AND ABOVE" -> "LOW_AND_ABOVE";
                case "MEDIUM_AND_ABOVE", "中风险及以上", "MEDIUM AND ABOVE" -> "MEDIUM_AND_ABOVE";
                case "HIGH_AND_ABOVE", "高风险及以上", "HIGH AND ABOVE" -> "HIGH_AND_ABOVE";
                case "CRITICAL_ONLY", "仅严重风险", "CRITICAL ONLY" -> "CRITICAL_ONLY";
                default -> text;
            };
            case "threeDsTriggerAction" -> switch (normalized) {
                case "FORCE_3DS", "强制3DS", "FORCE 3DS" -> "FORCE_3DS";
                case "SKIP_3DS", "跳过3DS", "SKIP 3DS" -> "SKIP_3DS";
                case "FOLLOW_DEFAULT", "跟随默认", "FOLLOW DEFAULT" -> "FOLLOW_DEFAULT";
                default -> text;
            };
            case "frequencyDimension" -> switch (normalized) {
                case "ELEMENT_COMBINATION", "元素组合", "ELEMENT COMBINATION" -> "ELEMENT_COMBINATION";
                case "ANY_ELEMENT", "任一元素", "ANY ELEMENT" -> "ANY_ELEMENT";
                default -> text;
            };
            case "frequencyWindowUnit" -> switch (normalized) {
                case "MINUTE", "分钟", "MINUTE(S)", "MINUTES" -> "MINUTE";
                case "HOUR", "小时", "HOUR(S)", "HOURS" -> "HOUR";
                case "DAY", "天", "DAY(S)", "DAYS" -> "DAY";
                default -> text;
            };
            case "frequencyElement" -> switch (normalized) {
                case "CARDNO", "CARD_NO", "卡号", "CARD NUMBER" -> "cardNo";
                case "CARDFINGERPRINT", "CARD_FINGERPRINT", "卡指纹", "CARD FINGERPRINT" -> "cardFingerprint";
                case "IP", "IP地址", "IP ADDRESS" -> "ip";
                case "EMAIL", "邮箱" -> "email";
                case "PHONE", "手机号" -> "phone";
                case "CUSTOMERID", "CUSTOMER_ID", "CUSTOMER ID" -> "customerId";
                case "DEVICEFINGERPRINT", "DEVICE_FINGERPRINT", "设备指纹", "DEVICE FINGERPRINT" -> "deviceFingerprint";
                default -> text;
            };
            default -> text;
        };
    }

    private boolean isEnglishLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale != null && Locale.ENGLISH.getLanguage().equalsIgnoreCase(locale.getLanguage());
    }

    private List<String> csvHeaderAliases(String key) {
        return switch (key) {
            case "merchantScope" -> List.of("生效范围", "Scope", "merchant_scope");
            case "merchantId" -> List.of("商户号", "Merchant ID", "merchant_id");
            case "ruleName" -> List.of("规则名称", "Rule Name", "rule_name");
            case "matchValuePlain" -> List.of("匹配值", "Match Value", "卡号", "Card Number", "卡指纹", "Card Fingerprint", "持卡人姓名", "Cardholder Name", "法人", "Legal Person", "企业", "Enterprise", "商户账单地址", "Merchant Billing Address", "手机号", "Phone Number", "邮箱地址", "Email Address", "邮箱用户名", "Email Username", "邮箱域名", "Email Domain", "账单地址", "Billing Address", "账单邮编", "Billing Postal Code", "收货地址", "Shipping Address", "收货邮编", "Shipping Postal Code", "设备指纹", "Device Fingerprint", "来源网址", "Source URL", "match_value_masked");
            case "matchValueMasked" -> List.of("脱敏值", "Masked Value", "match_value_masked");
            case "matchValueHash" -> List.of("哈希值", "Hash Value", "match_value_hash");
            case "matchValueStart" -> List.of("起始值", "起始BIN", "Start BIN", "起始IP", "Start IP", "match_value_start");
            case "matchValueEnd" -> List.of("截止值", "截止BIN", "End BIN", "截止IP", "End IP", "match_value_end");
            case "ipVersion" -> List.of("IP版本", "IP Version", "ip_version");
            case "cardBrand" -> List.of("卡品牌", "Card Brand", "card_brand");
            case "countryAlpha2" -> List.of("国家/地区", "Country/Region", "国家/地区(Alpha2)", "国家Alpha2", "Country Alpha2", "country_alpha2");
            case "countryAlpha3" -> List.of("国家Alpha3", "Country Alpha3", "country_alpha3");
            case "countryNumeric" -> List.of("国家数字码", "Country Numeric Code", "country_numeric");
            case "riskLevel" -> List.of("风险等级", "Risk Level", "risk_level");
            case "decisionAction" -> List.of("决策动作", "Decision Action", "decision_action");
            case "validityType" -> List.of("有效期类型", "Validity Type", "validity_type");
            case "validityDays" -> List.of("有效天数", "Validity Days", "validity_days");
            case "sourceType" -> List.of("来源", "Source", "source_type");
            case "status" -> List.of("状态", "Status");
            case "remark" -> List.of("备注", "Remark");
            case "regionMatchLevel" -> List.of("区域级别", "Region Level", "region_match_level");
            case "stateProvinceName" -> List.of("州/省", "State/Province", "state_province_name");
            case "cityName" -> List.of("城市", "City", "city_name");
            case "matchMode" -> List.of("匹配方式", "Match Mode", "match_mode");
            case "matchValue" -> List.of("规则匹配值", "Rule Match Value", "来源网址", "Source URL", "发卡行国家/地区", "Issuer Country/Region", "卡BIN区间", "Card BIN Range", "match_value");
            case "sourceUrl" -> List.of("来源网址", "Source URL", "source_url");
            case "sourceHost" -> List.of("来源网址Host", "Source Host", "source_host");
            case "limitType" -> List.of("限额类型", "Limit Type", "limit_type");
            case "limitAmount" -> List.of("限额金额", "Limit Amount", "limit_amount");
            case "amountMin" -> List.of("最小金额", "Minimum Amount", "amount_min");
            case "amountMax" -> List.of("最大金额", "Maximum Amount", "amount_max");
            case "currency" -> List.of("币种", "Currency");
            case "ruleType" -> List.of("规则类型", "Rule Type", "rule_type");
            case "channelCode" -> List.of("渠道编码", "Channel Code", "channel_code");
            case "paymentMethodCardBrand" -> List.of("支付方式/卡品牌", "支付方式卡品牌", "Payment Method / Card Brand", "Payment Method/Card Brand", "payment_method_card_brand");
            case "paymentMethod" -> List.of("支付方式", "Payment Method", "payment_method");
            case "amountCondition" -> List.of("金额条件", "Amount Condition", "amount_condition");
            case "amountMatchType" -> List.of("金额匹配类型", "Amount Match Type", "amount_match_type");
            case "riskCondition" -> List.of("风险条件", "Risk Condition", "risk_condition");
            case "triggerAction" -> List.of("触发动作", "Trigger Action", "trigger_action");
            case "priority" -> List.of("优先级", "Priority");
            case "statDimension" -> List.of("统计维度", "Statistic Dimension", "stat_dimension", "statistic_dimension");
            case "elementSet" -> List.of("元素集合", "Element Set", "element_set");
            case "windowValue" -> List.of("时间窗口", "Time Window", "window_value");
            case "windowUnit" -> List.of("窗口单位", "Window Unit", "window_unit");
            case "maxTransactionCount" -> List.of("最大交易次数", "Max Transactions", "max_transaction_count");
            case "maxSuccessCount" -> List.of("最大成功次数", "Max Successes", "max_success_count");
            case "timeWindowSeconds" -> List.of("时间窗口秒数", "Time Window Seconds", "time_window_seconds");
            case "thresholdCount" -> List.of("阈值次数", "Threshold Count", "threshold_count");
            case "elementsJson" -> List.of("组合元素JSON", "Elements JSON", "频率策略", "Frequency Policy", "elements_json");
            case "effectiveTime" -> List.of("生效时间", "Effective Time", "effective_time");
            case "expireTime" -> List.of("失效时间", "Expire Time", "expire_time");
            default -> List.of();
        };
    }

    private record CsvColumn(String key, String header, Function<Map<String, Object>, Object> extractor) {
    }

    private record ImportRow(int rowNo, Map<String, String> values) {
    }

    private boolean hasRangeFields(RiskFunctionDefinition definition) {
        String code = definition.getFunctionCode();
        return "cardBin".equals(code) || "ip".equals(code);
    }

    private boolean hasCardBrandField(RiskFunctionDefinition definition) {
        String code = definition.getFunctionCode();
        return "cardNo".equals(code) || "card".equals(code) || "cardBin".equals(code);
    }

    private boolean isMerchantWhitelist(RiskFunctionDefinition definition) {
        return "WHITE".equalsIgnoreCase(definition.getModuleType())
                && "merchant".equals(definition.getFunctionCode());
    }

    private String cardBinLookupNumber(RiskFunctionDefinition definition, String matchValue) {
        if (!"cardBin".equals(definition.getFunctionCode()) || !StringUtils.hasText(matchValue)) {
            return null;
        }
        String digits = matchValue.trim();
        if (!digits.matches("\\d{6,11}")) {
            return null;
        }
        return rightPad(digits, 11, '0');
    }

    private String rightPad(String value, int length, char ch) {
        if (value.length() >= length) {
            return value;
        }
        return value + String.valueOf(ch).repeat(length - value.length());
    }

    private boolean hasCountryFields(RiskFunctionDefinition definition) {
        String code = definition.getFunctionCode();
        return "country".equals(code)
                || code.endsWith("Country")
                || code.contains("Country");
    }

    private boolean hasCountryNumericField(RiskFunctionDefinition definition) {
        return hasCountryFields(definition) && !isCountryListFunction(definition);
    }

    private boolean hasIpVersionField(RiskFunctionDefinition definition) {
        return "ip".equals(definition.getFunctionCode());
    }

    /**
     * 来源网址 AML 需要额外保存 host，后续交易和商户进件按全局 host 命中。
     *
     * @param definition 风控功能定义
     * @return 是否写入来源网址 host 字段
     */
    private boolean hasSourceHostField(RiskFunctionDefinition definition) {
        return isAmlSourceUrlFunction(definition);
    }

    private boolean isAmlSourceUrlFunction(RiskFunctionDefinition definition) {
        return MODULE_AML.equals(definition.getModuleType()) && FUNCTION_SOURCE_URL.equals(definition.getFunctionCode());
    }

    private boolean isCountryListFunction(RiskFunctionDefinition definition) {
        String code = definition.getFunctionCode();
        return !definition.isRuleFunction() && ("country".equals(code) || code.endsWith("Country") || code.contains("Country"));
    }

    private long offset(long pageNo, long pageSize) {
        return Math.max(pageNo - 1, 0) * pageSize;
    }

    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        return StringUtils.hasText(account.getLoginAccount()) ? account.getLoginAccount() : "admin";
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String upper(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private Integer defaultStatus(Integer status) {
        return status == null ? ENABLED : status;
    }

    private String joinPath(Object... values) {
        List<String> parts = new ArrayList<>();
        for (Object value : values) {
            String text = value == null ? null : String.valueOf(value);
            if (StringUtils.hasText(text)) {
                parts.add(text);
            }
        }
        return String.join("/", parts);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? null : Long.parseLong(String.valueOf(value));
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return value == null ? null : Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return value == null ? null : LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }

    /**
     * 简单分页请求适配，用于变更日志列表。
     */
    public static class PageRequestAdapter extends com.scott.payment.component.core.model.PageRequest {
    }

    private record SourceUrlParts(String sourceUrl, String sourceHost) {
    }

    private record ThreeDsPaymentScope(String paymentMethod, String cardBrand) {
    }

    private record ThreeDsAmountCondition(String amountMatchType, BigDecimal amountMin, BigDecimal amountMax) {
    }

    private record FrequencyWindow(int windowValue, String windowUnit) {
    }
}
