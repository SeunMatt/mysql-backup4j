package com.smattme;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by seun_ on 10-Oct-20.
 *
 */
class MysqlBackup4JIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(MysqlBackup4JIntegrationTest.class);
    private static final String TEST_DB = "mysqlbackup4j_test";
    private static final String RESTORED_DB = "mysqlbackup4j_restored";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "backup4j";
    private static final String DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";
    protected static String MYSQL_DB_PORT = "3306";
    protected static String MYSQL_DB_HOST = "localhost";

    private static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.1.0");

    @BeforeAll
    static void setUp() {
        mysql.withUsername(DB_USERNAME)
                .withPassword(DB_PASSWORD)
                .withExposedPorts(3306)
                .withInitScript("mysql_init.sql")
                .start();
        MYSQL_DB_PORT = mysql.getMappedPort(3306).toString();
        MYSQL_DB_HOST = mysql.getHost();
        logger.info("MYSQL_DB_HOST: {}, MYSQL_DB_PORT: {}", MYSQL_DB_HOST, MYSQL_DB_PORT);
    }

    @AfterAll
    static void tearDown() {
        if (Objects.nonNull(mysql)) {
            mysql.stop();
        }
    }


    @Test
    void givenDBCredentials_whenExportDatabaseAndImportDatabase_thenBackUpAndRestoreTestDbSuccessfully() throws Exception {

        Properties properties = new Properties();
        properties.setProperty(MysqlExportService.DB_NAME, TEST_DB);
        properties.setProperty(MysqlExportService.DB_USERNAME, DB_USERNAME);
        properties.setProperty(MysqlExportService.DB_PASSWORD, DB_PASSWORD);

        properties.setProperty(MysqlExportService.DB_HOST, MYSQL_DB_HOST);
        properties.setProperty(MysqlExportService.DB_PORT, MYSQL_DB_PORT);

        properties.setProperty(MysqlExportService.PRESERVE_GENERATED_ZIP, "true");
        properties.setProperty(MysqlExportService.PRESERVE_GENERATED_SQL_FILE, "true");

        properties.setProperty(MysqlExportService.JDBC_DRIVER_NAME, DRIVER_CLASS_NAME);
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

        File sqlFile = new File("external/sql/test_output_file_name.sql");
        logger.info("SQL File name: " + sqlFile.getAbsolutePath());

        String sql = new String(Files.readAllBytes(sqlFile.toPath()));
        MysqlImportService res = MysqlImportService.builder()
                .setJdbcDriver("com.mysql.cj.jdbc.Driver")
                .setDatabase(RESTORED_DB)
                .setSqlString(sql)
                .setUsername(DB_USERNAME)
                .setPassword(DB_PASSWORD)
                .setHost(MYSQL_DB_HOST)
                .setPort(MYSQL_DB_PORT)
                .setDeleteExisting(true)
                .setDropExisting(true);

        assertTrue(res.importDatabase());

        assertDatabaseBackedUp();

    }


    @Test
    void givenJDBCConString_whenExportDatabaseAndImportDatabase_thenBackUpAndRestoreTestDbSuccessfully() throws Exception {

        Properties properties = new Properties();
        properties.setProperty(MysqlExportService.DB_USERNAME, DB_USERNAME);
        properties.setProperty(MysqlExportService.DB_PASSWORD, DB_PASSWORD);
        properties.setProperty(MysqlExportService.DB_NAME, TEST_DB);
        String jdbcUrl = String.format("jdbc:mysql://%s:%s/%s?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false",
                MYSQL_DB_HOST, MYSQL_DB_PORT, TEST_DB);
        properties.setProperty(MysqlExportService.JDBC_CONNECTION_STRING, jdbcUrl);

        properties.setProperty(MysqlExportService.PRESERVE_GENERATED_ZIP, "true");
        properties.setProperty(MysqlExportService.PRESERVE_GENERATED_SQL_FILE, "true");
        properties.setProperty(MysqlExportService.SQL_FILE_NAME, "test_output_file_name");
        properties.setProperty(MysqlExportService.ADD_IF_NOT_EXISTS, "true");

        properties.setProperty(MysqlExportService.TEMP_DIR, new File("external").getPath());

        MysqlExportService mysqlExportService = new MysqlExportService(properties);
        mysqlExportService.export();

        String generatedSql = mysqlExportService.getGeneratedSql();
//        logger.debug("Final Output:\n {}", generatedSql);

        File file = mysqlExportService.getGeneratedZipFile();
        assertNotNull(file);
        Assertions.assertEquals("test_output_file_name.zip", file.getName());


        //import
        File sqlFile = new File("external/sql/test_output_file_name.sql");

        String sql = new String(Files.readAllBytes(sqlFile.toPath()));
        String restoredJdbcUrl = String.format("jdbc:mysql://%s:%s/%s?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false"
                        + "&serverTimezone=UTC&useSSL=false",
                MYSQL_DB_HOST, MYSQL_DB_PORT, RESTORED_DB);
        boolean res = MysqlImportService.builder()
                .setSqlString(sql)
                .setJdbcConnString(restoredJdbcUrl)
                .setUsername(DB_USERNAME)
                .setPassword(DB_PASSWORD)
                .setDatabase(RESTORED_DB)
                .setDeleteExisting(true)
                .setDropExisting(true)
                .importDatabase();

        assertTrue(res);

        assertDatabaseBackedUp();
    }


    private void assertDatabaseBackedUp() throws Exception {
        Connection connection = MysqlBaseService.connect(DB_USERNAME, DB_PASSWORD, MYSQL_DB_HOST, MYSQL_DB_PORT,
                RESTORED_DB, DRIVER_CLASS_NAME);
        Statement statement = connection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
        statement.execute("SELECT COUNT(1) as total FROM users");
        ResultSet resultSet = statement.getResultSet();
        resultSet.first();
        assertTrue(resultSet.getLong("total") > 0);
    }

}