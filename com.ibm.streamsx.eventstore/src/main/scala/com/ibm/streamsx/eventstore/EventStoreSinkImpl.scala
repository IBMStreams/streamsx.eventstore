package com.ibm.streamsx.eventstore

import com.ibm.streams.operator.Tuple
import com.ibm.streams.operator.StreamSchema
import com.ibm.streams.operator.Attribute
import com.ibm.streams.operator.Type
import com.ibm.streams.operator.meta.{MapType, CollectionType}
import org.apache.spark.sql.Row
import com.ibm.streamsx.eventstore.exception.EventStoreWriterException
import com.ibm.streams.operator.logging.LoggerNames;
import com.ibm.streams.operator.logging.TraceLevel;
import org.apache.log4j.Logger;

import com.ibm.event.common.ConfigurationReader
import com.ibm.event.oltp.{InsertResult, EventContext}
import com.ibm.event.catalog.TableSchema
import com.ibm.event.catalog.ResolvedTableSchema
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.collection.JavaConverters._
import org.apache.spark.sql.types._
import org.apache.spark.sql.types.{MapType => ScalaMapType}
import java.io._
import scala.util.parsing.json.JSON

object EventStoreSinkImpl {
  private val log = Logger.getLogger("EventStoreSinkImpl")//EventStoreSink.class.getName());

  // refactor to split up the zk stuff and EventStore stuff for 6-lining
  def mkWriter(databaseName : String, tableName: String, schemaName: String,
		connectionString: String,
                frontEndConnectionFlag : Boolean,
		streamSchema: StreamSchema,
                eventStoreUser: String, eventStorePassword: String,
                partitioningKey: String, primaryKey: String,
                sslConnection: Boolean, trustStore: String, trustStorePassword: String, keyStore: String, keyStorePassword: String,
                pluginName: String, pluginFlag: Boolean): EventStoreSinkImpl = {
    log.trace("Initializing the Event Store writer operator")

    if( databaseName == null || databaseName.isEmpty() ||
		tableName == null || tableName.isEmpty() ){
       throw EventStoreWriterException( s"Database or table name is empty", new Exception)
    } else if( streamSchema.getAttributeCount == 0 ){
       throw EventStoreWriterException( s"Invalid empty input port schema", new Exception)
    }

    try {
      log.trace( "CONNECTION info ****** connectionString= " + connectionString)
      log.trace( "CONNECTION info ****** databaseName= " + databaseName +
	" tablename= " + tableName )
      new EventStoreSinkImpl(databaseName, tableName, schemaName,
		connectionString, frontEndConnectionFlag, streamSchema, 
                eventStoreUser, eventStorePassword,
                partitioningKey, primaryKey,
                sslConnection, trustStore, trustStorePassword, keyStore, keyStorePassword,
                pluginName, pluginFlag)
    } catch { case e: Exception => 
      log.error("Bad connection")
      throw e 
    }
  }
}

/* This class is used to connect to IBM Db2 Event Store, create a table if none
 * exists, and insert batches of rows using the Event Store client APIs from the EventContext class.
 */
