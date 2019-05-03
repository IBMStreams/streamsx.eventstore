---
title: "Toolkit Development overview"
permalink: /docs/developer/overview/
excerpt: "Contributing to this toolkits development."
last_modified_at: 2018-11-20T12:37:48-04:00
redirect_from:
   - /theme-setup/
sidebar:
   nav: "developerdocs"
---
{% include toc %}
{% include editme %}


# Develop com.ibm.streamsx.eventstore toolkit

## Downloading the streamsx.eventstore toolkit

Download the full toolkit requires git. Enter a directory of your choice and execute :

`cd yourDirectory`

`git clone https://github.com/IBMStreams/streamsx.eventstore.git`

## Setting up IBM Db2 Event Store

For more information on installing IBM Db2 Event Store, see:
<https://www.ibm.com/support/knowledgecenter/SSGNPV>

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


## Build the toolkit

Change to your toolkit folder `com.ibm.streamsx.eventstore` and run the following commands:

```
sbt ctk toolkit
```

Alternatively, change to the `com.ibm.streamsx.eventstore` directory and run: 

```
./recompile.sh
```

## Test the toolkit (version 1.x) with Developer Edition (1.x)

Set the following environment variables for the toolkit location and the connection string:

```
export STREAMSX_EVENTSTORE_TOOLKIT=../../com.ibm.streamsx.eventstore
export EVENTSTORE_CONNECTION=<ip>:<port>
```

For Db2 Event Store Developer edition the port is `1100`.

You must have an existing IBM Db2 Event Store database, and the IBM Db2 Event Store cluster or server must be running. 

Run the test suite on your local Streams instance:

`cd tests/spl-tests`

`ant test`

