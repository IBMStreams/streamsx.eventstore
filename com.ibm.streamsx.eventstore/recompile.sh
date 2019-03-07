#!/bin/bash

#Script to create the streamsx.eventstore toolkit for IBM Streams use

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

spl-make-toolkit -i .