class EventStoreSinkImpl(databaseName : String, tableName: String, schemaName: String,
                         connectionString: String, 
                         frontEndConnectionFlag: Boolean, 
                         streamSchema: StreamSchema,
                         eventStoreUser: String, eventStorePassword: String,
                         partitioningKey: String, primaryKey: String,
                         sslConnection: Boolean, trustStore: String, trustStorePassword: String, keyStore: String, keyStorePassword: String,
                         pluginName: String, pluginFlag: Boolean) {
  protected val log = Logger.getLogger("EventStoreSinkImpl")//EventStoreSink.class.getName());

  var context: EventContext = null
  var tableToInsert : ResolvedTableSchema = null
  var conversionFunctionMap : scala.collection.mutable.HashMap[Int,(Tuple, Int) => Any] = null
  var notConnectedMode = false

  import org.apache.log4j.{Level, LogManager}
  val logLevel = LogManager.getRootLogger().getLevel()
  log.info("Root logger level = " + logLevel)
  // suppress traces from com.ibm.event on INFO level
  if ((logLevel == Level.DEBUG) || (logLevel == Level.TRACE)) {
    LogManager.getLogger("com.ibm.event").setLevel(logLevel)
    LogManager.getLogger("org.apache.spark.sql.ibm.event").setLevel(logLevel)
    LogManager.getLogger("io.netty").setLevel(logLevel)
  }
  else {
    LogManager.getLogger("org.apache.spark.sql.ibm.event").setLevel(Level.ERROR)
    LogManager.getLogger("com.ibm.event").setLevel(Level.ERROR)
    LogManager.getLogger("io.netty").setLevel(Level.OFF)
  }
  if( connectionString == "__NOT_CONNECTED_MODE__" ){
     notConnectedMode = true
  }
  else {
 try {
     connectToDatabase(true)

     // Now get the table from the EventContext to make sure it exists
     // and it it exists then validate the schema as well, otherwise if
     // the table does not exist then try and create the table with the
     // stream schema and the input table name.
     try {
         tableToInsert = context.getTable(tableName)
         log.info( "Successful resolution of table: " + tableName)

     } catch { case e: Exception =>
         log.error( "Bad getTable for table: " + tableName )
         val sw = new StringWriter
         e.printStackTrace(new PrintWriter(sw))
         log.error(sw.toString)

         // For now assume that the exception exists due to the fact the
         // table does not exist so create the table
         createTableFromStream(tableName, streamSchema,
              partitioningKey, primaryKey)

         // On successful recreation try to get the table again
         tableToInsert = context.getTable(tableName)
         log.info( "Successful resolution of table: " + tableName)
     } 

     // Now validate the schema between the input stream and the EventStore table
     validateSchemas(streamSchema, tableToInsert.schema)
     conversionFunctionMap = ConversionAPIObject.createConversionFunctions(streamSchema)

  } catch {
     case e: Exception => {
	if( context == null ){
           log.error( "Could not connect to the database: " + databaseName)
	} else {
           log.error( "Could not resolve the table: " + tableName)
        }
        context = null
        tableToInsert = null
        
	throw e
     }
  }
 } 

  // This routine is used to connect or reconnect to the DB
  def connectToDatabase(initialConnect: Boolean) : Unit = {
     log.info( "connectToDatabase " + databaseName)
     if( initialConnect ){
        // Determine if we need to setup the zookeeper connection string using an API
        if( connectionString != null ){
            log.info( "setConnectionEndpoints: " + connectionString)
            ConfigurationReader.setConnectionEndpoints(connectionString)
        }

        if( schemaName != null ){
            log.info( "setEventSchemaName: " + schemaName)
            ConfigurationReader.setEventSchemaName(schemaName)
        }

        if( frontEndConnectionFlag ){
            try {
              log.info( "setUseFrontendConnectionEndpoints: " + frontEndConnectionFlag)
              ConfigurationReader.setUseFrontendConnectionEndpoints(frontEndConnectionFlag)
            } catch {
               case e: Exception => {
                  log.error( "Could not set ConfigurationReader.setUseFrontendConnectionEndpoints likely due to incorrect Event Store version" )
               }
            }
        }

        // Determine if we need to setup EventStore user string using an API
        if (eventStoreUser != null) {
           log.info( "setEventUser: " + eventStoreUser)
           ConfigurationReader.setEventUser(eventStoreUser)
           //ConfigurationReader.setLegacyEventUser(eventStoreUser)
        }

        // Determine if we need to setup EventStore password string using an API
        if (eventStorePassword != null) {
           log.info( "setEventPassword: " + eventStorePassword)
           ConfigurationReader.setEventPassword(eventStorePassword)
           //ConfigurationReader.setLegacyEventPassword(eventStorePassword)
        }

        log.info( "setSSLEnabled: " + sslConnection)
        ConfigurationReader.setSSLEnabled(sslConnection)
        
        log.info( "setClientPlugin: " + pluginFlag)
        ConfigurationReader.setClientPlugin(pluginFlag)
      
        if (pluginName != null) {
           log.info( "setClientPluginName: " + pluginName)
           ConfigurationReader.setClientPluginName(pluginName)
        }
        if (trustStore != null) {
           log.info( "setSslTrustStoreLocation: " + trustStore)
           ConfigurationReader.setSslTrustStoreLocation(trustStore)
        }
        if (trustStorePassword != null) {
           log.info( "setSslTrustStorePassword: " + "xxx")
           ConfigurationReader.setSslTrustStorePassword(trustStorePassword)
        }
        if (keyStore != null) {
           log.info( "setSslKeyStoreLocation: " + keyStore)
           ConfigurationReader.setSslKeyStoreLocation(keyStore)
        }
        if (keyStorePassword != null) {
           log.info( "setSslKeyStorePassword: " + "xxx")
           ConfigurationReader.setSslKeyStorePassword(keyStorePassword)
        }
        log.info( "EventContext.getEventContext: " + databaseName)
        context = EventContext.getEventContext(databaseName)
        if( context != null ){
           log.info( "Successful connection to the database: " + databaseName)
        }
        else {
           throw EventStoreWriterException("Error while connecting to database")
        }
     }
     
     //val dberror = context.openDatabase()
     //if (dberror.isDefined) {
     //  log.error("error while opening database: " + dberror.get)
     //  throw EventStoreWriterException( "error while opening database: " + dberror.get,
     //		new Exception)
     //} else {
     //  log.info("database opened successfully")
     //}
  }

  // Make sure that the stream schema and the table schemas are of the same data types in the 
  // same order. If not then signal an exception since we should not be allowed to 
  // insert to the table as a result.
  def validateSchemas(streamSchema: StreamSchema, tableSchema: StructType): Unit = {
    if( streamSchema.getAttributeCount != tableSchema.fields.length ){
       throw EventStoreWriterException( s"The number of schema attributes do not match",
		new Exception)
    }

    try {
        for(i <- 0 until streamSchema.getAttributeCount){ 
	    compareAttributeTypes(streamSchema.getAttribute(i), tableSchema.fields(i))  
	}
    } catch {
	case e: Exception => throw e
    }
  }

  def applyConversionFunction(tuple: Tuple, i: Int) : Any = {
    ConversionAPIObject.applyConversionFunction(conversionFunctionMap,tuple,i)
  }

  // Determine the default batchSize based on the stream's schema
  def calcDefaultBatchSize() : Int = {
    var rowSize : Int = 0 
    for(i <- 0 until streamSchema.getAttributeCount){
      rowSize += { streamSchema.getAttribute(i).getType.getLanguageType match {
        case "boolean"  | "optional<boolean>" => 1
        case "int8"  | "uint8"  | "optional<int8>" | "optional<uint8>" => 1
        case "int16" | "uint16" | "optional<int16>" | "optional<uint16>" => 2
        case "int32" | "uint32" | "optional<int32>" | "optional<uint32>" => 4
        case "float32" | "optional<float32>" => 4
        case _ => 8
       }
      }
    }

    val defBatchSize = if( rowSize >= 8192 ) 1 else 8192 / rowSize
    log.info( "Default batch size is: " + defBatchSize + " using row size: " + rowSize )
    defBatchSize
  }

  // Take the stream schema and convert it to a Event Store schema and issue a
  // create table with that schema and table name.
  def createTableFromStream(tableName: String, streamSchema: StreamSchema,
                            partitioningKey: String, primaryKey: String): Unit = {
    try {
      // Table the Stream schema attributes and convert them to schema attribute types
      // for converting it to a EventStore schema
      // Set nullable to true in StructField if SPL attribute is optional type
      val fields = (0 until streamSchema.getAttributeCount).
        map(i => streamSchema.getAttribute(i)).map(
        attr => StructField(attr.getName, convertStreamType(attr.getType), attr.getType.getLanguageType.toUpperCase().contains("OPTIONAL"))).toSeq
      log.info( "NEW FIELDS = " + fields )
      val newStruct = StructType(fields)
      log.info( "New schema for table " + tableName + " is : " + newStruct)

      log.info("Convert partitoning key = " + partitioningKey )
      var partKey = createStringAttrArray( partitioningKey, streamSchema, Array[Int](0) )
      log.info( "Partitioning key column index array = " )
      for( i <- 0 until partKey.length ){ log.info(s"Col key index val = ${partKey(i)}" ) }

      log.info("Convert primary key = " + primaryKey)
      var primKey = createStringAttrArray( primaryKey, streamSchema, Array[Int]() )
      log.info( "Primary key column index array = " )
      for( i <- 0 until primKey.length ){ log.info(s"Col key index val = ${primKey(i)}" ) }

      // Now create the final table schema and try and create the table
      val tableSchema = TableSchema( tableName, newStruct, partKey, primKey )
      val res = context.createTable(tableSchema)
      if (res.isDefined) {
        throw EventStoreWriterException( s"Error while creating table ${tableName}\n: ${res.get}")
      } else {
        log.info(s"Table ${tableName} successfully created.")
      }
    } catch {
      case e: Exception => throw e
    }
  }

  // Give a string of attributes, make the attribute string name array corresponding to each
  // attribute's position in the schema.
  def createStringAttrArray( colAttr: String, streamSchema: StreamSchema, defaultArray : Array[Int] ) : Array[String] = {
    var returnArray = Array[String]() //defaultArray

    val fields = (0 until streamSchema.getAttributeCount).
      map(i => streamSchema.getAttribute(i)).map(attr => attr.getName).toArray
    val nameMap = scala.collection.mutable.Map[String, Int]()
    for( i <- 0 until fields.length ){
      nameMap(fields(i)) = i
      if( !defaultArray.isEmpty && defaultArray.size == 1 && defaultArray(0) == i ){
        returnArray = Array[String](fields(i))
      }
    }

    log.info("Conversion of schema attributes to map = ")
    nameMap.foreach( p => log.info(s"Schema pos ${p}" ) )

    if( colAttr != null && !colAttr.isEmpty ){
      val colArray = colAttr.split(",").map( str => str.stripPrefix(" ").stripSuffix(" ").trim )
      log.info("Column key array = " + colArray)
      returnArray = colArray
    }
    returnArray
  }

  def compareAttributeTypes(streamAttr: Attribute, tableAttr: StructField): Unit = {
    val tableAttrType = tableAttr.dataType
    val convertedType = convertStreamType(streamAttr.getType)

    log.info( s"Compare stream type ${streamAttr.getName()} : ${streamAttr.getType.getLanguageType} and table type ${tableAttr.name} : ${tableAttrType}")

    if( convertType(convertedType) != convertType(tableAttrType) ){
       throw EventStoreWriterException( s"Mismatch in stream attrbute ${streamAttr.getName()} : ${streamAttr.getType.getLanguageType} and table attribute ${tableAttr.name} : ${tableAttrType}")
    }
  }

  def convertType(t : DataType) : DataType = {
      t match {
        case ArrayType(elementType, nullable) =>
          ArrayType(convertType(elementType),false)
        case ScalaMapType(keyType, elementType, nullable) =>
          ScalaMapType(convertType(keyType), convertType(elementType), false)
        case curr => curr
      }
  }


  // Convert the IBM streams schema to a EventStore schema where by default we make all nullable
  def convertStreamType(attrType: Type): DataType = {
    attrType.getLanguageType match {
      case "boolean" | "optional<boolean>" => BooleanType
      case "int8"  | "uint8" | "optional<int8>" | "optional<uint8>" => ByteType
      case "int16" | "uint16" | "optional<int16>" | "optional<uint16>" => ShortType
      case "int32" | "uint32" | "optional<int32>" | "optional<uint32>" => IntegerType
      case "int64" | "uint64" | "optional<int64>" | "optional<uint64>" => LongType
      case "float32" | "optional<float32>" => FloatType
      case "float64" | "optional<float64>" => DoubleType
      //case "decimal32" | "decimal64" | "decimal128" => DecimalType
      case "timestamp" | "optional<timestamp>" => TimestampType
      case "rstring" | "ustring" | "optional<rstring>" | "optional<ustring>"  => StringType
      //case "blob" => tuple.getBlob(attr.getIndex)
      //case "xml" => tuple.getXML(attr.getIndex).toString //Cassandra doesn't have XML as data type, thank goodness
      case l if l.startsWith("list") => {
          val listType: CollectionType = attrType.asInstanceOf[CollectionType]
          val elementT: Type  = listType.getElementType
          ArrayType( convertStreamType(elementT), true )
      }
      case s if s.startsWith("set") => {
          val setType: CollectionType = attrType.asInstanceOf[CollectionType]
          val elementT: Type  = setType.getElementType
          ArrayType( convertStreamType(elementT), true )
      }
      case m if m.startsWith("map") => {
          val mapType: MapType = attrType.asInstanceOf[MapType]
          val keyT: Type = mapType.getKeyType
          val valT: Type = mapType.getValueType
          ScalaMapType( convertStreamType(keyT), convertStreamType(valT), true )
      }
      case _ => throw EventStoreWriterException( s"Unrecognized convert type: ${attrType.getLanguageType}", new Exception)
    }
  }

  def insertTuple(tuplebatch: java.util.LinkedList[/*Row*/Tuple]): Unit = {
    if (log.isTraceEnabled()) {
      log.trace("Inserting EventStore tuple...")
    }
    val rowBatch = /*tuplebatch.asScala.toIndexedSeq */mkRowIterator(tuplebatch.asScala.toList).toIndexedSeq
    if (log.isTraceEnabled()) {
      log.trace("Inserting a batch ...")
    }
    if (notConnectedMode) {
      return
    }
    val future: Future[InsertResult] = context.batchInsertAsync(tableToInsert, rowBatch)
    if (log.isTraceEnabled()) {
      log.trace("... client continues while batches are being inserted ...")
      log.trace("waiting for batch inserts to complete...")
    }
    val result = Await.result(future, Duration.Inf)
    if (result.failed) {
      // In the future, we need to check the proper error code, and
      // we should throw an exception for certain codes
      // such as UnauthorizedException or when the connection
      // is down, and potentially not throw the exception
      // in other cases. Also we should make sure that an exception thrown will
      // result in the sink operator shutting down and eventually restarting to 
      // reinitialize and thus reconnect to the DB
      log.error(s"batch insert 1 incomplete: $result")
      
      try {
         //connectToDatabase(false)
         throw EventStoreWriterException( s"batch insert 1 incomplete: $result",
		new Exception)
      } catch { case e: Exception =>
         throw EventStoreWriterException( s"Database down caused batch insert 1 incomplete: $result",
		new Exception)
      }
    } else {
      if (log.isTraceEnabled()) {
        log.trace(s"batch insert 1 complete: $result")
      }
    }
  }

  def mkRowIterator(tupleList: List[Tuple]): Iterator[Row] = {
    val schema = tupleList.head.getStreamSchema
    val attrList = (0 until schema.getAttributeCount).map(schema.getAttribute).toList
    tupleList.map(t => generateRow(t, attrList)).toIterator
  }

  def generateRow(tuple: Tuple, attrList: List[Attribute]): Row = {
    val fields = attrList.map(attr => getValueFromTuple(tuple, attr))
    Row.fromSeq(fields)
  }

  def getOptionalValue[T](tuple: Tuple, attr: Attribute, value: T ) : T = {
    var returnVal : T = null.asInstanceOf[T]

    if ((tuple.getOptional(attr.getIndex, attr.getType().getAsCompositeElementType()).isPresent()))
      returnVal = tuple.getOptional(attr.getIndex, attr.getType().getAsCompositeElementType()).get().asInstanceOf[T]

    returnVal
  }

  def getOptionalTimestamp(tuple: Tuple, attr: Attribute) : java.sql.Timestamp = {
    var returnVal : java.sql.Timestamp = null.asInstanceOf[java.sql.Timestamp]

    if ((tuple.getOptional(attr.getIndex, attr.getType().getAsCompositeElementType()).isPresent()))
      returnVal = tuple.getOptional(attr.getIndex, attr.getType().getAsCompositeElementType()).get().asInstanceOf[com.ibm.streams.operator.types.Timestamp].getSQLTimestamp()

    returnVal
  }

  def getValueFromTuple(tuple: Tuple, attr: Attribute): Any = {
    val value: Any = attr.getType.getLanguageType match {
      case "boolean" => { val value: Boolean = tuple.getBoolean(attr.getIndex); value }
      case "optional<boolean>" => { val value: Boolean = getOptionalValue(tuple, attr, null.asInstanceOf[Boolean]); value }
      case "int8"  | "uint8" => { val value: Byte = tuple.getByte(attr.getIndex); value }
      case "optional<int8>" | "optional<uint8>" => { val value: Byte = getOptionalValue(tuple, attr, null.asInstanceOf[Byte]); value }
      case "int16" | "uint16" => { val value: Short = tuple.getShort(attr.getIndex); value }
      case "optional<int16>" | "optional<uint16>" => { val value: Short = getOptionalValue(tuple, attr, null.asInstanceOf[Short]); value }
      case "int32" | "uint32" => { val value: Int = tuple.getInt(attr.getIndex); value }
      case "optional<int32>" | "optional<uint32>" => { val value: Int = getOptionalValue(tuple, attr, null.asInstanceOf[Int]); value }
      case "int64" | "uint64" => { val value: Long = tuple.getLong(attr.getIndex); value }
      case "optional<int64>" | "optional<uint64>" => { val value: Long = getOptionalValue(tuple, attr, null.asInstanceOf[Long]); value }
      case "float32" => { val value: Float = tuple.getFloat(attr.getIndex); value }
      case "optional<float32>" => { val value: Float = getOptionalValue(tuple, attr, null.asInstanceOf[Float]); value }
      case "float64" => { val value: Double = tuple.getDouble(attr.getIndex); value }
      case "optional<float64>" => { val value: Double = getOptionalValue(tuple, attr, null.asInstanceOf[Double]); value }
      case "decimal32" | "decimal64" | "decimal128" => tuple.getBigDecimal(attr.getIndex)
      case "timestamp" => { val value: java.sql.Timestamp = new java.sql.Timestamp(tuple.getTimestamp(attr.getIndex).getTime()); value }
      case "optional<timestamp>" => { val value: java.sql.Timestamp = getOptionalTimestamp(tuple, attr); value }
      case "rstring" | "ustring" => { val value: String = tuple.getString(attr.getIndex); value }
      case "optional<rstring>" | "optional<ustring>" => { val value: String = getOptionalValue(tuple, attr, null.asInstanceOf[String]); value }
      //case "blob" => tuple.getBlob(attr.getIndex)
      //case "xml" => tuple.getXML(attr.getIndex).toString //Cassandra doesn't have XML as data type, thank goodness
      case l if l.startsWith("list") => mkList(tuple, attr)
      case s if s.startsWith("set") => mkSet(tuple, attr)
      case m if m.startsWith("map") => mkMap(tuple, attr)
      case _ => throw EventStoreWriterException( s"Unrecognized value type: ${attr.getType.getLanguageType}", new Exception)
    }
    value
  }

  def getScalaValueFromStreamValue(inputValue: Any): Any = {
    val value: Any = inputValue match {
      case a : com.ibm.streams.operator.types.RString =>
          { val value: String = a.toString(); value } 
      case a : java.lang.String =>
          { val value: String = a.toString(); value } 
      case a : com.ibm.streams.operator.types.Timestamp =>
          { val value: java.sql.Timestamp = new java.sql.Timestamp(a.getTime()); value }
      case a : Array[elementType] =>
          { val value = a.map( v => getScalaValueFromStreamValue(v) ).toSeq; value }
      case a : List[elementType] =>
          { val value = a.map( v => getScalaValueFromStreamValue(v) ).toSeq; value }
      case a : Set[elementType] =>
          { val value = a.map( v => getScalaValueFromStreamValue(v) ).toSeq; value }
      case a : Seq[elementType] =>
          { val value = a.map( v => getScalaValueFromStreamValue(v) ).toSeq; value }
      case a : Map[keyType,elementType] =>
	  { val value = a.map( v => (getScalaValueFromStreamValue(v._1),
					getScalaValueFromStreamValue(v._2) )).toMap; value }
      case a => a
    }
    value
  }

  private def mkList(tuple: Tuple, attr: Attribute): Any = {
    val rawList = tuple.getList(attr.getIndex)
    rawList.asScala.toSeq.map( v => getScalaValueFromStreamValue(v) )
  }

  private def mkSet(tuple: Tuple, attr: Attribute): Any = {
    val rawSet = tuple.getSet(attr.getIndex)
    rawSet.asScala.toSeq.map( v => getScalaValueFromStreamValue(v) )
  }

  private def mkMap(tuple: Tuple, attr: Attribute): Any = {
    val rawMap = tuple.getMap(attr.getIndex)
    rawMap.asScala.toMap.map( v => (getScalaValueFromStreamValue(v._1),
                                        getScalaValueFromStreamValue(v._2)) )
  }

  def shutdown(): Unit = {
  }
}

