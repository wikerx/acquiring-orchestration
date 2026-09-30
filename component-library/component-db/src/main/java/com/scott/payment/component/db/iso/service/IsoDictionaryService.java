package com.scott.payment.component.db.iso.service;

import com.scott.payment.component.core.iso.IsoCountryInfo;
import com.scott.payment.component.core.iso.IsoCurrencyInfo;
import com.scott.payment.component.core.iso.IsoCurrencyPresentationInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoDictionaryService
 * @date : 2026-06-03 14:28
 * @email : scott_x@163.com
 * @description : ISO 国家地区与币种基础字典公共查询服务
 * @status : create
 */
public interface IsoDictionaryService {

    /**
     * 查询系统当前启用的全部国家地区。
     *
     * @return 启用国家地区列表
     */
    List<IsoCountryInfo> listCountries();

    /**
     * 根据已提供的 ISO 国家代码在数据库中精确查询启用国家，多个代码使用 AND 组合。
     *
     * @param alpha2 ISO 3166-1 两位代码，未传时为 null
     * @param alpha3 ISO 3166-1 三位代码，未传时为 null
     * @param numeric ISO 3166-1 三位数字字符串，未传时为 null
     * @return 命中的启用国家；无条件时使用全量字典快照
     */
    List<IsoCountryInfo> listCountriesByCodes(String alpha2, String alpha3, String numeric);

    /**
     * 根据关键字查询国家地区，支持 alpha-2、alpha-3、numeric、英文名、中文名、地区、默认币种和主要语言。
     *
     * @param keyword 查询关键字，空值时返回全部启用国家地区
     * @return 命中的国家地区列表
     */
    List<IsoCountryInfo> searchCountries(String keyword);

    /**
     * 根据标准代码或名称精确识别国家地区。
     *
     * @param value 国家地区代码或名称
     * @return 命中的国家地区
     */
    Optional<IsoCountryInfo> getCountry(String value);

    /**
     * 根据七大洲代码查询国家地区。
     *
     * @param continentCode 七大洲代码：AS/EU/AF/NA/SA/OC/AN
     * @return 指定大洲下的国家地区列表
     */
    List<IsoCountryInfo> listCountriesByContinent(String continentCode);

    /**
     * 根据默认币种查询国家地区。
     *
     * @param currencyAlpha3Code ISO 4217 三位字母币种代码
     * @return 默认使用该币种的国家地区列表
     */
    List<IsoCountryInfo> listCountriesByCurrency(String currencyAlpha3Code);

    /**
     * 查询系统当前启用的全部币种。
     *
     * @return 启用币种列表
     */
    List<IsoCurrencyInfo> listCurrencies();

    /**
     * 根据已提供的 ISO 币种代码在数据库中精确查询启用币种，两个代码使用 AND 组合。
     *
     * @param alphabeticCode ISO 4217 三位字母代码，未传时为 null
     * @param numericCode ISO 4217 三位数字字符串，未传时为 null
     * @return 命中的启用币种；无条件时使用全量字典快照
     */
    List<IsoCurrencyInfo> listCurrenciesByCodes(String alphabeticCode, String numericCode);

    /**
     * 查询系统当前启用币种的界面展示信息。
     *
     * @return 币种代码、名称、符号和受控图标键列表
     */
    List<IsoCurrencyPresentationInfo> listCurrencyPresentations();

    /**
     * 根据关键字查询币种，支持三位字母代码、三位数字代码、英文名、中文名和币种符号。
     *
     * @param keyword 查询关键字，空值时返回全部启用币种
     * @return 命中的币种列表
     */
    List<IsoCurrencyInfo> searchCurrencies(String keyword);

    /**
     * 根据标准代码或名称精确识别币种。
     *
     * @param value 币种代码或名称
     * @return 命中的币种信息
     */
    Optional<IsoCurrencyInfo> getCurrency(String value);

    /**
     * 校验金额小数位是否符合币种默认辅币位。
     *
     * @param amount        交易金额，禁止使用 double/float
     * @param currencyValue 币种代码或名称
     * @return true 表示金额小数位合法
     */
    boolean isCurrencyFractionValid(BigDecimal amount, String currencyValue);

    /**
     * 将主币单位金额转换为最小辅币单位。
     *
     * @param amount        主币单位金额
     * @param currencyValue 币种代码或名称
     * @return 最小辅币单位金额
     */
    long toMinorUnit(BigDecimal amount, String currencyValue);
}
