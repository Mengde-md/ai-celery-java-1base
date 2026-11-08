package com.example.aitask.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

/** MyBatis XML 可解析性及核心抢占 SQL 形态测试。 */
class AiTaskMapperXmlTest {
    private static final String NAMESPACE = "com.example.aitask.storage.mapper.AiTaskMapper";

    /** 确认抢占由单条带状态条件的 UPDATE 完成，而不是先 SELECT 再更新。 */
    @Test
    void mapperParsesAndClaimUsesOneConditionalUpdate() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream input = getClass().getResourceAsStream("/mapper/AiTaskMapper.xml")) {
            assertThat(input).isNotNull();
            new XMLMapperBuilder(input, configuration, "mapper/AiTaskMapper.xml",
                    configuration.getSqlFragments()).parse();
        }

        assertThat(configuration.hasStatement(NAMESPACE + ".claimTasks")).isTrue();
        BoundSql claimSql = configuration.getMappedStatement(NAMESPACE + ".claimTasks")
                .getBoundSql(Map.of(
                        "runnerId", "runner-1",
                        "claimToken", "lease-1",
                        "leaseExpireAt", "2026-10-02T12:00:00Z",
                        "limit", 10));
        String normalized = claimSql.getSql().replaceAll("\\s+", " ").trim();

        assertThat(normalized)
                .contains("UPDATE ai_task")
                .contains("WHERE status = 'PENDING'")
                .contains("ORDER BY priority DESC, updated_at ASC, id ASC")
                .contains("LIMIT ?");
    }
}