object ConversionAPIObject {
  protected val log = Logger.getLogger("ConversionAPIObject")

  var tableToInsert : ResolvedTableSchema = null

  def setTableSchema(tableSchema : ResolvedTableSchema) : Unit = {
    log.info( "Set EventStore table schema: " + tableSchema)
    tableToInsert = tableSchema
  }

  // Make sure that the stream schema and the table schemas are of the same data types in the
  // same order. If not then signal an exception since we should not be allowed to
  // insert to the table as a result.
  def validateSchemas(streamSchema: StreamSchema, tableSchema: StructType): Unit = {
    if( streamSchema.getAttributeCount != tableSchema.fields.length ){
      throw EventStoreWriterException( s"The number of schema attributes do not match",
        new Exception)
    }

    try {
      for(i <- 0 until streamSchema.getAttributeCount){
        compareAttributeTypes(streamSchema.getAttribute(i), tableSchema.fields(i))
      }
    } catch {
      case e: Exception => throw e
    }
  }

  // Determine the default batchSize based on the stream's schema
  def calcDefaultBatchSize(streamSchema: StreamSchema) : Int = {
    var rowSize : Int = 0
    for(i <- 0 until streamSchema.getAttributeCount){
      rowSize += { streamSchema.getAttribute(i).getType.getLanguageType match {
        case "boolean" | "optional<boolean>" => 1
        case "int8"  | "uint8" | "optional<int8>" | "optional<uint8>" => 1
        case "int16" | "uint16" | "optional<int16>" | "optional<uint16>" => 2
        case "int32" | "uint32" | "optional<int32>" | "optional<uint32>" => 4
        case "float32" | "optional<float32>" => 4
        case _ => 8
      }
      }
    }

    val defBatchSize = if( rowSize >= 8192 ) 1 else 8192 / rowSize
    log.info( "Default batch size is: " + defBatchSize + " using row size: " + rowSize )
    defBatchSize
  }

