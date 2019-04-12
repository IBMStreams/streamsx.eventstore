# streamsx.eventstore

This toolkit contains operators that enable you to connect IBM Streams to IBM Db2 Event Store.

Currently, this toolkit contains one operator: An operator called EventStoreSink for inserting IBM Streams tuples in to an IBM Db2 Event Store table.

To use this operator, you must have an existing IBM Db2 Event Store database, and the IBM Db2 Event Store cluster or server must be running. 

You can find precompiled EventStoreSink toolkits for various IBM Db2 Event Store RELEASES here:
<https://github.com/IBMStreams/streamsx.eventstore/releases>


## Supported versions

IBM Db2 Event Store            | [com.ibm.streamsx.eventstore toolkit version](https://github.com/IBMStreams/streamsx.eventstore/releases) | [streamsx.eventstore python package](https://pypi.org/project/streamsx.eventstore/) |
--------            | -------------- | -----------  |
Enterprise 2.0.0	            | 2.x  | 2.x |
Enterprise 1.1.3             | 1.x      | 1.x |
Developer 1.1.4	            | 1.x  | 1.x |


## Setting up IBM Db2 Event Store

For more information on installing IBM Db2 Event Store, see:
<https://www.ibm.com/support/knowledgecenter/SSGNPV>

## Build the toolkit

[Build](BUILD.md)

## Documentation

Find the full documentation [here](https://ibmstreams.github.io/streamsx.eventstore/).

### Python package 

There is a python package available, that exposes SPL operators in the `com.ibm.streamsx.eventstore` toolkit as Python methods.
* [streamsx.eventstore python package](https://pypi.org/project/streamsx.eventstore/)
* [Python package documentation](http://streamsxeventstore.readthedocs.io)
