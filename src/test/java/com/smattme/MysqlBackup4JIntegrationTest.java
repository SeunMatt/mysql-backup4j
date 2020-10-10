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

    @BeforeEach
    void setUp() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "info");
    }

    @Test
    void givenDBCredentials_whenExportDatabaseAndImportDatabase_thenBackUpAndRestoreTestDbSuccessfully() throws SQLException, ClassNotFoundException, IOException {

        Properties properties = new Properties();
        properties.setProperty(MysqlExportService.DB_NAME, "mnetsms_db");
        properties.setProperty(MysqlExportService.DB_USERNAME, "root");
        properties.setProperty(MysqlExportService.DB_PASSWORD, "root");

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

        String sql = new String(Files.readAllBytes(Paths.get("external/sql/test_output_file_name.sql")));

        MysqlImportService res = MysqlImportService.builder()
                .setJdbcDriver("com.mysql.cj.jdbc.Driver")
                .setDatabase("backup4j_test")
                .setSqlString(sql)
                .setUsername("root")
                .setPassword("root")
                .setDeleteExisting(true)
                .setDropExisting(true);

        assertTrue(res.importDatabase());

    }

    @Test
    void givenJDBCConString_whenExportDatabaseAndImportDatabase_thenBackUpAndRestoreTestDbSuccessfully() throws SQLException, ClassNotFoundException, IOException {

        Properties properties = new Properties();
        properties.setProperty(MysqlExportService.DB_USERNAME, "root");
        properties.setProperty(MysqlExportService.DB_PASSWORD, "root");
        properties.setProperty(MysqlExportService.JDBC_CONNECTION_STRING, "jdbc:mysql://localhost:3306/mnetsms_db?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false");

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
                .setJdbcConnString("jdbc:mysql://localhost:3306/backup4j_test?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false")
                .setUsername("root")
                .setPassword("root")
                .setDeleteExisting(true)
                .setDropExisting(true)
                .importDatabase();

        assertTrue(res);

    }

}