  // Take the stream schema and convert it to a EventStore schema and issue a
  // create table with that schema and table name.
  def createTableSchemaFromStream(tableName: String, streamSchema: StreamSchema,
                            partitioningKey: String, primaryKey: String): TableSchema  = {
    try {
      // Table the Stream schema attributes and convert them to schema attribute types
      // for converting it to a EventStore schema
      val fields = (0 until streamSchema.getAttributeCount).
        map(i => streamSchema.getAttribute(i)).map(
        attr => StructField(attr.getName, convertStreamType(attr.getType), attr.getType.getLanguageType.toUpperCase().contains("OPTIONAL"))).toSeq
      log.info( "NEW FIELDS = " + fields )
      val newStruct = StructType(fields)
      log.info( "New schema for table " + tableName + " is : " + newStruct)

      log.info("Convert partitoning key = " + partitioningKey )
      var partKey = createStringAttrArray( partitioningKey, streamSchema, Array[Int](0) )
      log.info( "Partitioning key column index array = " )
      for( i <- 0 until partKey.length ){ log.info(s"Col key index val = ${partKey(i)}" ) }

      log.info("Convert primary key = " + primaryKey)
      var primKey = createStringAttrArray( primaryKey, streamSchema, Array[Int]() )
      log.info( "Primary key column index array = " )
      for( i <- 0 until primKey.length ){ log.info(s"Col key index val = ${primKey(i)}" ) }

      // Now create the final table schema and try and create the table
      TableSchema( tableName, newStruct, partKey, primKey )//Array[Int](0), Array[Int](0))
    } catch {
      case e: Exception => throw e
    }
  }

