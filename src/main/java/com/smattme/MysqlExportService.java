package com.smattme;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

/**
 * Created by seun_ on 24-Feb-18.
 *
 */
public class MysqlExportService {

    private Statement stmt;
    private String database;
    private String generatedSql = "";
    private Logger logger = LoggerFactory.getLogger(getClass());
    private final String LOG_PREFIX = "java-mysql-exporter";
    private String dirName = "java-mysql-exporter-temp";
    private String sqlFileName = "";
    private String zipFileName = "";
    private Properties properties;
    private File generatedZipFile;

    public static final String EMAIL_HOST = "EMAIL_HOST";
    public static final String EMAIL_PORT = "EMAIL_PORT";
    public static final String EMAIL_USERNAME = "EMAIL_USERNAME";
    public static final String EMAIL_PASSWORD = "EMAIL_PASSWORD";
    public static final String EMAIL_SUBJECT = "EMAIL_SUBJECT";
    public static final String EMAIL_MESSAGE = "EMAIL_MESSAGE";
    public static final String EMAIL_FROM = "EMAIL_FROM";
    public static final String EMAIL_TO = "EMAIL_TO";
    public static final String DB_NAME = "DB_NAME";
    public static final String DB_USERNAME = "DB_USERNAME";
    public static final String DB_PASSWORD = "DB_PASSWORD";
    public static final String PRESERVE_GENERATED_ZIP = "PRESERVE_GENERATED_ZIP";
    public static final String TEMP_DIR = "TEMP_DIR";
    public static final String ADD_IF_NOT_EXISTS = "ADD_IF_NOT_EXISTS";
    public static final String DROP_TABLES = "DROP_TABLES";
    public static final String DELETE_EXISTING_DATA = "DELETE_EXISTING_DATA";
    public static final String JDBC_CONNECTION_STRING = "JDBC_CONNECTION_STRING";
    public static final String JDBC_DRIVER_NAME = "JDBC_DRIVER_NAME";


    public MysqlExportService(Properties properties) {
        this.properties = properties;
    }

    private boolean validateProperties() {
        return properties != null &&
                properties.containsKey(DB_USERNAME) &&
                properties.containsKey(DB_PASSWORD) &&
                (properties.containsKey(DB_NAME) || properties.containsKey(JDBC_CONNECTION_STRING));
    }

    private boolean emailPropertiesSet() {
        return properties != null &&
               properties.containsKey(EMAIL_HOST) &&
               properties.containsKey(EMAIL_PORT) &&
               properties.containsKey(EMAIL_USERNAME) &&
               properties.containsKey(EMAIL_PASSWORD) &&
               properties.containsKey(EMAIL_FROM) &&
               properties.containsKey(EMAIL_TO);
    }

    private String getTableInsertStatement(String table) throws SQLException {

        StringBuilder sql = new StringBuilder();
        ResultSet rs;
        boolean addIfNotExists = Boolean.parseBoolean(properties.containsKey(ADD_IF_NOT_EXISTS) ? properties.getProperty(ADD_IF_NOT_EXISTS, "true") : "true");
        boolean dropTable = Boolean.parseBoolean(properties.containsKey(DROP_TABLES) ? properties.getProperty(DROP_TABLES, "false") : "false");

        if(table != null && !table.isEmpty()){
          rs = stmt.executeQuery("SHOW CREATE TABLE `" + database + "`.`" + table + "`;");
          while ( rs.next() ) {
                String qtbl = rs.getString(1);
                String query = rs.getString(2);
                sql.append("\n\n--");
                sql.append("\n").append(MysqlBaseService.SQL_START_PATTERN).append("  table dump : ").append(qtbl);
                sql.append("\n--\n\n");

                if(addIfNotExists) {
                    query = query.trim().replace("CREATE TABLE", "CREATE TABLE IF NOT EXISTS ");
                }

                if(dropTable) {
                    sql.append("DROP TABLE IF EXISTS `").append(database).append("`.`").append(table).append("`;\n");
                }
                sql.append(query).append(";\n\n");
          }
        }

        sql.append("\n\n--");
        sql.append("\n").append(MysqlBaseService.SQL_END_PATTERN).append("  table dump : ").append(table);
        sql.append("\n--\n\n");

        return sql.toString();
    }

