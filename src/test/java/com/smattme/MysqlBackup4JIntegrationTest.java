package com.smattme;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by seun_ on 10-Oct-20.
 *
 */
class MysqlBackup4JIntegrationTest {

    private Logger logger = LoggerFactory.getLogger(getClass());
    private static final String TEST_DB = "mysqlbackup4j_test";
    private static final String RESTORED_DB = "mysqlbackup4j_restored";
    private static final String DB_USERNAME = "travis";
    private static final String DB_PASSWORD = "";

    @BeforeEach
    void setUp() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
    }

    @Test
    void givenDBCredentials_whenExportDatabaseAndImportDatabase_thenBackUpAndRestoreTestDbSuccessfully() throws SQLException, ClassNotFoundException, IOException {

        Properties properties = new Properties();
        properties.setProperty(MysqlExportService.DB_NAME, TEST_DB);
        properties.setProperty(MysqlExportService.DB_USERNAME, DB_USERNAME);
        properties.setProperty(MysqlExportService.DB_PASSWORD, DB_PASSWORD);

        properties.setProperty(MysqlExportService.PRESERVE_GENERATED_ZIP, "true");
        properties.setProperty(MysqlExportService.PRESERVE_GENERATED_SQL_FILE, "true");

        properties.setProperty(MysqlExportService.JDBC_DRIVER_NAME, "com.mysql.cj.jdbc.Driver");
        properties.setProperty(MysqlExportService.ADD_IF_NOT_EXISTS, "true");


        properties.setProperty(MysqlExportService.TEMP_DIR, new File("external").getPath());
        properties.setProperty(MysqlExportService.SQL_FILE_NAME, "test_output_file_name");

        MysqlExportService mysqlExportService = new MysqlExportService(properties);
        mysqlExportService.export();

        String generatedSql = mysqlExportService.getGeneratedSql();
        Assertions.assertFalse(generatedSql.isEmpty());
//        logger.info("generated SQL: \n" + generatedSql);

        File file = mysqlExportService.getGeneratedZipFile();
        assertNotNull(file);
        logger.info("Generated Filename: " + file.getAbsolutePath());

        String sql = new String(Files.readAllBytes(new File("external/sql/test_output_file_name.sql").toPath()));

        MysqlImportService res = MysqlImportService.builder()
                .setJdbcDriver("com.mysql.cj.jdbc.Driver")
                .setDatabase(RESTORED_DB)
                .setSqlString(sql)
                .setUsername(DB_USERNAME)
                .setPassword(DB_PASSWORD)
                .setDeleteExisting(true)
                .setDropExisting(true);

        assertTrue(res.importDatabase());

    }

    @Test
    void givenJDBCConString_whenExportDatabaseAndImportDatabase_thenBackUpAndRestoreTestDbSuccessfully() throws SQLException, ClassNotFoundException, IOException {

        Properties properties = new Properties();
        properties.setProperty(MysqlExportService.DB_USERNAME, DB_USERNAME);
        properties.setProperty(MysqlExportService.DB_PASSWORD, DB_PASSWORD);
        properties.setProperty(MysqlExportService.JDBC_CONNECTION_STRING, "jdbc:mysql://localhost:3306/" + TEST_DB + "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false");

        properties.setProperty(MysqlExportService.PRESERVE_GENERATED_ZIP, "true");
        properties.setProperty(MysqlExportService.PRESERVE_GENERATED_SQL_FILE, "true");
        properties.setProperty(MysqlExportService.ADD_IF_NOT_EXISTS, "true");

        properties.setProperty(MysqlExportService.TEMP_DIR, new File("external").getPath());

        MysqlExportService mysqlExportService = new MysqlExportService(properties);
        mysqlExportService.export();

        String generatedSql = mysqlExportService.getGeneratedSql();

        logger.debug("Final Output:\n {}", generatedSql);

        File file = mysqlExportService.getGeneratedZipFile();
        assertNotNull(file);
        logger.debug("generated file name: " + file.getAbsolutePath());


        //import
        String sql = new String(Files.readAllBytes(Paths.get("external/sql/test_output_file_name.sql")));
        boolean res = MysqlImportService.builder()
                .setSqlString(sql)
                .setJdbcConnString("jdbc:mysql://localhost:3306/" + RESTORED_DB + "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false")
                .setUsername(DB_USERNAME)
                .setPassword(DB_PASSWORD)
                .setDeleteExisting(true)
                .setDropExisting(true)
                .importDatabase();

        assertTrue(res);

    }

}