  // Give a string of attributes, make the attribute string name array corresponding to each
  // attribute's position in the schema.
  def createStringAttrArray( colAttr: String, streamSchema: StreamSchema, defaultArray : Array[Int] ) : Array[String] = {
    var returnArray = Array[String]() //defaultArray

    val fields = (0 until streamSchema.getAttributeCount).
      map(i => streamSchema.getAttribute(i)).map(attr => attr.getName).toArray
    val nameMap = scala.collection.mutable.Map[String, Int]()
    for( i <- 0 until fields.length ){
      nameMap(fields(i)) = i
      if( !defaultArray.isEmpty && defaultArray.size == 1 && defaultArray(0) == i ){
        returnArray = Array[String](fields(i))
      }
    }

    log.info("Conversion of schema attributes to map = ")
    nameMap.foreach( p => log.info(s"Schema pos ${p}" ) )

    if( colAttr != null && !colAttr.isEmpty ){
      val colArray = colAttr.split(",").map( str => str.stripPrefix(" ").stripSuffix(" ").trim )
      log.info("Column key array = " + colArray)
      returnArray = colArray
    }
    returnArray
  }

  def compareAttributeTypes(streamAttr: Attribute, tableAttr: StructField): Unit = {
    val tableAttrType = tableAttr.dataType
    val convertedType = convertStreamType(streamAttr.getType)

    log.info( s"Compare stream type ${streamAttr.getName()} : ${streamAttr.getType.getLanguageType} and table type ${tableAttr.name} : ${tableAttrType}")

    if( convertType(convertedType) != convertType(tableAttrType) ){
      throw EventStoreWriterException( s"Mismatch in stream attrbute ${streamAttr.getName()} : ${streamAttr.getType.getLanguageType} and table attribute ${tableAttr.name} : ${tableAttrType}")
    }
  }

