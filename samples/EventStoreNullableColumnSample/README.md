# Event Store Insert Sample with the use of optional types

## Description

This sample application demonstrate how to use the EventStoreSink operator with input stream having optional types.

The application inserts 100 rows into a table with a default batch size of 50 rows.
The output stream of the EventStoreSink operator contains an attribute "_Inserted_" to indicate the result of the insert (true or false).
In the console log you can verify the dumps of these tuples.

## Use

### Add the truststore/keystore file

SSL connection is enabled by default in Event Store 2.0.
You need to get the clientkeystore file and corresponding password from the Event Store Server.

* Create a directory named "opt" in the project directory
* Copy the "clientkeystore" file into the "opt" directory of this sample project to ensure that this file is part of the application bundle.

Set the parameters **trustStore** and **keyStore** to "opt/clientkeystore". The operator will read this file at runtime.

### Build from command line

Build the application from command line (requires STREAMS_INSTALL):

`make`

### Build in Streams Studio

* Import the project into Streams Studio
* Add eventstore toolkit to the toolkit locations
* Configure SPL Build and select as Builder Type "External Builder" if not already active
* "Build project"

### Launch with Streams Console

Open the Streams Console of your Streams Instance and select the application bundle (sab file) to submit the application.

Select the application bundle:

    output/com.ibm.streamsx.eventstore.sample.OptionalTypesSample.sab

In the "Submit job dialog" press the button "Configure", after this the upload begins.

Before submitting the job, you need to apply the values for the submission parameters:

- **connectionString:** The set of IP addresses and port numbers needed to connect to IBM Db2 Event Store.
- **databaseName:** The name of the database, as defined in IBM Db2 Event Store.
- **tableName:** The name of the table into which you want to insert rows.
- **schemaName:** The name of the table schema into which you want to insert rows.
- **eventStoreUser:** The user ID to use to connect to IBM DB2 Event Store.
- **eventStorePassword:** The password of the user set in the parameter "eventStoreUser".
- **keyStore** Location of key store file, for example "opt/clientkeystore"
- **keyStorePassword:** Set password of the key store.
- **trustStore:** Location of trust store file, for example "opt/clientkeystore"
- **trustStorePassword:** Set password of the trust store.

### Launch to Streams in Cloud Pak for Data

Ensure that the sab has been built with Streams 4.3.1 or later when using optional types.

Submit the application bundle and specify the submission parameter, for example the application configuration name and keystore/truststore file:

    streamsx-streamtool --disable-ssl-verify submitjob --P configObject=eventstore --P keyStore=opt/clientkeystore --P trustStore=opt/clientkeystore output/com.ibm.streamsx.eventstore.sample.OptionalTypesSample.sab


## Utilized Toolkits
 - com.ibm.streamsx.eventstore

