# Event Store Insert Sample

## Description

This sample application demonstrates how to use the EventStoreSink operator with optional output port.

- **connectionString:** The set of IP addresses and port numbers needed to connect to IBM Db2 Event Store.
- **databaseName:** The name of the database, as defined in IBM Db2 Event Store.
- **tableName:** The name of the table into which you want to insert rows.

This sample is using "TESTDB" as database name and "ReviewTable" as table name.
If table does not exist, then the table is created by the EventStoreSink operator.

## Use

Build the application:

`make`

Run:

`./output/bin/standalone connectionString=<host>:<port>`

## Utilized Toolkits
 - com.ibm.streamsx.eventstore
