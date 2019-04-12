For developers of this toolkit:

This toolkit uses Apache Ant 1.8 (or later) to build.

Internally Apache Maven 3.2 (or later) and Make are used.

Download and setup directions for Apache Maven can be found here: http://maven.apache.org/download.cgi#Installation

The top-level build.xml contains two main targets:

* all - Builds and creates SPLDOC for the toolkit and samples. Developers should ensure this target is successful when creating a pull request.
* build-all-samples - Builds all samples. Developers should ensure this target is successful when creating a pull request.

# Prerequisite

Download and extract SCALA 2.11.8: http://downloads.typesafe.com/scala/2.11.8/scala-2.11.8.tgz

* Set environment variable M2_HOME to the path of maven home directory.
* Set environment variable SCALA_HOME to the path of scala home directory.

