package com.smattme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by seun_ on 01-Mar-18.
 *
 */
public class MysqlBaseService {

    private static Logger logger = LoggerFactory.getLogger(MysqlBaseService.class);

    static final String SQL_START_PATTERN = "-- start";
    static final String SQL_END_PATTERN = "-- end";

    static Connection connect(String username, String password, String database, String driverName) throws ClassNotFoundException, SQLException {
        String url = "jdbc:mysql://localhost:3306/" + database;
        String driver = (Objects.isNull(driverName) || driverName.isEmpty()) ? "com.mysql.jdbc.Driver" : driverName;
        return doConnect(driver, url, username, password);
    }

    static Connection connectWithURL(String username, String password, String jdbcURL, String driverName) throws ClassNotFoundException, SQLException {
        String driver = (Objects.isNull(driverName) || driverName.isEmpty()) ? "com.mysql.jdbc.Driver" : driverName;
        return doConnect(driver, jdbcURL, username, password);
    }








    private static Connection doConnect(String driver, String url, String username, String password) throws SQLException, ClassNotFoundException {
        Class.forName(driver);
        Connection connection = DriverManager.getConnection(url, username, password);
        logger.debug("DB Connected Successfully");
        return  connection;
    }


    static List<String> getAllTables(String database, Statement stmt) throws SQLException {
        List<String> table = new ArrayList<>();
        ResultSet rs;
        rs = stmt.executeQuery("SHOW TABLE STATUS FROM `" + database + "`;");
        while ( rs.next() ) {
            table.add(rs.getString("Name"));
        }
        return table;
    }

    static String getEmptyTableSQL(String database, String table) {
        return  "\n" + MysqlBaseService.SQL_START_PATTERN + "\n" +
                "DELETE FROM `" + database + "`.`" + table + "`;\n" +
                "\n" + MysqlBaseService.SQL_END_PATTERN + "\n";
    }

}
