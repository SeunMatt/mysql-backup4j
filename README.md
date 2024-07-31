mysql-backup4j
==============

[![SeunMatt](https://circleci.com/gh/SeunMatt/request-validator.svg?style=svg)](https://github.com/SeunMatt/mysql-backup4j)

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.smattme/mysql-backup4j/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.smattme/mysql-backup4j/badge.svg)

mysql-backup4j is a library for programmatically exporting mysql databases 
and sending the zipped dump to email, Amazon S3, Google Drive or any other cloud storage of choice

**It gives the developer access to the generated zip file and the generated SQL query string**
 for use in other part of the application. 

**It also provides a method for importing the SQL exported by the tool - programmatically.**

Installation
============
The artifact is available on Maven Central and can be added to the project's pom.xml:

```xml
<dependency>
    <groupId>com.smattme</groupId>
    <artifactId>mysql-backup4j</artifactId>
    <version>1.2.1</version>
</dependency>
```

The latest version can be found [here](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.smattme%22%20a%3A%22mysql-backup4j%22)

Usage
=====
The minimum configuration required for the library is the database name, username and password.

However, if you want the backup file to be sent to your email automatically after backup, you must 
provide email configurations as well.

```java
//required properties for exporting of db
Properties properties = new Properties();
properties.setProperty(MysqlExportService.DB_NAME, "database-name");
properties.setProperty(MysqlExportService.DB_USERNAME, "root");
properties.setProperty(MysqlExportService.DB_PASSWORD, "root");
properties.setProperty(MysqlExportService.DB_HOST, "localhost");
properties.setProperty(MysqlExportService.DB_PORT, "3306");
        
//properties relating to email config
properties.setProperty(MysqlExportService.EMAIL_HOST, "smtp.mailtrap.io");
properties.setProperty(MysqlExportService.EMAIL_PORT, "25");
properties.setProperty(MysqlExportService.EMAIL_USERNAME, "mailtrap-username");
properties.setProperty(MysqlExportService.EMAIL_PASSWORD, "mailtrap-password");
properties.setProperty(MysqlExportService.EMAIL_FROM, "test@smattme.com");
properties.setProperty(MysqlExportService.EMAIL_TO, "backup@smattme.com");

//optional email configs
properties.setProperty(MysqlExportService.EMAIL_SSL_PROTOCOLS, "TLSv1.2");
properties.setProperty(MysqlExportService.EMAIL_SMTP_AUTH_ENABLED, "true");
properties.setProperty(MysqlExportService.EMAIL_START_TLS_ENABLED, "true");

//set the outputs temp dir
properties.setProperty(MysqlExportService.TEMP_DIR, new File("external").getPath());

MysqlExportService mysqlExportService = new MysqlExportService(properties);
mysqlExportService.export();
```

Calling `mysqlExportService.export();` will export the database and save the dump temporarily in the configured `TEMP_DIR`

If an email config is supplied, the dump will be sent as an attachment. Finally, when all operations are completed the 
temporary dir is cleared and deleted.

If you want to get the generated backup file as a Java `File` object, you need to specify this property as part of the 
configuration:

```java
//...
properties.setProperty(MysqlExportService.PRESERVE_GENERATED_ZIP, "true");
properties.setProperty(MysqlExportService.PRESERVE_GENERATED_SQL_FILE, "true");
```

and then you can call this method:

```java
File file = mysqlExportService.getGeneratedZipFile();
```

Finally, let's say for some reason you want the generated SQL string you can do this:

```java
String generatedSql = mysqlExportService.getGeneratedSql();
```

Other parameters are:

```java
properties.setProperty(MysqlExportService.ADD_IF_NOT_EXISTS, "true");
properties.setProperty(MysqlExportService.JDBC_DRIVER_NAME, "com.mysql.cj.jdbc.Driver");
properties.setProperty(MysqlExportService.JDBC_CONNECTION_STRING, "jdbc:mysql://localhost:3306/database-name?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false");
```

They are explained in a detailed manner in this [tutorial](https://smattme.com/blog/technology/how-to-backup-mysql-database-programmatically-using-mysql-backup4j)

Importing a Database
--------------------
To import a database, you need to use the ImportService like so:

```java
String sql = new String(Files.readAllBytes(Paths.get("path/to/sql/dump/file.sql")));

boolean res = MysqlImportService.builder()
        .setDatabase("database-name")
        .setSqlString(sql)
        .setUsername("root")
        .setPassword("root")
        .setHost("localhost")
        .setPort("3306")
        .setDeleteExisting(true)
        .setDropExisting(true)
        .importDatabase();
        
assertTrue(res);
```

First get SQL as a String and then pass it to the import service with the right configurations.

Alternatively, you can also use the `.setJdbcConnString(jdbcURL)` method on the import service.

e.g. 
```java
boolean res = MysqlImportService.builder()
                .setSqlString(generatedSql)
                .setJdbcConnString("jdbc:mysql://localhost:3306/backup4j_test?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=UTC&useSSL=false")
                .setUsername("db-username")
                .setPassword("db-password")
                .setDeleteExisting(true)
                .setDropExisting(true)
                .importDatabase();
```

`setDeleteExisting(true)` will **delete all data** from existing tables in the target database. 

While `setDropExisting(true)` will **drop** the table. 

Supplying `false` to these functions will disable their respective actions.


**NOTE: The import service is only guaranteed to work with SQL files generated by the export service of this library**

CHANGELOG
=========
v1.2.1
    - Raises a new runtime exception `MysqlBackup4JException` if the required properties are not configured

Author
======
Seun Matt [smattme.com](https://smattme.com) with :green_heart:

Contributions and Support
=========================
If you want to create a new feature, though not compulsory, but it will be helpful to reach out to me first before proceeding.

To avoid a scenario where you submit a PR for an issue that someone else is working on already.


Tutorials / Articles
====================
- [https://smattme.com/blog/technology/how-to-backup-mysql-database-programmatically-using-mysql-backup4j](https://smattme.com/blog/technology/how-to-backup-mysql-database-programmatically-using-mysql-backup4j)