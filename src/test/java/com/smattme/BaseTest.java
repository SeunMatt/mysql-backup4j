package com.smattme;

import com.smattme.config.FixedPortMySQLContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(BaseTest.class);
    public static FixedPortMySQLContainer<?> mysql = new FixedPortMySQLContainer<>("mysql:8.1.0");
    protected static final String TEST_DB = "mysqlbackup4j_test";
    protected static final String RESTORED_DB = "mysqlbackup4j_restored";
    protected static final String DB_USERNAME = "root";
    protected static final String DB_PASSWORD = "password";
    protected static final String DRIVER_CLASS_NAME = "com.mysql.cj.jdbc.Driver";

    protected static String DB_PORT = "3306";

    protected static String DB_HOST = "localhost";


    @BeforeAll
    static void setUp() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "trace");
        mysql.addFixedExposedPort(3306, 3306);
        mysql.withUsername(DB_USERNAME)
                .withPassword(DB_PASSWORD)
                .withEnv("MYSQL_ROOT_PASSWORD", DB_PASSWORD)
                .withExposedPorts(3306)
                .withInitScript("sample_database.sql")
            .start();
        DB_PORT = mysql.getMappedPort(3306).toString();
        DB_HOST = mysql.getHost();
    }


    @AfterAll
    static void tearDown() {
        if(Objects.nonNull(mysql))
            mysql.stop();
    }



}
