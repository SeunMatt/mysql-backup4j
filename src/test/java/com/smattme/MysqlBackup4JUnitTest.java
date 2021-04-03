package com.smattme;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MysqlBackup4JUnitTest {


    @Test
    void givenJDBCURL_whenExtractDatabaseNameFromJDBCURL_thenReturnDatabaseName() {
        String jdbcURL = "jdbc:mysql://localhost:3306/test?characterEncoding=utf-8&useSSL=true&serverTimezone=Asia/Shanghai";
        String databaseName = MysqlBaseService.extractDatabaseNameFromJDBCUrl(jdbcURL);
        Assertions.assertEquals("test", databaseName);

        jdbcURL = "jdbc:mysql://localhost:3306/backup4j_test?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false";
        databaseName = MysqlBaseService.extractDatabaseNameFromJDBCUrl(jdbcURL);
        Assertions.assertEquals("backup4j_test", databaseName);

        jdbcURL = "jdbc:mysql://localhost:3306/backup4j_test";
        databaseName = MysqlBaseService.extractDatabaseNameFromJDBCUrl(jdbcURL);
        Assertions.assertEquals("backup4j_test", databaseName);
    }
}
