# Event Store Insert Sample

## Description

This sample application demonstrates how to use the EventStoreSink operator with optional output port.

- **connectionString:** The set of IP addresses and port numbers needed to connect to IBM Db2 Event Store.
- **databaseName:** The name of the database, as defined in IBM Db2 Event Store.
- **tableName:** The name of the table into which you want to insert rows.
- **schemaName:** The name of the table schema into which you want to insert rows.


## Use

Build the application:

`make`

Run:

`streamtool submitjob output/com.ibm.streamsx.eventstore.sample.InsertSample.sab -P connectionString="<host>:30780;<host>:1101" -P schemaName=<your-schema> -P eventStoreUser=<your-user> -P eventStorePassword=<your-password>`


## Utilized Toolkits
 - com.ibm.streamsx.eventstore