    private String getDataInsertStatement(String table) throws SQLException {

        StringBuilder sql = new StringBuilder();

        ResultSet rs = stmt.executeQuery("SELECT * FROM `" + database + "`.`" + table + "`;");
        rs.last();
        int rowCount = rs.getRow();

        //there are no records just return empty string
        if(rowCount <= 0) {
            return sql.toString();
        }

        sql.append("\n--").append("\n-- Inserts of ").append(table).append("\n--\n\n");

        //temporarily disable foreign key constraint
        sql.append("\n/*!40000 ALTER TABLE `").append(table).append("` DISABLE KEYS */;\n");

        boolean deleteExistingData = Boolean.parseBoolean(properties.containsKey(DELETE_EXISTING_DATA) ? properties.getProperty(DELETE_EXISTING_DATA, "false") : "false");

        if(deleteExistingData) {
            sql.append(MysqlBaseService.getEmptyTableSQL(database, table));
        }

        sql.append("\n--\n")
                .append(MysqlBaseService.SQL_START_PATTERN).append(" table insert : ").append(table)
                .append("\n--\n");

        sql.append("INSERT INTO `").append(table).append("`(");

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        for(int i = 0; i < columnCount; i++) {
           sql.append("`")
                   .append(metaData.getColumnName( i + 1))
                   .append("`, ");
        }

        //remove the last whitespace and comma
        sql.deleteCharAt(sql.length() - 1).deleteCharAt(sql.length() - 1).append(") VALUES \n");

        //build the values
        rs.beforeFirst();
        while(rs.next()) {
           sql.append("(");
            for(int i = 0; i < columnCount; i++) {

                int columnType = metaData.getColumnType(i + 1);
                int columnIndex = i + 1;

                if( columnType == Types.INTEGER || columnType == Types.TINYINT || columnType == Types.BIT) {
                    sql.append(rs.getInt(columnIndex)).append(", ");
                } else if (rs.getString(columnIndex) == null){
                    String val = "null";
                    val = val.replace("'", "\\'");
                    sql.append(val).append(", ");
                } else {
                    String val = !rs.getString(columnIndex).isEmpty() ? rs.getString(columnIndex) : "";
                    val = val.replace("'", "\\'");
                    sql.append("'").append(val).append("', ");
                }
            }

            //now that we're done with a row

            //let's remove the last whitespace and comma
            sql.deleteCharAt(sql.length() - 1).deleteCharAt(sql.length() - 1);

            if(rs.isLast()) {
              sql.append(")");
            } else {
              sql.append("),\n");
            }
        }

        //now that we are done processing the entire row
        //let's add the terminator
        sql.append(";");

        sql.append("\n--\n")
                .append(MysqlBaseService.SQL_END_PATTERN).append(" table insert : ").append(table)
                .append("\n--\n");

        //enable FK constraint
        sql.append("\n/*!40000 ALTER TABLE `").append(table).append("` ENABLE KEYS */;\n");

        return sql.toString();
    }

