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
public class MySqlExportService {

    private Connection connection;
    private Statement stmt;
    private String driver = "com.mysql.jdbc.Driver";
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




    public MySqlExportService(Properties properties) {
        this.properties = properties;
    }

    private boolean validateProperties() {
        return properties != null &&
                properties.containsKey(DB_USERNAME) &&
                properties.containsKey(DB_PASSWORD) &&
                properties.containsKey(DB_NAME);
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

    private void connect(String username, String password) {
        try {
            String url = "jdbc:mysql://localhost:3306/" + database;
            Class.forName(driver);
            connection = DriverManager.getConnection(url, username, password);
            stmt = connection.createStatement();
            logger.debug("DB Connected Successfully");
        } catch (ClassNotFoundException e1) {
            logger.error(LOG_PREFIX + ": The database driver [" + driver + "] is not found! " +
                    "Please first make sure the dependencies are resolved and check that mysql-connector-java:5.1.45 is in your " +
                    "classpath. If it persists, you can report this as an issue on github \n" +
                    "https://github.com/SeunMatt/java-mysql-exporter/issues \n" +
                    e1.getLocalizedMessage()
                );
            e1.printStackTrace();
        } catch (SQLException e2) {
          logger.error(LOG_PREFIX + ": SQLException occurred while trying to connect to database " + database + " " +
                  "Please ensure the DB_USERNAME and DB_PASSWORD properties are correct. \n" +
          e2.getLocalizedMessage());
          e2.printStackTrace();
        }
    }

    private List<String> getAllTables() throws SQLException {
        List<String> table = new ArrayList<>();
        ResultSet rs;
        rs = stmt.executeQuery("SHOW TABLE STATUS FROM `" + database + "`;");
        while ( rs.next() ) {
            table.add(rs.getString("Name"));
        }
        return table;
    }

    private String getTableInsertStatement(String table) throws SQLException {

        StringBuilder sql = new StringBuilder();
        ResultSet rs;
        if(table != null && !table.isEmpty()){
          rs = stmt.executeQuery("SHOW CREATE TABLE `" + database + "`.`" + table + "`;");
          while ( rs.next() ) {
                String qtbl = rs.getString(1);
                String query = rs.getString(2);
                sql.append("\r\n\r\n--");
                sql.append("\r\n-- Table dump : ").append(qtbl);
                sql.append("\r\n--\r\n\r\n");
                sql.append(query).append(";\r\n\r\n");
          }
        }
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

        sql.append("\r\n--").append("\r\n-- Inserts of ").append(table).append("\r\n--\r\n\r\n");

        //temporarily disable foreign key constraint
        sql.append("\r\n/*!40000 ALTER TABLE `").append(table).append("` DISABLE KEYS */;\r\n");

        sql.append("INSERT INTO `").append(table).append("`(");

        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();

        for(int i = 0; i < columnCount; i++) {
           sql.append("`")
                   .append(metaData.getColumnName( i + 1))
                   .append("`, ");
        }

        //remove the last whitespace and comma
        sql.deleteCharAt(sql.length() - 1).deleteCharAt(sql.length() - 1).append(") VALUES \r\n");

        //build the values
        rs.beforeFirst();
        while(rs.next()) {
           sql.append("(");
            for(int i = 0; i < columnCount; i++) {

                int columnType = metaData.getColumnType(i + 1);
                int columnIndex = i + 1;

                if( columnType == Types.INTEGER || columnType == Types.TINYINT || columnType == Types.BIT) {
                    sql.append(rs.getInt(columnIndex)).append(", ");
                } else {
                    String val = rs.getString(columnIndex) != null ? rs.getString(columnIndex) : "";
                    sql.append("'").append(val).append("', ");
                }
            }

            //now that we're done with a row

            //let's remove the last whitespace and comma
            sql.deleteCharAt(sql.length() - 1).deleteCharAt(sql.length() - 1);

            if(rs.isLast()) {
              sql.append(")");
            } else {
              sql.append("),\r\n");
            }
        }

        //now that we are done processing the entire row
        //let's add the terminator
        sql.append(";");

        //enable FK constraint
        sql.append("\r\n/*!40000 ALTER TABLE `").append(table).append("` ENABLE KEYS */;\r\n");

        return sql.toString();
    }

    private String exportToSql() throws SQLException {

        StringBuilder sql = new StringBuilder();
        sql.append("--");
        sql.append("\r\n-- Generated by java-mysql-exporter");
        sql.append("\r\n-- Date: ").append(new SimpleDateFormat("d-M-Y H:m:s").format(new Date()));
        sql.append("\r\n--");

        //these declarations are extracted from HeidiSQL
        sql.append("\r\n\r\n/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;")
        .append("\r\n/*!40101 SET NAMES utf8 */;")
        .append("\r\n/*!50503 SET NAMES utf8mb4 */;")
        .append("\r\n/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;")
        .append("\r\n/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;");


        //get the tables
        List<String> tables = getAllTables();

        //get the table insert statement for each table
        for (String s: tables) {
            try {
                sql.append(getTableInsertStatement(s.trim()));
                sql.append(getDataInsertStatement(s.trim()));
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

       sql.append("\r\n/*!40101 SET SQL_MODE=IFNULL(@OLD_SQL_MODE, '') */;")
        .append("\r\n/*!40014 SET FOREIGN_KEY_CHECKS=IF(@OLD_FOREIGN_KEY_CHECKS IS NULL, 1, @OLD_FOREIGN_KEY_CHECKS) */;")
        .append("\r\n/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;");

        this.generatedSql = sql.toString();
        return sql.toString();
    }

    public void export() {

        //check if properties is set or not
        if(!validateProperties()) {
            logger.error("Invalid config properties: The config properties is missing important parameters: DB_NAME, DB_USERNAME and DB_PASSWORD");
            return;
        }

        //connect to the database
        this.database = properties.getProperty(DB_NAME);
        connect(properties.getProperty(DB_USERNAME), properties.getProperty(DB_PASSWORD));

        //generate the final SQL
        String sql;
        try {
            sql = exportToSql();
        } catch (SQLException e) {
            logger.error(LOG_PREFIX + ": Error while generating SQL content from database: " + e.getLocalizedMessage() +
                    "\nSQL STATE: " + e.getSQLState());
            e.printStackTrace();
            return;
        }

        //create a temp dir
        File file = new File(dirName);
        if(!file.exists()) {
            boolean res = file.mkdir();
            if(!res) {
                logger.error(LOG_PREFIX + ": Unable to create temp dir: " + file.getAbsolutePath());
                return;
            }
        }

        //write the sql file out
        sqlFileName = new SimpleDateFormat("d_M_Y_H_mm_ss").format(new Date()) + "_" + database + "_database_dump.sql";
        try (FileOutputStream outputStream = new FileOutputStream(file + "/" + sqlFileName)) {
            outputStream.write(sql.getBytes());
        } catch (IOException e) {
            logger.error(LOG_PREFIX + ": IO Error while writing SQL Statements to file: " + sqlFileName + ".\n" + e.getLocalizedMessage());
            e.printStackTrace();
            return;
        }

        //zip the file
        zipFileName = sqlFileName.replace(".sql", ".zip");
        generatedZipFile = new File(zipFileName);
        ZipUtil.pack(new File(dirName), generatedZipFile);

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

    public void clearTempFiles(boolean preserveZip) {

        //delete the temp sql file
        File sqlFile = new File(dirName + "/" + sqlFileName);
        if(sqlFile.exists())
            sqlFile.delete();

        //delete the folder
        File folder = new File(dirName);
        if(folder.exists())
            folder.delete();

        //delete the zipFile
        if(!preserveZip) {
            logger.debug(LOG_PREFIX + ": Deleting generated zip file");
            File zipFile = new File(zipFileName);
            if (zipFile.exists())
                zipFile.delete();
            logger.debug(LOG_PREFIX + ": Generated Zip File Deleted");
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
