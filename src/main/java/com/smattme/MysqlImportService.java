package com.smattme;

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
    private String password;
    private String sqlString;
    private boolean deleteExisting;
    private boolean dropExisting;
    private List<String> tables;
    private Logger logger = LoggerFactory.getLogger(MysqlImportService.class);

    private MysqlImportService() {
        this.deleteExisting = false;
        this.dropExisting = false;
        this.tables = new ArrayList<>();
    }

    public boolean importDatabase() throws SQLException, ClassNotFoundException {

        if(!this.assertValidParams()) {
            logger.error("Required Parameters not set or empty \n" +
                    "Ensure database, username, password, sqlString params are configured \n" +
                    "using their respective setters");
            return false;
        }

        //connect
        Connection connection = MysqlBaseService.connect(username, password, database);
        Statement stmt = connection.createStatement();

        //disable foreign key check
        stmt.addBatch("SET FOREIGN_KEY_CHECKS = 0");


        if(dropExisting) {






        }

         if(deleteExisting || dropExisting) {

             if(deleteExisting)
                logger.debug("deleteExisting flag is set to TRUE! I believe you know what you're doing");

             if(dropExisting)
                 logger.debug("dropExisting flag is set to TRUE! This will drop any existing table(s) in the database");

            //get all the tables
            tables = MysqlBaseService.getAllTables(database, stmt);

             //execute delete query
            for (String table: tables) {

                //if deleteExisting and dropExisting is true
                //skip the deleteExisting query
                //dropExisting will take care of both

                if(deleteExisting && !dropExisting) {
                    String delQ = "DELETE FROM `" + database + "`.`" + table + "`";
                    logger.debug("adding " + delQ + " to batch");
                    stmt.addBatch(delQ);
                }

                if(dropExisting) {
                    String dropQ = "DROP TABLE `" + database + "`.`" + table + "`";
                    logger.debug("adding " + dropQ + " to batch");
                    stmt.addBatch(dropQ);
                }

            }
        }

        //now process the sql string supplied
        while (sqlString.contains(MysqlBaseService.SQL_START_PATTERN)) {

            //get the chunk of the first statement to execute
            int startIndex = sqlString.indexOf(MysqlBaseService.SQL_START_PATTERN);
            int endIndex = sqlString.indexOf(MysqlBaseService.SQL_END_PATTERN);

            String executable = sqlString.substring(startIndex, endIndex);
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

        final String[] resultString = {""};
        Arrays.stream(result).forEach(i -> resultString[0] = resultString[0].concat(i + " "));
        logger.debug( result.length + " queries were executed in batch for provided SQL String with the following result : \n" + resultString[0]);

        stmt.close();
        connection.close();

        return true;
    }

    private boolean assertValidParams() {
        return !this.database.isEmpty() &&
                !this.username.isEmpty() &&
                !this.password.isEmpty() &&
                !this.sqlString.isEmpty();
    }

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


}