  def convertType(t : DataType) : DataType = {
    t match {
      case ArrayType(elementType, nullable) =>
        ArrayType(convertType(elementType),false)
      case ScalaMapType(keyType, elementType, nullable) =>
        ScalaMapType(convertType(keyType), convertType(elementType), false)
      case curr => curr
    }
  }

  // Convert the IBM streams schema to a EventStore schema where by default we make all nullable
  def convertStreamType(attrType: Type): DataType = {
    attrType.getLanguageType match {
      case "boolean" | "optional<boolean>" => BooleanType
      case "int8"  | "uint8" | "optional<int8>" | "optional<uint8>" => ByteType
      case "int16" | "uint16" | "optional<int16>" | "optional<uint16>" => ShortType
      case "int32" | "uint32" | "optional<int32>" | "optional<uint32>" => IntegerType
      case "int64" | "uint64" | "optional<int64>" | "optional<uint64>" => LongType
      case "float32" | "optional<float32>" => FloatType
      case "float64" | "optional<float64>" => DoubleType
      case "decimal32" | "decimal64" | "decimal128" | "optional<decimal32>" | "optional<decimal64>" | "optional<decimal128>" => DecimalType
      case "timestamp" | "optional<timestamp>" => TimestampType
      case "rstring" | "ustring" | "optional<rstring>" | "optional<ustring>" => StringType
      //case "blob" => tuple.getBlob(attr.getIndex)
      //case "xml" => tuple.getXML(attr.getIndex).toString //Cassandra doesn't have XML as data type, thank goodness
      case l if l.startsWith("list") => {
        val listType: CollectionType = attrType.asInstanceOf[CollectionType]
        val elementT: Type  = listType.getElementType
        ArrayType( convertStreamType(elementT), true )
      }
      case s if s.startsWith("set") => {
        val setType: CollectionType = attrType.asInstanceOf[CollectionType]
        val elementT: Type  = setType.getElementType
        ArrayType( convertStreamType(elementT), true )
      }
      case m if m.startsWith("map") => {
        val mapType: MapType = attrType.asInstanceOf[MapType]
        val keyT: Type = mapType.getKeyType
        val valT: Type = mapType.getValueType
        ScalaMapType( convertStreamType(keyT), convertStreamType(valT), true )
      }
      case _ => throw EventStoreWriterException( s"Unrecognized convert type: ${attrType.getLanguageType}", new Exception)
    }
  }

  // TODO: see if this can be converted to use the iterator
  def mkRowIterator(tupleList: List[Tuple]): Iterator[Row] = {
    val schema = tupleList.head.getStreamSchema
    val attrList = (0 until schema.getAttributeCount).map(schema.getAttribute).toList
    tupleList.map(t => generateRow(t, attrList)).toIterator
  }

  def generateRow(tuple: Tuple, attrList: List[Attribute]): Row = {
    val fields = attrList.map(attr => getValueFromTuple(tuple, attr))
    Row.fromSeq(fields)
  }


