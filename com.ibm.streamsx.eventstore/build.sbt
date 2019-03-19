import scala.language.postfixOps // <- making IntelliJ hush about the ! bash command postfix

name := "streamsx.eventstore"
organization := "com.ibm"
version := "0.4.0-RELEASE"
scalaVersion := "2.11.8"
scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature")
compileOrder in Compile := CompileOrder.ScalaThenJava

val circeVersion           = "0.3.0"
val ibmStreamsVersion      = "4.2.0.0"
val jodaTimeVersion        = "2.9.4"
val junitVersion           = "4.10"
val log4jVersion           = "2.2"
val scalacheckVersion      = "1.11.4"
val scalatestVersion       = "2.2.2"
val scalazVersion          = "7.1.2"
val slf4jVersion           = "1.7.12"
val streamsOperatorVersion = "4.2.0.0"
val curatorVersion         = "2.11.0"
val zooKlientVersion       = "0.3.1-RELEASE"
val streamsxUtilVersion    = "0.2.5-RELEASE"
val sparkver = 		"2.1.0"

parallelExecution in Test := false

libraryDependencies ++= Seq(
  // The maven entry for the IBM Db2 Event Store client jar 
  // Comment in only the one that corresponds to the IBM Db2 Event Store version
  //"com.ibm.event" % "ibm-db2-eventstore-client" % "1.1.0", // For IBM Db2 Event Store Enterprise edition v1.1.0
  //"com.ibm.event" % "ibm-db2-eventstore-client" % "1.1.1", // For IBM Db2 Event Store Enterprise edition v1.1.1
  //"com.ibm.event" % "ibm-db2-eventstore-client" % "1.1.2", // For IBM Db2 Event Store Enterprise edition v1.1.2
  //"com.ibm.event" % "ibm-db2-eventstore-client" % "1.1.3", // For IBM Db2 Event Store Enterprise edition v1.1.3
  //"com.ibm.event" % "ibm-event-desktop" % "1.1.1", // For IBM Db2 Event Store Developer edition v1.1.1
  //"com.ibm.event" % "ibm-db2-eventstore-desktop-client" % "1.1.2", // For IBM Db2 Event Store Developer edition v1.1.2
  "com.ibm.event" % "ibm-db2-eventstore-desktop-client" % "1.1.4", // For IBM Db2 Event Store Developer edition v1.1.4

  "com.google.protobuf" % "protobuf-java" % "2.5.0",
  "org.apache.spark" %% "spark-core" % sparkver intransitive(), 

  "org.apache.spark" %% "spark-unsafe" % sparkver intransitive(), 

  "org.apache.spark" %% "spark-catalyst" % sparkver intransitive(), 

  "org.apache.spark" %% "spark-sql" % sparkver  intransitive(), 

  "org.apache.spark" %% "spark-streaming" % sparkver % "provided",

  "org.apache.spark" %% "spark-hive" % sparkver % "provided",
  "org.apache.hadoop" % "hadoop-core" % "0.20.2" intransitive(),
  "com.esotericsoftware.kryo" % "kryo" % "2.16",
  "org.apache.commons" % "commons-lang3" % "3.3.2",
  "org.json4s" % "json4s-ast_2.11" % "3.2.10",
  "org.json4s" %% "json4s-jackson" % "3.2.11",

  "org.apache.spark" %% "spark-streaming-kafka-0-10" % sparkver % "provided",

  "org.apache.curator" % "curator-client" % curatorVersion % "provided",
  "org.apache.curator" % "curator-recipes" % curatorVersion, // % "provided",

  "io.netty" % "netty-all" % "4.0.29.Final",

  "org.slf4j"                   % "slf4j-log4j12"              % slf4jVersion, // % "provided",

  "io.circe"                    %% "circe-core"            % circeVersion, //  % "provided",
  "io.circe"                    %% "circe-generic"         % circeVersion, // % "provided",
  "io.circe"                    %% "circe-jawn"            % circeVersion, // % "provided",
  "org.apache.curator"           % "curator-framework"     % curatorVersion,// % "provided",
  "org.scalaz"                  %% "scalaz-core"           % scalazVersion, // % "provided",
  "org.apache.logging.log4j"    % "log4j-api"              % log4jVersion, // % "provided",
  "org.slf4j"                   % "slf4j-api"              % slf4jVersion, // % "provided",
  "joda-time"                   % "joda-time"              % jodaTimeVersion, // % "provided",
  "junit"                       % "junit"                  % junitVersion            % "test",
  "org.scalacheck"              %% "scalacheck"            % scalacheckVersion       % "test",
  "org.scalatest"               %% "scalatest"             % scalatestVersion        % "test",
  "org.slf4j"                   % "slf4j-simple"           % slf4jVersion            % "test",
  "org.apache.curator"          % "curator-test"           % curatorVersion          % "test"
)

val jarFn = "streamsx.eventstore.jar"
val libDir = "impl/lib"

cleanFiles <+= baseDirectory { base => base / "com.ibm.streamsx.eventstore" }

def rmToolkit(u: Unit): Unit = "spl-make-toolkit -c -i ." !

val ctk = TaskKey[Unit]("ctk", "Cleans the SPL toolkit")
ctk <<= clean map rmToolkit

def mkToolkit(jar: sbt.File): Unit = "spl-make-toolkit -i ." ! match {
  case 0 => s"cp -p ${jar.getPath} $libDir/$jarFn" !
  case _ => sys.error(s"not copying $jarFn bc toolkit creation failed")
}

val dist = TaskKey[Unit]("dist", "Makes a distribution for the toolkit")
dist := {
  val dir = baseDirectory.value.getName
  val parent = baseDirectory.value.getParent
  val excludes = Seq(
    "build.sbt",
    "data",
    "recompile.sh",
    "scalastyle-config.xml",
    "lib/com.ibm.streams.operator.jar",
    "output",
    "project",
    "src",
    "target",
    ".apt_generated",
    ".classpath",
    ".git",
    ".gitignore",
    ".gitkeep",
    ".project",
    ".settings",
    ".toolkitList"
  ).map(d => s"--exclude=$d").mkString(" ")
  s"tar -zcf $parent/${name.value}_${version.value}.tgz -C $parent $dir $excludes" !
}

test in assembly := {}

val toolkit = TaskKey[Unit]("toolkit", "Makes the SPL toolkit")
toolkit <<= assembly map mkToolkit
dist <<= dist.dependsOn(toolkit)

(fullClasspath in Test) := (fullClasspath in Test).value ++ Seq(
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/com.ibm.streams.install.dependency.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/com.ibm.streams.management.jmxmp.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/com.ibm.streams.operator.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/com.ibm.streams.operator.samples.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/com.ibm.streams.management.mx.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/com.ibm.streams.resourcemgr.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/com.ibm.streams.resourcemgr.symphony.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/com.ibm.streams.resourcemgr.utils.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/com.ibm.streams.resourcemgr.yarn.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/com.ibm.streams.security.authc.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/com.ibm.streams.spl.expressions.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/streams.domainmgr.base.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/streams.domainmgr.server.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/streams.sws.annotation.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/streams.sws.base.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/streams.sws.bi.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/streams.sws.client.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/streams.sws.dsmutils.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/streams.sws.internal.jar")),
  Attributed.blank(file(s"/opt/ibm/InfoSphere_Streams/$ibmStreamsVersion/lib/streams.sws.tools.jar")) 
)