    private String exportToSql() throws SQLException {

        StringBuilder sql = new StringBuilder();
        sql.append("--");
        sql.append("\n-- Generated by java-mysql-exporter");
        sql.append("\n-- Date: ").append(new SimpleDateFormat("d-M-Y H:m:s").format(new Date()));
        sql.append("\n--");

        //these declarations are extracted from HeidiSQL
        sql.append("\n\n/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;")
        .append("\n/*!40101 SET NAMES utf8 */;")
        .append("\n/*!50503 SET NAMES utf8mb4 */;")
        .append("\n/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;")
        .append("\n/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;");


        //get the tables
        List<String> tables = MysqlBaseService.getAllTables(database, stmt);

        //get the table insert statement for each table
        for (String s: tables) {
            try {
                sql.append(getTableInsertStatement(s.trim()));
                sql.append(getDataInsertStatement(s.trim()));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

       sql.append("\n/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;")
        .append("\n/*!40014 SET FOREIGN_KEY_CHECKS=IF(@OLD_FOREIGN_KEY_CHECKS IS NULL, 1, @OLD_FOREIGN_KEY_CHECKS) */;")
        .append("\n/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;");

        this.generatedSql = sql.toString();
        return sql.toString();
    }

    public void export() throws IOException, SQLException, ClassNotFoundException {

        //check if properties is set or not
        if(!validateProperties()) {
            logger.error("Invalid config properties: The config properties is missing important parameters: DB_NAME, DB_USERNAME and DB_PASSWORD");
            return;
        }

        //connect to the database
        database = properties.getProperty(DB_NAME);
        String jdbcURL = properties.getProperty(JDBC_CONNECTION_STRING, "");
        String driverName = properties.getProperty(JDBC_DRIVER_NAME, "");

        Connection connection;

        if(jdbcURL.isEmpty()) {
            connection = MysqlBaseService.connect(properties.getProperty(DB_USERNAME), properties.getProperty(DB_PASSWORD),
                    database, driverName);
        }
        else {
            if (jdbcURL.contains("?")){
                database = jdbcURL.substring(jdbcURL.lastIndexOf("/") + 1, jdbcURL.indexOf("?"));
            } else {
                database = jdbcURL.substring(jdbcURL.lastIndexOf("/") + 1);
            }
            logger.debug("database name extracted from connection string: " + database);
            connection = MysqlBaseService.connectWithURL(properties.getProperty(DB_USERNAME), properties.getProperty(DB_PASSWORD),
                    jdbcURL, driverName);
        }

        stmt = connection.createStatement();

        //generate the final SQL
        String sql = exportToSql();

        //create a temp dir
        dirName = properties.getProperty(MysqlExportService.TEMP_DIR, dirName);
        File file = new File(dirName);
        if(!file.exists()) {
            boolean res = file.mkdir();
            if(!res) {
                throw new IOException(LOG_PREFIX + ": Unable to create temp dir: " + file.getAbsolutePath());
            }
        }

        //write the sql file out
        File sqlFolder = new File(dirName + "/sql");
        if(!sqlFolder.exists())
            sqlFolder.mkdir();
        sqlFileName = new SimpleDateFormat("d_M_Y_H_mm_ss").format(new Date()) + "_" + database + "_database_dump.sql";
        FileOutputStream outputStream = new FileOutputStream( sqlFolder + "/" + sqlFileName);
        outputStream.write(sql.getBytes());
        outputStream.close();

        //zip the file
        zipFileName = dirName + "/" + sqlFileName.replace(".sql", ".zip");
        generatedZipFile = new File(zipFileName);
        ZipUtil.pack(sqlFolder, generatedZipFile);

        //mail the zipped file if mail settings are available
        if(emailPropertiesSet()) {
            boolean emailSendingRes = EmailService.builder()
                    .setHost(properties.getProperty(EMAIL_HOST))
                    .setPort(Integer.valueOf(properties.getProperty(EMAIL_PORT)))
                    .setToAddress(properties.getProperty(EMAIL_TO))
                    .setFromAddress(properties.getProperty(EMAIL_FROM))
                    .setUsername(properties.getProperty(EMAIL_USERNAME))
                    .setPassword(properties.getProperty(EMAIL_PASSWORD))
                    .setSubject(properties.getProperty(EMAIL_SUBJECT, sqlFileName.replace(".sql", "")))
                    .setMessage(properties.getProperty(EMAIL_MESSAGE, "Please find attached database backup of " + database))
                    .setAttachments(new File[]{new File(zipFileName)})
                    .sendMail();

            if (emailSendingRes) {
                logger.debug(LOG_PREFIX + ": Zip File Sent as Attachment to Email Address Successfully");
            } else {
                logger.error(LOG_PREFIX + ": Unable to send zipped file as attachment to email. See log debug for more info");
            }
        }

        //clear the generated temp files
         clearTempFiles(Boolean.parseBoolean(properties.getProperty(PRESERVE_GENERATED_ZIP, Boolean.FALSE.toString())));

    }

    public void clearTempFiles(boolean preserveZipFile) {

        //delete the temp sql file
        File sqlFile = new File(dirName + "/sql/" + sqlFileName);
        if(sqlFile.exists()) {
            boolean res = sqlFile.delete();
            logger.debug(LOG_PREFIX + ": " + sqlFile.getAbsolutePath() + " deleted successfully? " + (res ? " TRUE " : " FALSE "));
        } else {
            logger.debug(LOG_PREFIX + ": " + sqlFile.getAbsolutePath() + " DOES NOT EXIST while clearing Temp Files");
        }

        File sqlFolder = new File(dirName + "/sql");
        if(sqlFolder.exists()) {
            boolean res = sqlFolder.delete();
            logger.debug(LOG_PREFIX + ": " + sqlFolder.getAbsolutePath() + " deleted successfully? " + (res ? " TRUE " : " FALSE "));
        } else {
            logger.debug(LOG_PREFIX + ": " + sqlFolder.getAbsolutePath() + " DOES NOT EXIST while clearing Temp Files");
        }


       if(!preserveZipFile) {

           //delete the zipFile
           File zipFile = new File(zipFileName);
           if (zipFile.exists()) {
               boolean res = zipFile.delete();
               logger.debug(LOG_PREFIX + ": " + zipFile.getAbsolutePath() + " deleted successfully? " + (res ? " TRUE " : " FALSE "));
           } else {
               logger.debug(LOG_PREFIX + ": " + zipFile.getAbsolutePath() + " DOES NOT EXIST while clearing Temp Files");
           }

           //delete the temp folder
           File folder = new File(dirName);
           if (folder.exists()) {
               boolean res = folder.delete();
               logger.debug(LOG_PREFIX + ": " + folder.getAbsolutePath() + " deleted successfully? " + (res ? " TRUE " : " FALSE "));
           } else {
               logger.debug(LOG_PREFIX + ": " + folder.getAbsolutePath() + " DOES NOT EXIST while clearing Temp Files");
           }
       }

        logger.debug(LOG_PREFIX + ": generated temp files cleared successfully");
    }

    public String getGeneratedSql() {
        return generatedSql;
    }

    public File getGeneratedZipFile() {
        if(generatedZipFile != null && generatedZipFile.exists()) {
            return generatedZipFile;
        }
        return null;
    }
}
