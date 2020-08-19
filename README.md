# streamsx.eventstore

This toolkit contains operators that enable you to connect IBM Streams to IBM Db2 Event Store.

Currently, this toolkit contains one operator: An operator called EventStoreSink for inserting IBM Streams tuples in to an IBM Db2 Event Store table.

To use this operator, you must have an existing IBM Db2 Event Store database, and the IBM Db2 Event Store cluster or server must be running. 

You can find precompiled EventStoreSink toolkits for various IBM Db2 Event Store RELEASES here:
<https://github.com/IBMStreams/streamsx.eventstore/releases>


## Supported versions

IBM Db2 Event Store            | [com.ibm.streamsx.eventstore toolkit version](https://github.com/IBMStreams/streamsx.eventstore/releases) | [streamsx.eventstore python package](https://pypi.org/project/streamsx.eventstore/) |
--------            | -------------- | -----------  |
Enterprise 2.0.0	            | [2.x](https://github.com/IBMStreams/streamsx.eventstore/releases/latest)  | [2.x](https://pypi.org/project/streamsx.eventstore/) |
Enterprise 1.1.3             | [1.2.0](https://github.com/IBMStreams/streamsx.eventstore/releases/tag/v1.2.0-Enterprise-v1.1.3)      |  [1.1.0](https://pypi.org/project/streamsx.eventstore/1.1.0/) |
Developer 1.1.4	            | [1.2.0](https://github.com/IBMStreams/streamsx.eventstore/releases/tag/v1.2.0-Developer-v1.1.4)  |  [1.1.0](https://pypi.org/project/streamsx.eventstore/1.1.0/) |


## Changes
[CHANGELOG.md](com.ibm.streamsx.eventstore/CHANGELOG.md)

## Setting up IBM Db2 Event Store

For more information on installing IBM Db2 Event Store, see:
<https://www.ibm.com/support/knowledgecenter/SSGNPV>

## Documentation

* [Connect to Db2 Event Store 2.0](https://community.ibm.com/community/user/cloudpakfordata/viewdocument/connect-to-db2-event-store)
* [Build the toolkit](BUILD.md)
* [SPLDoc 2.x](https://ibmstreams.github.io/streamsx.eventstore/doc/spldoc2.0/html/)
* [SPLDoc 1.x](https://ibmstreams.github.io/streamsx.eventstore/doc/spldoc1.0/html/)
* [Python package documentation](http://streamsxeventstore.readthedocs.io)

### Remark

This toolkit implements the NLS feature. Use the guidelines for the message bundle that are described in the [Messages and National Language Support for toolkits](https://github.com/IBMStreams/administration/wiki/Messages-and-National-Language-Support-for-toolkits) document.


