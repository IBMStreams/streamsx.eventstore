# streamsx.eventstore

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
# Table of Contents

- [Description](#description)
- [EventStoreSink](#eventstoresink)
  - [Changes](#changes)
  - [Supported versions](#supported-versions)
  - [Data types](#data-types)
    - [Additional documentation](#additional-documentation)
- [Installation](#installation)
  - [Using the distribution](#using-the-distribution)
  - [Building from source](#building-from-source)
    - [Updating to a new version](#updating-to-a-new-version)
    - [Installing the toolkit from scratch](#installing-the-toolkit-from-scratch)
- [Configuration and setup for sample project](#configuration-and-setup-for-sample-project)
  - [Setting up IBM Db2 Event Store](#setting-up-ibm-db2-eventstore)
  - [Setting up the reference to Event Store daemon on your virtual machine](#setting-up-the-reference-to-the-ibm-db2-event-store-daemon-on-your-virtual-machine)
- [Usage](#usage)
  - [Event Store sink operator parameters](#event-store-sink-operator-parameters)
  - [Optional output port](#optional-output-port)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Description

This toolkit contains operators that enable you to connect IBM Streams to IBM Db2 Event Store.

Currently, this toolkit contains one operator: a file sink operator called EventStoreSink for inserting IBM Streams tuples in to an IBM Db2 Event Store table.

To use this operator, you must have an existing IBM Db2 Event Store database, and the IBM Db2 Event Store cluster or server must be running. 

You can find precompiled EventStoreSink toolkits for various IBM Db2 Event Store RELEASES here:
<https://github.com/IBMStreams/streamsx.eventstore/releases>

# EventStoreSink

**Important**: The tuple field types and positions in the IBM Streams schema must match the field names in your IBM Db2 Event Store table schema _exactly_.

The EventStoreSink operator has three required parameters:

- **connectionString:** The set of IP addresses and port numbers needed to connect to IBM Db2 Event Store.
- **databaseName:** The name of the database, as defined in IBM Db2 Event Store.
- **tableName:** The name of the table into which you want to insert rows.

This sink operator can execute within a Consistent Region.


## Changes
This is the first version of the toolkit, so there are no changes yet!


## Supported versions

- **IBM Streams:** 4.2.0 or later
- **IBM Db2 Event Store:** 1.1.0


## Data types

SPL type            | Support status | Event Store type |
--------            | -------------- | -----------  |
boolean             | Supported      | Boolean |
enum	            | Not supported  | N/A |
int8	            | Supported      | Byte |
int16	            | Supported      | Short |
int32	            | Supported      | Int |
int64	            | Supported      | Long |
uint8	            | Supported      | Byte |
uint16	            | Supported      | Short |
uint32	            | Supported      | Int |
uint64	            | Supported      | Long |
float32	            | Supported      | Float |
float64	            | Supported      | Double |
decimal32	        | Not supported      | N/A |
decimal64	        | Not supported      | N/A |
decimal128	        | Not supported      | N/A |
complex32	        | Not supported  | N/A |
complex64	        | Not supported  | N/A |
timestamp	        | Supported  | java.sql.Timestamp |
rstring	            | Supported      | String |
ustring	            | Supported      | String|
blob     	        | Not supported  | N/A |
xml	                | Not supported  | N/A |
list\<T\>	            | Only one level supported      | Array\<T\> |
bounded list type	| Not supported      | N/A |
set\<T\>          	| Only one level supported      | Array\<T\> |
bounded set type	| Not supported      | N/A |
map\<K,V\>        	| Only one level supported      | Map\<K,V\>|
bounded map type	| Not supported      | N/A |
tuple\<T name, ...\>  | Not supported  | N/A |

In the preceding table "only one level supported" means the array or map has elements and keys that are primitive data types.


### Additional documentation
[Java equivalents for SPL types](http://www.ibm.com/support/knowledgecenter/SSCRJU_4.1.1/com.ibm.streams.dev.doc/doc/workingwithspltypes.html)


# Installation


## Using the distribution

1. Clone the git repository to your VM where IBM Streams is installed.

1. In the home directory, compile the sink operator to make the JAR files, the project, and the target directories, and the toolkit.xml file by running one of the following commands:

   - `sbt toolkit`
   - `./recompile.sh`
1. Use the following steps to use the directory as a toolkit location in Streams Studio:
  - Open IBM Streams Studio.
  - Open the **Streams Explorer** view.
  - Expand the **IBM Streams Installation** section, and expand the **IBM Streams** subsection.
  - Right click on **Toolkit locations**.
  - Select **Add Toolkit Location**.
  - Select the directory where the cloned directory is stored and select **OK**.

**Tip:** If you don't want to use a clone, replace steps 1-3 by using a toolkit release for streamsx.eventstore that corresponds to the IBM Db2 Event Store release in a local directory. In step 4, use the directory where the toolkit is saved as the toolkit location.  


## Building from source

The build instructions assume the following setup:

- IBM Db2 Event Store is running remotely on another machine
- IBM Streams QSE VM running on VirtualBox or similar  


### Updating to a new version

If you already installed the toolkit following the instructions in [Installing the toolkit from scratch](#installing-the-tookit-from-scratch) and  need the new version, complete the following steps:

1. Change to your toolkit folder on your virtual machine and run the following commands:

    ```
    git pull
    sbt ctk toolkit
    ```

    Alternatively, change to the streamsx.eventstore directory and run: 

   `./recompile.sh`

1. Refresh the toolkit location in Streams Studio (as specified in [Using the distribution](#using-the-distribution)).


### Installing the toolkit from scratch

In these instructions, your virtual machine is the Streams QSE VM. These instructions were written for Streams 4.2.0

1. Install SBT on your virtual machine. See instructions for RedHat here: <http://www.scala-sbt.org/0.13/docs/Installing-sbt-on-Linux.html>.
1. Clone the <https://github.com/IBMStreams/streamsx.eventstore> repository on the virtual machine file system. (It doesn't need to be in your Eclipse workspace.)
1. Setup IBM Db2 Event Store on your remote machine and create a local configuration file to reference the remote IBM Db2 Event Store daemon. 
  
    For more information, see [Setting up the reference to the IBM Db2 Event Store daemon on your virtual machine](#setting-up-the-reference-to-the-ibm-db2-event-store-daemon-on-your-virtual-machine)
  
    For more information on installing IBM Db2 Event Store, see <https://www.ibm.com/support/knowledgecenter/SSGNPV/eventstore/welcome.html>.
1. In the top level of the repository, run `sbt toolkit` or `./recompile.sh`. 

1. Create a new IBM Streams project. Add the location of the repository as a toolkit location.
1. Start IBM Db2 Event Store on the remote machine (Depending on the version of IBM Db2 Event Store that you have installed, this could be your local host or your cluster.)
1. Write an IBM Streams test project with EventStoreSink as the sink operator.


# Configuration and setup for sample project

## Setting up IBM Db2 Event Store

For more information on installing IBM Db2 Event Store, see:
<https://www.ibm.com/support/knowledgecenter/SSGNPV/eventstore/welcome.html>

On your IBM Streams system, either download the precomipled toolkit that corresponds to your IBM Db2 Event Store edition or you clone the <https://github.com/IBMStreams/streamsx.eventstore> repository.

  **Note:** If you clone the repository, you might need to edit the `build.sbt` file that is used to compile so that the IBM Db2 Event Store client JAR file corresponds to the IBM Db2 Event Store release where you want to insert data. For example to get the client JAR for IBM Db2 Event Store Enterprise Edition from Maven, the `build.sbt` file has the line:
  
  `"com.ibm.event” % “ibm-db2-eventstore-client” % “1.1.0"`

To use IBM Db2 Event Store Developer Edition version 1.1.2, comment out `"com.ibm.event” % “ibm-db2-eventstore-client” % “1.1.0"` and uncomment the following line:

  `“com.ibm.event” % “ibm-db2-eventstore-desktop-client” % “1.1.2”`
  
Note there are other editions that reside on Maven as well, e.g., Enterprise 1.1.1 and Developer 1.1.4.

## Setting up the reference to the IBM Db2 Event Store daemon on your virtual machine

When you start IBM Db2 Event Store on a remote machine, the daemon should also start automatically. 

To enable the EventStoreSink operator to connect to your remote IBM Db2 Event Store installation, you must determine the connection endpoint string. The connection endpoint string is the set of IP addresses and port numbers that has the form `<hostname>:<portnumber>`. Each entry is separated with a comma. 

Enter this value for the connectionString parameter. For example: `9.26.150.75:1101,9.26.150.76:1101`

**Tip:** To connect to IBM Db2 Event Store Developer Edition, use the external IP address for the work station where IBM Db2 Event Store is running. Use the same port number that is specified in the sample notebooks that are available in the **Community** section of the IBM Db2 Event Store end user client.  

If you are running IBM Db2 Event Store Developer Edition on a Mac, you can find the external IP address in **System Preferences > Network**.


## Event Store sink operator parameters

You can define the following parameters for the Event Store:

- **connectionString** - (required) A string that specifies the IBM Db2 Event Store connection endpoint as a set of IP addresses and ports. Separate multiple entries with a comma. 

- **databaseName** - (required) The name of an existing IBM Db2 Event Store database.

- **tableName** - (required) The name of the table into which you want to insert data from the IBM Streams application. If the table does not exist, the table is automatically created in IBM Db2 Event Store.

- **eventStoreUser** - The user ID to use to conect to IBM DB2 Event Store. If you don't specify the **eventStoreUser** parameter, the `admin` user is used. 

- **eventStorePassword** - The password to use to connect to IBM Db2 Event Store. If you do not specify the **eventStoreUser** paramater, a default is used. 

- **batchSize** - The number of rows that will be batched in the operator before the batch is inserted into IBM Db2 Event Store by using the `batchInsertAsync` method. If you do not specify this parameter, the batchSize defaults to the estimated number of rows that could fit into an 8K memory page.

- **partitioningKey** - A string of attribute names separated by commas. The order of the attribute names defines the order of entries in the sharding key for the IBM Db2 Event Store table. The attribute names are the names of the fields in the stream. The **partitioningKey** parameter is used only if the table does not yet exist in the IBM Db2 Event Store database. If you do not specify this parameter or if the key string is empty, the key defaults to making the first column in the stream as the shard key. For example, "col1, col2, col3"

- **primaryKey** - A string of attribute names separated by commas. The order of the attribute names defines the order of entries in the primary key for the IBM Db2 Event Store table. The attribute names are the names of the fields in the stream. The **primaryKey** parameter is used only if the table does not yet exist in the IBM Db2 Event Store database. If you do not specify this parameter, the resulting table has an empty primary key. 

- **configObject** - A string to use for the application configuration name that can be created using the `streamtool mkappconfig ... <configObject name>`. If you specify parameter values in the configuration object, they override the values that are configured for the EventStoreSink operator. Supported parameters are:
  - eventStoreUser
  - eventStorePassword

- **maxNumActiveBatches** - The number of batches that can be filled and inserted asynchronously. The default is 1. 


## Optional output port

An optional output port exists in the EventStoreSink operator. This output port is intended to output the information on whether a tuple was successful or not when it was inserted into the database. EventStoreSink looks for a Boolean field called "_Inserted_" in the output stream. EventStoreSink sets the field to `true` if the data was successfully inserted and `false` if the insert failed. 

Besides the "_Inserted_" column, the output will include the original tuple that was processed by the EventStoreSink operator. 

The output stream can be used to tie in with an SQS operator to enable it to use the "_Inserted_" flag to remove successfully inserted tuples from the SQS query. If the tuple is not inserted, the tuple must remain in the SQS query so that it can be resubmitted for insertion again. 

To add the output port for the sink operator, add the output port explicitly in the operator properties and define the schema for the output stream. 
