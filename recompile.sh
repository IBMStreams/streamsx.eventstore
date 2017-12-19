#!/bin/bash

# NOTE: Before compiling, make sure you make a directory impl/lib under the home directory

#Script to create the streamsx.eventstore toolkit for IBM Streams use

# make sure this is not deleted
#git checkout com.ibm.streamsx.eventstore/EventStoreSink/EventStoreSink.xml

# Make sure the librarydependencies contains the proper IBM Db2 Event Store client
# Note that this is done by commenting in the IBM Db2 Event Store client
#   library dependency in build.sbt

# cleanup before making the toolkit
rm toolkit.xml
rm impl/lib/streamsx.eventstore.jar
rm -rf target
rm -rf project/project
rm -rf project/target

# compile the streamsx.eventstore toolkit
sbt toolkit 