  def getOptionalValue[T](tuple: Tuple, attr: Attribute, value: T ) : T = {
    var returnVal : T = null.asInstanceOf[T]

    if ((tuple.getOptional(attr.getIndex, attr.getType().getAsCompositeElementType()).isPresent()))
      returnVal = tuple.getOptional(attr.getIndex, attr.getType().getAsCompositeElementType()).get().asInstanceOf[T]

    returnVal
  }

  def getOptionalTimestamp(tuple: Tuple, attr: Attribute) : java.sql.Timestamp = {
    var returnVal : java.sql.Timestamp = null.asInstanceOf[java.sql.Timestamp]

    if ((tuple.getOptional(attr.getIndex, attr.getType().getAsCompositeElementType()).isPresent()))
      returnVal = tuple.getOptional(attr.getIndex, attr.getType().getAsCompositeElementType()).get().asInstanceOf[com.ibm.streams.operator.types.Timestamp].getSQLTimestamp()

    returnVal
  }

  def getValueFromTuple(tuple: Tuple, attr: Attribute): Any = {
    val value: Any = attr.getType.getLanguageType match {
      case "boolean" => { val value: Boolean = tuple.getBoolean(attr.getIndex); value }
      case "optional<boolean>" => { val value: Boolean = getOptionalValue(tuple, attr, null.asInstanceOf[Boolean]); value }
      case "int8"  | "uint8" => { val value: Byte = tuple.getByte(attr.getIndex); value }
      case "optional<int8>" | "optional<uint8>" => { val value: Byte = getOptionalValue(tuple, attr, null.asInstanceOf[Byte]); value }
      case "int16" | "uint16" => { val value: Short = tuple.getShort(attr.getIndex); value }
      case "optional<int16>" | "optional<uint16>" => { val value: Short = getOptionalValue(tuple, attr, null.asInstanceOf[Short]); value }
      case "int32" | "uint32" => { val value: Int = tuple.getInt(attr.getIndex); value }
      case "optional<int32>" | "optional<uint32>" => { val value: Int = getOptionalValue(tuple, attr, null.asInstanceOf[Int]); value }
      case "int64" | "uint64" => { val value: Long = tuple.getLong(attr.getIndex); value }
      case "optional<int64>" | "optional<uint64>" => { val value: Long = getOptionalValue(tuple, attr, null.asInstanceOf[Long]); value }
      case "float32" => { val value: Float = tuple.getFloat(attr.getIndex); value }
      case "optional<float32>" => { val value: Float = getOptionalValue(tuple, attr, null.asInstanceOf[Float]); value }
      case "float64" => { val value: Double = tuple.getDouble(attr.getIndex); value }
      case "optional<float64>" => { val value: Double = getOptionalValue(tuple, attr, null.asInstanceOf[Double]); value }
      case "decimal32" | "decimal64" | "decimal128" => tuple.getBigDecimal(attr.getIndex)
      case "timestamp" => { val value: java.sql.Timestamp = new java.sql.Timestamp(tuple.getTimestamp(attr.getIndex).getTime()); value }
      case "optional<timestamp>" => { val value: java.sql.Timestamp = getOptionalTimestamp(tuple, attr); value }
      case "rstring" | "ustring" => { val value: String = tuple.getString(attr.getIndex); value }
      case "optional<rstring>" | "optional<ustring>" => { val value: String = getOptionalValue(tuple, attr, null.asInstanceOf[String]); value }
      //case "blob" => tuple.getBlob(attr.getIndex)
      //case "xml" => tuple.getXML(attr.getIndex).toString //Cassandra doesn't have XML as data type, thank goodness
      case l if l.startsWith("list") => mkList(tuple, attr)
      case s if s.startsWith("set") => mkSet(tuple, attr)
      case m if m.startsWith("map") => mkMap(tuple, attr)
      case _ => throw EventStoreWriterException( s"Unrecognized value type: ${attr.getType.getLanguageType}", new Exception)
    }
    value
  }


  // Make sure that the stream schema and the table schemas are of the same data types in the
  // same order. If not then signal an exception since we should not be allowed to
  // insert to the table as a result.
  def createConversionFunctions(streamSchema: StreamSchema): scala.collection.mutable.HashMap[Int,(Tuple, Int) => Any] = {
    val conversionFunctions = new scala.collection.mutable.HashMap[Int,(Tuple, Int) => Any]()
    try {
      for(i <- 0 until streamSchema.getAttributeCount){
        val attr = streamSchema.getAttribute(i)
        val func = attr.getType.getLanguageType match {
          case "boolean" | "optional<boolean>" => { val f = (tuple: Tuple, i: Int) => {tuple.getBoolean(i)}; f }
          case "int8"  | "uint8" | "optional<int8>" | "optional<uint8>" => { val f = (tuple: Tuple, i: Int) => {tuple.getByte(i)}; f }
          case "int16" | "uint16" | "optional<int16>" | "optional<uint16>" => { val f = (tuple: Tuple, i: Int) => {tuple.getShort(i)}; f }
          case "int32" | "uint32" | "optional<int32>" | "optional<uint32>" => { val f = (tuple: Tuple, i: Int) => {tuple.getInt(i)}; f }
          case "int64" | "uint64" | "optional<int64>" | "optional<uint64>" => { val f = (tuple: Tuple, i: Int) => {tuple.getLong(i)}; f }
          case "float32" | "optional<float32>" => { val f = (tuple: Tuple, i: Int) => {tuple.getFloat(i)}; f }
          case "float64" | "optional<float64>" => { val f = (tuple: Tuple, i: Int) => {tuple.getDouble(i)}; f }
          case "decimal32" | "decimal64" | "decimal128" | "optional<decimal32>" | "optional<decimal64>" | "optional<decimal128>" => { val f = (tuple: Tuple, i: Int) => {tuple.getBigDecimal(i)}; f }
          case "timestamp" | "optional<timestamp>" => { val f = (tuple: Tuple, i: Int) => {new java.sql.Timestamp(tuple.getTimestamp(attr.getIndex).getTime())}; f }
          case "rstring" | "ustring" | "optional<rstring>" | "optional<ustring>" => { val f = (tuple: Tuple, i: Int) => {tuple.getString(i)}; f }
          //case "blob" => tuple.getBlob(attr.getIndex)
          //case "xml" => tuple.getXML(attr.getIndex).toString //Cassandra doesn't have XML as data type, thank goodness
          case l if l.startsWith("list") => { val f = (tuple: Tuple, i: Int) => {mkList(tuple, i)}; f }
          case s if s.startsWith("set") => { val f = (tuple: Tuple, i: Int) => {mkSet(tuple, i)}; f }
          case m if m.startsWith("map") => { val f = (tuple: Tuple, i: Int) => {mkMap(tuple,i)}; f }
          case _ => throw EventStoreWriterException( s"Unrecognized value type: ${attr.getType.getLanguageType}", new Exception)
        }
        conversionFunctions(i) = func
      }
      conversionFunctions
    } catch {
      case e: Exception => throw e
    }
  }

