package com.scott.payment.component.db.reference.mapper;

import com.scott.payment.component.db.reference.entity.CardBinRangeDO;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinLookupMapperSqlTest
 * @date : 2026-09-30
 * @email : scott_x@163.com
 * @description : 在 MySQL 兼容的内存库执行 BIN Mapper SQL，验证跨区间与精度排序语义。
 * @status : create
 */
class CardBinLookupMapperSqlTest {

    @Test
    void shouldMatchCrossBinRangeAndPreferMorePreciseEffectiveRow() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:bin_lookup_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE base_card_bin_range (
                    id BIGINT PRIMARY KEY,
                    deleted BIGINT NOT NULL,
                    status INT NOT NULL,
                    card_bin_start BIGINT NOT NULL,
                    card_bin_end BIGINT NOT NULL,
                    bin_length INT NOT NULL,
                    card_brand VARCHAR(64),
                    card_sub_brand VARCHAR(64),
                    card_type VARCHAR(32),
                    card_level VARCHAR(32),
                    issuer_country_name VARCHAR(64),
                    issuer_country_alpha2 VARCHAR(2),
                    issuer_country_alpha3 VARCHAR(3),
                    issuer_country_numeric VARCHAR(3),
                    issuer_bank VARCHAR(64),
                    effective_time TIMESTAMP(3),
                    expire_time TIMESTAMP(3),
                    update_time TIMESTAMP(3) NOT NULL
                )
                """);
        // 后台允许 411111 至 411113 的六位区间，起点补 0、终点补 9。
        jdbc.update("""
                INSERT INTO base_card_bin_range
                    (id, deleted, status, card_bin_start, card_bin_end, bin_length,
                     card_brand, update_time)
                VALUES (1, 0, 1, 41111100000, 41111399999, 6, 'VISA', CURRENT_TIMESTAMP(3))
                """);
        jdbc.update("""
                INSERT INTO base_card_bin_range
                    (id, deleted, status, card_bin_start, card_bin_end, bin_length,
                     card_brand, update_time)
                VALUES (2, 0, 1, 41111234000, 41111234999, 8, 'MASTERCARD', CURRENT_TIMESTAMP(3))
                """);
        jdbc.update("""
                INSERT INTO base_card_bin_range
                    (id, deleted, status, card_bin_start, card_bin_end, bin_length,
                     card_brand, effective_time, update_time)
                VALUES (3, 0, 1, 41111234000, 41111234999, 8, 'FUTURE',
                        DATEADD('HOUR', 1, CURRENT_TIMESTAMP(3)), CURRENT_TIMESTAMP(3))
                """);
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(CardBinLookupMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);

        try (SqlSession session = factory.openSession()) {
            CardBinLookupMapper mapper = session.getMapper(CardBinLookupMapper.class);
            CardBinRangeDO crossRange = mapper.selectBestMatch(41111200000L, 6);
            CardBinRangeDO precise = mapper.selectBestMatch(41111234000L, 8);

            assertThat(crossRange.getCardBrand()).isEqualTo("VISA");
            assertThat(precise.getCardBrand()).isEqualTo("MASTERCARD");
            assertThat(mapper.selectBestMatch(41111400000L, 6)).isNull();
            assertThat(mapper.selectNextEffectiveTime(41111234000L, 8))
                    .isAfter(LocalDateTime.now());
        }
    }
}
