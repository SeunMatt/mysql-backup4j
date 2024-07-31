package com.smattme;

import com.smattme.exceptions.MysqlBackup4JException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by seun_ on 01-Mar-18.
 *
 */
public class MysqlImportService {

    private String database;
    private String username;
    private String host = "localhost";
    private String port = "3306";
    private String password;
    private String sqlString;
    private String jdbcConnString;
    private String jdbcDriver;
    private boolean deleteExisting;
    private boolean dropExisting;
    private List<String> tables;
    private Logger logger = LoggerFactory.getLogger(MysqlImportService.class);

    private MysqlImportService() {
        this.deleteExisting = false;
        this.dropExisting = false;
        this.tables = new ArrayList<>();
    }

    /**
     *
     * @return bool
     * @throws SQLException exception
     * @throws ClassNotFoundException exception
     */
    public boolean importDatabase() throws SQLException, ClassNotFoundException {

        if(!this.assertValidParams()) {
            String message = "Required Parameters not set or empty \n" +
                    "Ensure database, username, password, sqlString params are configured \n" +
                    "using their respective setters";
            logger.error(message);
            throw new MysqlBackup4JException(message);
        }


        //connect to the database
        Connection connection;
        if(jdbcConnString == null || jdbcConnString.isEmpty()) {
            connection = MysqlBaseService.connect(username, password, host, port, database, jdbcDriver);
        }
        else {

            //this prioritizes the value set using the setDatabase() over the one extracted from the connection string
            //it will only use the one from the connection string if no value is set using the setDatabase()
            if(database == null || database.isEmpty()) {
                database = MysqlBaseService.extractDatabaseNameFromJDBCUrl(jdbcConnString);
                logger.debug("database name extracted from connection string: " + database);
            }

            connection = MysqlBaseService.connectWithURL(username, password, jdbcConnString, jdbcDriver);
        }

        Statement stmt = connection.createStatement();

         if(deleteExisting || dropExisting) {

            //get all the tables, so as to eliminate delete errors due to non-existent tables
             TablesResponse allTablesAndViews = MysqlBaseService.getAllTablesAndViews(database, stmt);
             tables = allTablesAndViews.getTables();
             logger.debug("tables found for deleting/dropping: \n" + tables.toString());


             //execute delete query for tables
            for (String table: tables) {

                //if deleteExisting and dropExisting is true
                //skip the deleteExisting query
                //dropExisting will take care of both
                if(deleteExisting && !dropExisting) {
                    String delQ = "DELETE FROM " + "`" + table + "`;";
                    logger.debug("adding " + delQ + " to batch");
                    stmt.addBatch(delQ);
                }

                if(dropExisting) {
                    String dropQ = "DROP TABLE IF EXISTS " + "`" + table + "`";
                    logger.debug("adding " + dropQ + " to batch");
                    stmt.addBatch(dropQ);
                }

            }


             List<String> views = allTablesAndViews.getViews();
             //execute delete query for views
            for (String view: views) {
                if(dropExisting) {
                    String dropQ = "DROP VIEW IF EXISTS " + "`" + view + "`";
                    logger.debug("adding " + dropQ + " to batch");
                    stmt.addBatch(dropQ);
                }
            }

        }

        //disable foreign key check
        stmt.addBatch("SET FOREIGN_KEY_CHECKS = 0");


        //now process the sql string supplied
        while (sqlString.contains(MysqlBaseService.SQL_START_PATTERN)) {

            //get the chunk of the first statement to execute
            int startIndex = sqlString.indexOf(MysqlBaseService.SQL_START_PATTERN);
            int endIndex = sqlString.indexOf(MysqlBaseService.SQL_END_PATTERN);

            String executable = sqlString.substring(startIndex, endIndex).trim();
            logger.debug("adding extracted executable SQL chunk to batch : \n" + executable);
            stmt.addBatch(executable);

            //remove the chunk from the whole to reduce it
            sqlString = sqlString.substring(endIndex + 1);

            //repeat
        }


        //add enable foreign key check
        stmt.addBatch("SET FOREIGN_KEY_CHECKS = 1");

        //now execute the batch
        long[] result = stmt.executeLargeBatch();

        if(logger.isDebugEnabled())
            logger.debug( result.length + " queries were executed in batches for provided SQL String with the following result : \n" + Arrays.toString(result));

        stmt.close();
        connection.close();

        return true;
    }

    /**
     * This function will check that required parameters
     * are set.
     * password is excluded here because it's possible to have a mysql database
     * user with no password
     * @return true if the required params are present and valid, false otherwise
     */
    private boolean assertValidParams() {
        return username != null && !this.username.isEmpty() &&
                sqlString != null && !this.sqlString.isEmpty() &&
        ( (database != null && !this.database.isEmpty()) || (jdbcConnString != null && !jdbcConnString.isEmpty()) );
    }

    /**
     * This function will create a new
     * MysqlImportService instance thereby facilitating
     * a builder pattern
     * @return MysqlImportService
     */
    public static MysqlImportService builder() {
        return new MysqlImportService();
    }

    public MysqlImportService setDatabase(String database) {
        this.database = database;
        return this;
    }

    public MysqlImportService setUsername(String username) {
        this.username = username;
        return this;
    }

    public MysqlImportService setPassword(String password) {
        this.password = password;
        return this;
    }

    public MysqlImportService setSqlString(String sqlString) {
        this.sqlString = sqlString;
        return this;
    }

    public MysqlImportService setDeleteExisting(boolean deleteExisting) {
        this.deleteExisting = deleteExisting;
        return this;
    }

    public MysqlImportService setDropExisting(boolean dropExistingTable) {
        this.dropExisting = dropExistingTable;
        return  this;
    }

    public MysqlImportService setJdbcDriver(String jdbcDriver) {
        this.jdbcDriver = jdbcDriver;
        return this;
    }

    public MysqlImportService setJdbcConnString(String jdbcConnString) {
        this.jdbcConnString = jdbcConnString;
        return this;
    }

    public MysqlImportService setHost(String host) {
        this.host = host;
        return this;
    }

    public MysqlImportService setPort(String port) {
        this.port = port;
        return this;
    }
}