  def applyConversionFunction( convFunc : scala.collection.mutable.HashMap[Int,(Tuple, Int) => Any], tuple: Tuple, i : Int ) : Any = {
    val func = convFunc(i)
    func(tuple,i)
  }

  // Make data transfer from Tuple object in java to scala for all allowed types as overrided functions
  def getValueFromObject( obj : java.lang.Boolean ): Boolean = { obj }
  def getValueFromObject( obj : java.lang.Byte ): Byte = { obj }
  def getValueFromObject( obj : java.lang.Short ): Short = { obj }
  def getValueFromObject( obj : java.lang.Integer ): Int = { obj }
  def getValueFromObject( obj : java.lang.Long ): Long = { obj }
  def getValueFromObject( obj : java.lang.Float ): Float = { obj }
  def getValueFromObject( obj : java.lang.Double ): Double = { obj }
  def getValueFromObject( obj : com.ibm.streams.operator.types.Timestamp ): java.sql.Timestamp = {
    new java.sql.Timestamp(obj.getTime)
  }
  def getValueFromObject( obj : com.ibm.streams.operator.types.RString ): String = {
    obj.toString
  }
  def getValueFromObject( obj : java.lang.String ): String= { obj }
  def getValueFromObject( obj : java.util.List[Object] ): Any = {
    obj.asScala.toSeq.map( v => getScalaValueFromStreamValue(v) )
  }
  def getValueFromObject( obj : java.util.Set[Object] ): Any = {
    obj.asScala.toSeq.map( v => getScalaValueFromStreamValue(v) )
  }
  def getValueFromObject( obj : java.util.Map[Object,Object] ): Any = {
    obj.asScala.toMap.map( v => (getScalaValueFromStreamValue(v._1),
      getScalaValueFromStreamValue(v._2)) )
  }

  def getScalaValueFromStreamValue(inputValue: Any): Any = {
    val value: Any = inputValue match {
      case a : com.ibm.streams.operator.types.RString =>
      { val value: String = a.toString(); value }
      case a : java.lang.String =>
      { val value: String = a.toString(); value }
      case a : com.ibm.streams.operator.types.Timestamp =>
      { val value: java.sql.Timestamp = new java.sql.Timestamp(a.getTime()); value }
      case a : Array[elementType] =>
      { val value = a.map( v => getScalaValueFromStreamValue(v) ).toSeq; value }
      case a : List[elementType] =>
      { val value = a.map( v => getScalaValueFromStreamValue(v) ).toSeq; value }
      case a : Set[elementType] =>
      { val value = a.map( v => getScalaValueFromStreamValue(v) ).toSeq; value }
      case a : Seq[elementType] =>
      { val value = a.map( v => getScalaValueFromStreamValue(v) ).toSeq; value }
      case a : Map[keyType,elementType] =>
      { val value = a.map( v => (getScalaValueFromStreamValue(v._1),
        getScalaValueFromStreamValue(v._2) )).toMap; value }
      case a => a
    }
    value
  }

  private def mkList(tuple: Tuple, i: Int): Any = {
    val rawList = tuple.getList(i)
    rawList.asScala.toSeq.map( v => getScalaValueFromStreamValue(v) )
  }

  private def mkSet(tuple: Tuple, i: Int): Any = {
    val rawSet = tuple.getSet(i)
    rawSet.asScala.toSeq.map( v => getScalaValueFromStreamValue(v) )
  }

  private def mkMap(tuple: Tuple, i: Int): Any = {
    val rawMap = tuple.getMap(i)
    rawMap.asScala.toMap.map( v => (getScalaValueFromStreamValue(v._1),
      getScalaValueFromStreamValue(v._2)) )
  }

  private def mkList(tuple: Tuple, attr: Attribute): Any = {
    val rawList = tuple.getList(attr.getIndex)
    rawList.asScala.toSeq.map( v => getScalaValueFromStreamValue(v) )
  }

  private def mkSet(tuple: Tuple, attr: Attribute): Any = {
    val rawSet = tuple.getSet(attr.getIndex)
    rawSet.asScala.toSeq.map( v => getScalaValueFromStreamValue(v) )
  }

  private def mkMap(tuple: Tuple, attr: Attribute): Any = {
    val rawMap = tuple.getMap(attr.getIndex)
    rawMap.asScala.toMap.map( v => (getScalaValueFromStreamValue(v._1),
      getScalaValueFromStreamValue(v._2)) )
  }
}
