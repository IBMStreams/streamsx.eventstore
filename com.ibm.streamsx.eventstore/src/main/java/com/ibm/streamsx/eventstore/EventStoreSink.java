/* begin_generated_IBM_copyright_prolog                             */
/*                                                                  */
/* This is an automatically generated copyright prolog.             */
/* After initializing,  DO NOT MODIFY OR MOVE                       */
/* **************************************************************** */
/* THIS SAMPLE CODE IS PROVIDED ON AN "AS IS" BASIS. IBM MAKES NO   */
/* REPRESENTATIONS OR WARRANTIES, EXPRESS OR IMPLIED, CONCERNING    */
/* USE OF THE SAMPLE CODE, OR THE COMPLETENESS OR ACCURACY OF THE   */
/* SAMPLE CODE. IBM DOES NOT WARRANT UNINTERRUPTED OR ERROR-FREE    */
/* OPERATION OF THIS SAMPLE CODE. IBM IS NOT RESPONSIBLE FOR THE    */
/* RESULTS OBTAINED FROM THE USE OF THE SAMPLE CODE OR ANY PORTION  */
/* OF THIS SAMPLE CODE.                                             */
/*                                                                  */
/* LIMITATION OF LIABILITY. IN NO EVENT WILL IBM BE LIABLE TO ANY   */
/* PARTY FOR ANY DIRECT, INDIRECT, SPECIAL OR OTHER CONSEQUENTIAL   */
/* DAMAGES FOR ANY USE OF THIS SAMPLE CODE, THE USE OF CODE FROM    */
/* THIS [ SAMPLE PACKAGE,] INCLUDING, WITHOUT LIMITATION, ANY LOST  */
/* PROFITS, BUSINESS INTERRUPTION, LOSS OF PROGRAMS OR OTHER DATA   */
/* ON YOUR INFORMATION HANDLING SYSTEM OR OTHERWISE.                */
/*                                                                  */
/* (C) Copyright IBM Corp. 2010, 2014  All Rights reserved.         */
/*                                                                  */
/* end_generated_IBM_copyright_prolog                               */
package com.ibm.streamsx.eventstore;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
import com.ibm.streams.operator.OperatorContext.ContextCheck;
import com.ibm.streams.operator.compile.OperatorContextChecker;
import com.ibm.streams.operator.StreamingData.Punctuation;
import com.ibm.streams.operator.StreamingInput;
import com.ibm.streams.operator.StreamingOutput;
import com.ibm.streams.operator.StreamSchema;
import com.ibm.streams.operator.Tuple;
import com.ibm.streams.operator.model.InputPortSet;
import com.ibm.streams.operator.model.InputPortSet.WindowMode;
import com.ibm.streams.operator.model.InputPortSet.WindowPunctuationInputMode;
import com.ibm.streams.operator.model.InputPorts;
import com.ibm.streams.operator.OutputTuple;
import com.ibm.streams.operator.model.OutputPortSet;
import com.ibm.streams.operator.model.OutputPorts;
import com.ibm.streams.operator.model.Parameter;
import com.ibm.streams.operator.model.PrimitiveOperator;
import com.ibm.streams.operator.model.CustomMetric;
import com.ibm.streams.operator.metrics.*;
import com.ibm.streams.operator.state.Checkpoint;
import com.ibm.streams.operator.state.CheckpointContext;
import com.ibm.streams.operator.state.ConsistentRegionContext;
import com.ibm.streams.operator.state.StateHandler;
import com.ibm.streams.operator.log4j.TraceLevel;
import com.ibm.streams.operator.model.Libraries;

import com.ibm.streamsx.eventstore.EventStoreSinkImpl;
//import com.ibm.streamsx.eventstore.EventStoreSinkJImplObject;
//import com.ibm.streamsx.eventstore.EventStoreSinkJImpl;
import org.apache.log4j.Logger;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * IBM Db2 Event Store streams sink operator class 
 * that consumes tuples and does optionally
 * produce an output stream. Incoming tuples are processed in batches, where
 * the processing will be to send rows to IBM Db2 Event Store, and is driven by
 * the size of the batch and a timeout. A batch is processed when it reaches at
 * least getBatchSize() or at least getBatchTimeout() time have passed since the
 * first tuple was added to the batch. (At present the timeout is not activated)
 * <P>
 * The batch is processed by the processBatch provided by a concrete sub-class.
 * <BR>
 * Processing of the batch is asynchronous with incoming tuples.
 * </P>
 * <P>
 * A batch size of 1 (the default) is not treated specially, in this case each
 * incoming tuple may start batch processing meaning that all processing of the
 * tuple in processBatch() is handled asynchronously with other tuple arrival.
 * </P>
 * <P>
 * Note that this operator will work in a consistent region. <BR>
 * </P>
 */
@PrimitiveOperator(name="EventStoreSink", namespace="com.ibm.streamsx.eventstore", description=
EventStoreSink.operatorDescription +
EventStoreSink.DATA_TYPES + 
EventStoreSink.CR_DESC +
EventStoreSink.SPL_EXAMPLES_DESC
)
@InputPorts({@InputPortSet(
	id="0",
	description=EventStoreSink.iport0Description,
	cardinality=1,
	optional=false,
	windowingMode=WindowMode.NonWindowed,
	windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious)
})
@OutputPorts({
    @OutputPortSet(description=EventStoreSink.oport0Description, cardinality=1, optional=true)
})
@Libraries({"opt/*","opt/downloaded/*" })
public class EventStoreSink extends AbstractOperator implements StateHandler {
    /* begin_generated_IBM_copyright_code */
    public static final String IBM_COPYRIGHT = 
	"/*******************************************************************************" +
	"* Copyright (C) 2017,2019, International Business Machines Corporation" +
	"* All Rights Reserved" +
	"*******************************************************************************/";

    private static Logger tracer = Logger.getLogger(EventStoreSink.class.getName());

    EventStoreSinkImpl/* EventStoreSinkJImpl*/ impl = null;    
    
    String databaseName = null;
    String tableName = null;
    String schemaName = null;
    String connectionString = null;
    boolean frontEndConnectionFlag = false;
    String eventStoreUser = null;
    String eventStorePassword = null;
    String partitioningKey = "";
    String primaryKey = "";
    private String cfgObjectName = "";
    private Map<String, String> cfgMap;

    // SSL parameters
    private String keyStore = null;
    private String trustStore = null;
    private String keyStorePassword = null;
    private String trustStorePassword = null;
    private boolean sslConnection = true;
    private String pluginName = "IBMPrivateCloudAuth";
    private boolean pluginFlag = true;
    
    // Flag to specify if the optional tuple insert result port is used
    private boolean hasResultsPort = false;
    // Variable to specify tuple insert result output port
    private StreamingOutput<OutputTuple> resultsOutputPort;

    String nullMapString = null;

    OperatorMetrics opMetrics = null;

    /* Metrics include the count of the number of batches that failed to be processed by IBM Db2 Event Store
     *  (nWriteFailures), the number of successful batches sent and processed by Event Store (nWriteSuccesses), 
     *  the time spent by Event Store to process a batch, and at any given batch processing
     *  time there is a count of the number of batches processed together (numBatchesPerInsert).
     */
    Metric nWriteFailures = null;
    Metric nWriteSuccesses = null;
    Metric numBatchesPerInsert = null;
    Metric nActiveInserts = null;
	Metric lowestInsertTime;
	Metric highestInsertTime;
	Metric averageInsertTime;


    /**
     * A batch of tuples stored together before they are processed and received from the input
     */
    protected LinkedList</*Row*/Tuple> batch;
    private ArrayBlockingQueue<LinkedList</*Row*/Tuple>> batchQueue;
    private Thread[] insertThread;

    private int batchSize = 1;
    private int maxBatchSize = 200000;
    private boolean batchSizeParmSet = false;

    private int maxNumActiveBatches = 1;//2;
    private int numMaxActiveBatches = 2;

    private long batchTimeout = 0;

    private TimeUnit batchTimeoutUnit;

    private boolean preserveOrder = true;
    private boolean shutdown = false;
    private ConsistentRegionContext crContext;
    private Object drainLock = new Object();
    public static final long CONSISTENT_REGION_DRAIN_WAIT_TIME = 180000;
    
    private boolean isInsertSpeedMetricSet = false;
    ArrayList<Long> insertTimes = new ArrayList<Long>();
    
	// Initialize the metrics
    @CustomMetric (kind = Metric.Kind.COUNTER, name = "nActiveInserts", description = "Number of active insert requests")
    public void setnActiveInserts (Metric nActiveInserts) {
        this.nActiveInserts = nActiveInserts;
    }
    
    @CustomMetric (kind = Metric.Kind.COUNTER, name = "nWriteFailures", description = "Number of tuples that failed to get written to IBM Db2 Event Store")
    public void setnWriteFailures (Metric nWriteFailures) {
        this.nWriteFailures = nWriteFailures;
    }
    
    @CustomMetric (kind = Metric.Kind.COUNTER, name = "nWriteSuccesses", description = "Number of tuples that were written to IBM Db2 Event Store successfully")
    public void setnWriteSuccesses (Metric nWriteSuccesses) {
        this.nWriteSuccesses = nWriteSuccesses;
    }
   
    @CustomMetric (kind = Metric.Kind.GAUGE, name = "NumBatchesPerInsert", description = "Number of batches inserted together")
    public void setnumBatchesPerInsert (Metric numBatchesPerInsert) {
        this.numBatchesPerInsert = numBatchesPerInsert;
    }
    
	@CustomMetric (kind = Metric.Kind.TIME, name = "insertTimeMin", description = "Minimal duration to perform a successful batch insert.")
    public void setlowestInsertTime (Metric lowestInsertTime) {
		this.lowestInsertTime = lowestInsertTime;
	}

    @CustomMetric (kind = Metric.Kind.TIME, name = "insertTimeMax", description = "Maximal duration to perform a successful batch insert.")
    public void sethighestInsertTime (Metric highestInsertTime) {
        this.highestInsertTime = highestInsertTime;
    }    

    @CustomMetric (kind = Metric.Kind.TIME, name = "insertTimeAvg", description = "Average time to perform a successful batch insert.")
    public void setaverageInsertTime (Metric averageInsertTime) {
        this.averageInsertTime = averageInsertTime;
    }    
    
	@ContextCheck(compile = true)
	public static void checkCheckpointConfig(OperatorContextChecker checker) {
		OperatorContext opContext = checker.getOperatorContext();		
		CheckpointContext chkptContext = opContext.getOptionalContext(CheckpointContext.class);
		if (chkptContext != null) {
			if (chkptContext.getKind().equals(CheckpointContext.Kind.OPERATOR_DRIVEN)) {
				checker.setInvalidContext("The following operator does not support 'operatorDriven' checkpoint configuration: EventStoreSink", null);
			}
			if (chkptContext.getKind().equals(CheckpointContext.Kind.PERIODIC)) {
				checker.setInvalidContext("The following operator does not support 'periodic' checkpoint configuration: EventStoreSink", null);
			}			
		}
	}
	
	@ContextCheck(compile = true)
	public static void checkConsistentRegion(OperatorContextChecker checker) {
		
		// check that the object store sink is not at the start of the consistent region
		OperatorContext opContext = checker.getOperatorContext();
		ConsistentRegionContext crContext = opContext.getOptionalContext(ConsistentRegionContext.class);
		if (crContext != null) {
			if (crContext.isStartOfRegion()) {
				checker.setInvalidContext("The following operator cannot be the start of a consistent region when an input port is present: EventStoreSink", null); 
			}
		}
	}    
    
    /* InsertRunnable is the process that obtains a batch from a queue and processes it by sending to IBM Db2 Event Store.
     * It should be noted that this will execute asynchronously with the process that receives rows from the input.
     */
    private class InsertRunnable implements Runnable {
    	@Override
        public void run(){
        	long threadId = Thread.currentThread().getId();
			while(!shutdown ) {
				try {
					// Take batch from the queue and process it
					LinkedList</*Row*/Tuple> asyncBatch = batchQueue.take();
					if( asyncBatch == null ){
						continue;
					}
					if (tracer.isDebugEnabled()) {
						tracer.log(TraceLevel.DEBUG, "Found a non-empty batch so call insert from InsertRunnable: thread " + threadId);
					}
					// Process the batch with sending it to IBM Db2 Event Store
					boolean addLeftovers = processBatch(asyncBatch);

					if (tracer.isDebugEnabled()) {
						tracer.log(TraceLevel.DEBUG, "In InsertRunnable call after processbatch with addLeftovers = " + addLeftovers + " with thread " + threadId);
					}
				} catch (Exception e) {
					tracer.log(TraceLevel.ERROR, "Found exception in InsertRunnable thread " + threadId + " message:" + e.getMessage() );
					throw new RuntimeException(e);
				} finally {
					if(inConsistentRegion()) {
						// if internal buffer has been cleared, notify waiting thread.
						if(batchQueue.peek() == null) {
							synchronized(drainLock) {
								drainLock.notifyAll();
							}
						}
					}
				}
			}
    	}
    }

    /**
     * Initialize this operator. Called once before any tuples are processed.
     * Sub-classes must call super.initialize(context) to correctly setup an
     * operator.
     */
    @Override
    public synchronized void initialize(OperatorContext context)
            throws Exception {
        super.initialize(context);

        // Initialize the operator information from scratch
        startOperatorSetup(context, 0L, 0L);
    }

    @Override
    public void close() throws IOException {
        if (tracer.isDebugEnabled()) {
        	tracer.log(TraceLevel.DEBUG, "CLOSE the IBM Db2 Event Store sink operator");
        }
    }

    @Override
    public void checkpoint(Checkpoint checkpoint) throws Exception {
        if (tracer.isDebugEnabled()) {
        	tracer.log(TraceLevel.DEBUG, "CHECKPOINT the IBM Db2 Event Store sink operator Checkpoint sequence ID = " + checkpoint.getSequenceId() );
        }

        // Write out a checkpoint state and not the order of writes must be adhere to in reset for order of reads
        checkpoint.getOutputStream().writeLong(getWriteFailuresMetric().getValue());
        checkpoint.getOutputStream().writeLong(getWriteSuccessesMetric().getValue());
        if (tracer.isDebugEnabled()) {
        	tracer.log(TraceLevel.DEBUG, "CHECKPOINT completed with failcount = " + getWriteFailuresMetric().getValue() + " success count = " + getWriteSuccessesMetric().getValue());
        }
    }

    @Override
    public void drain() throws Exception {
        if (tracer.isDebugEnabled()) {
            tracer.log(TraceLevel.DEBUG, "DRAIN called for the IBM Db2 Event Store sink operator");
        }

        if( batch != null && !batch.isEmpty() ){
            LinkedList</*Row*/Tuple> asyncBatch = null;
            asyncBatch = batch;
            batch = newBatch();
            batchQueue.put(asyncBatch);
        }

        if(batchQueue.peek() != null) {
        	synchronized(drainLock) {
        		if(batchQueue.peek() != null) {
        			drainLock.wait(CONSISTENT_REGION_DRAIN_WAIT_TIME);
        			if(batchQueue.peek() != null) {
        				throw new Exception("TIMED_OUT_WAITING_FOR_TUPLES_DRAINING");
        			}
        		}
        	}
		}
    	if (tracer.isDebugEnabled()) {
    		tracer.log(TraceLevel.DEBUG, "DRAIN completed");
    	}
    }

    @Override
    public void reset(Checkpoint checkpoint) throws Exception {
    	if (tracer.isDebugEnabled()) {
    		tracer.log(TraceLevel.DEBUG, "RESET called for the IBM Db2 Event Store sink operator" + checkpoint.getSequenceId());
    	}

        long failCount = checkpoint.getInputStream().readLong();
        long successCount = checkpoint.getInputStream().readLong();
    	if (tracer.isDebugEnabled()) {
    		tracer.log(TraceLevel.DEBUG, "RESET failcount = " + failCount + " success count = " + successCount);
    	}
        if( failCount > 0 ){
            getWriteFailuresMetric().setValue(failCount);
        }
        if( successCount > 0 ){
            getWriteSuccessesMetric().setValue(successCount);
        }
        batch.clear();
        batch = null;
        batch = newBatch();
        if (tracer.isDebugEnabled()) {
    		tracer.log(TraceLevel.DEBUG, "RESET completed");
    	}
    }

    @Override
    public void resetToInitialState() throws Exception {
        if (tracer.isDebugEnabled()) {
    		tracer.log(TraceLevel.DEBUG, "RESETToINITIALSTATE called for the IBM Db2 Event Store sink operator");
    	}
        getWriteFailuresMetric().setValue(0L);
        getWriteSuccessesMetric().setValue(0L);
        batch.clear();
        batch = null;
        batch = newBatch();
        if (tracer.isDebugEnabled()) {
    		tracer.log(TraceLevel.DEBUG, "RESETToINITIALSTATE completed");
    	}
    }

    @Override
    public void retireCheckpoint(long id) throws Exception {
        if (tracer.isDebugEnabled()) {
    		tracer.log(TraceLevel.DEBUG, "RETIRECHECKPOINT called for the IBM Db2 Event Store sink operator id= " + id );
    	}
    }

    private boolean inConsistentRegion() {
        // check if this operator is within a consistent region
        if(crContext != null) {
            return true;
        } else {
            return false;
        }
    }

    private synchronized void startOperatorSetup(OperatorContext context, Long failCount, Long successCount)
            throws Exception {
    	if (tracer.isInfoEnabled()) {
    		tracer.log(TraceLevel.INFO, "Operator " + context.getName() + " initializing in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
    	}

        // Get the consistent region context
        crContext = context.getOptionalContext(ConsistentRegionContext.class);

        batchQueue = new ArrayBlockingQueue<LinkedList</*Row*/Tuple>>(numMaxActiveBatches*maxNumActiveBatches);

        if( failCount > 0 ){
            getWriteFailuresMetric().setValue(failCount);
        }

        if( successCount > 0 ){
            getWriteSuccessesMetric().setValue(successCount);
        }

        // Determine if we have the output port related to sending insert rows to it when inserts have failed
        if (context.getNumberOfStreamingOutputs() == 1) {
            hasResultsPort = true;
            resultsOutputPort = getOutput(0);
        }

        //Obtain the configuration information using the configuration name
        if( cfgObjectName != null && cfgObjectName != "" ){
            cfgMap = context.getPE().getApplicationConfiguration(cfgObjectName);
            if( cfgMap.size() > 0 ){
                if (tracer.isInfoEnabled()) {
                	tracer.log(TraceLevel.INFO, "Found application configuration object");
                }            	
                // Override input parameters
                // Check if we have a eventStoreUser
                if( cfgMap.containsKey("eventStoreUser") ){
                    eventStoreUser = cfgMap.get("eventStoreUser");
                    if (tracer.isInfoEnabled()) {
                    	tracer.log(TraceLevel.INFO, "Config override eventStoreUser = " + eventStoreUser);
                    }
                }
                if( cfgMap.containsKey("eventStorePassword") ){
                    eventStorePassword = cfgMap.get("eventStorePassword");
                    if (tracer.isInfoEnabled()) {
                    	tracer.log(TraceLevel.INFO, "Config override eventStorePassword = *****");
                    }
                }
                if( cfgMap.containsKey("connectionString") ){
                    connectionString = cfgMap.get("connectionString");
                    if (tracer.isInfoEnabled()) {
                    	tracer.log(TraceLevel.INFO, "Config override connectionString  = " + connectionString);
                    }
                }
                if( cfgMap.containsKey("databaseName") ){
                    databaseName = cfgMap.get("databaseName");
                    if (tracer.isInfoEnabled()) {
                    	tracer.log(TraceLevel.INFO, "Config override databaseName  = " + databaseName);
                    }
                }
                if( cfgMap.containsKey("keyStorePassword") ){
                    keyStorePassword = cfgMap.get("keyStorePassword");
                    if (tracer.isInfoEnabled()) {
                    	tracer.log(TraceLevel.INFO, "Config override keyStorePassword  = *****");
                    }
                }
                if( cfgMap.containsKey("trustStorePassword") ){
                    trustStorePassword = cfgMap.get("trustStorePassword");
                    if (tracer.isInfoEnabled()) {
                    	tracer.log(TraceLevel.INFO, "Config override trustStorePassword  = *****");
                    }
                }
                if( cfgMap.containsKey("pluginName") ){
                    pluginName = cfgMap.get("pluginName");
                    if (tracer.isInfoEnabled()) {
                    	tracer.log(TraceLevel.INFO, "Config override pluginName  = " + pluginName);
                    }
                }
                if( cfgMap.containsKey("pluginFlag") ){
                    pluginFlag = Boolean.valueOf(cfgMap.get("pluginFlag"));
                    if (tracer.isInfoEnabled()) {
                    	tracer.log(TraceLevel.INFO, "Config override pluginFlag  = " + pluginFlag);
                    }
                }
                if( cfgMap.containsKey("sslConnection") ){
                    sslConnection = Boolean.valueOf(cfgMap.get("sslConnection"));
                    if (tracer.isInfoEnabled()) {
                    	tracer.log(TraceLevel.INFO, "Config override sslConnection  = " + sslConnection);
                    }
                }
            }
            else  {
            	tracer.log(TraceLevel.WARN, "Application configuration object is configured, but no valid properties found: " + cfgObjectName);
            } 
        }
        if (tracer.isInfoEnabled()) {
            tracer.log(TraceLevel.INFO, "Resulting eventStoreUser = " + eventStoreUser + " and passwd = *****");
            tracer.log(TraceLevel.INFO, "The max number of active batches is " + maxNumActiveBatches);
            tracer.log(TraceLevel.INFO, "frontEndConnectionFlag: " + this.frontEndConnectionFlag);
            tracer.log(TraceLevel.INFO, "sslConnection: " + this.sslConnection);
            tracer.log(TraceLevel.INFO, "pluginFlag: " + this.pluginFlag);
            tracer.log(TraceLevel.INFO, "pluginName: " + this.pluginName);
        }
        
        StreamingInput<Tuple> streamingInput = context.getStreamingInputs().get(0);
        StreamSchema streamSchema = streamingInput.getStreamSchema();

        if (impl == null) {
            this.trustStore = getAbsolutePath(this.trustStore);
            this.keyStore = getAbsolutePath(this.keyStore);
            if (null != this.keyStore) {
            	tracer.log(TraceLevel.INFO, "System.setProperty: javax.net.ssl.keyStoreType=PKCS12");
                System.setProperty("javax.net.ssl.keyStoreType", "PKCS12");
            }
            if( databaseName == null || databaseName == "" ||
                    tableName == null || tableName == "" ){
            	tracer.log(TraceLevel.ERROR, "No database or table name was given so we cannot carry out the insert");
            	throw new IllegalArgumentException("No database or table name was given so we cannot carry out the insert");
            }
            else{
            	if (tracer.isInfoEnabled()) {
            		tracer.log(TraceLevel.INFO, "Connect to DB " + databaseName + " with " + connectionString);
            		tracer.log(TraceLevel.INFO, "tableName= " + tableName );
            	}
            	try {
            		// Set up connection to the IBM Db2 Event Store engine
                    impl = EventStoreSinkImpl/*EventStoreSinkJImplObject*/.mkWriter(databaseName, tableName, schemaName,
                        connectionString, frontEndConnectionFlag, streamSchema, nullMapString,
                        eventStoreUser, eventStorePassword,
                        partitioningKey, primaryKey,
                        sslConnection, trustStore, trustStorePassword, keyStore, keyStorePassword,
                        pluginName, pluginFlag);
                
                } catch(Exception e) {
                    tracer.log(TraceLevel.ERROR, "Failed to connect to IBM Db2 Event Store.\n"+ stringifyStackTrace(e) );
                    throw e;
                }
            }
        }
        // If the batch size if not provided calculate the default batchSize
        if( !batchSizeParmSet ){
            batchSize = impl.calcDefaultBatchSize();
            if (tracer.isInfoEnabled()) {
            	tracer.log(TraceLevel.INFO, "Set default batch size: " + batchSize );
            }
        }

        batch = newBatch();

    }

     
    private LinkedList</*Row*/Tuple> newBatch() {
        return new LinkedList</*Row*/Tuple>();
    }

    /**
     *      * Notification that initialization is complete and all input and output ports 
     *           * are connected and ready to receive and submit tuples.
     *                * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     *                     */
    @Override
    public synchronized void allPortsReady() throws Exception {
	OperatorContext context = getOperatorContext();
        Logger.getLogger(this.getClass()).trace("Operator " + context.getName() + " all ports are ready in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() ); 
        
        // Set up a thread for each active batch so they can be processed asynchronously
        insertThread = new Thread[maxNumActiveBatches];
        for( int i=0; i<maxNumActiveBatches && i<10; i++ ){
            insertThread[i] = context.getThreadFactory().newThread(new InsertRunnable());
            insertThread[i].start();
        }
    }

    /**
     * Process incoming tuples by adding them to the current batch
     * and submitting the batch for execution if required.
     */
    @Override
    public /*synchronized*/ void process(StreamingInput<Tuple> stream, Tuple tuple)
            throws Exception {
        LinkedList</*Row*/Tuple> asyncBatch;

        synchronized (this) {
            batch.add(/*new GenericRow(listofObj)); */tuple); 

            if (batch.size() < getBatchSize()) {
            /*
            if (getBatchTimeout() == 0)
                return;
            
            if (activeTimeout != null && !activeTimeout.isDone())
                return;
            
            // If we have a timeout for batch submission then the
            // first time a tuple gets added to the batch create a
            // timeout event to process the batch in case no more tuples come in.
            {
                activeTimeout = getOperatorContext()
                        .getScheduledExecutorService().schedule(
                                new Callable<Object>() {
                                    public Object call() throws Exception {
                                        submitBatch(false);
                                        return null;
                                    }
                                }, getBatchTimeout(), getBatchTimeoutUnit());
            }
            */
                return;
            }

            // At this point the batch is full so move it to a local variable which will then be used to
            // copy into the batch queue
            asyncBatch = batch;
            batch = newBatch();
        }

        // Add the new batch to the batchQueue which will cause it to block if there is no space available
        batchQueue.put(asyncBatch);
    }
    
    /**
     * Process any final punctuation by flushing the batch
     * and waiting for all the work to complete.
     */
    public void processPunctuation(StreamingInput<Tuple> port, Punctuation mark)
          throws Exception {
        // No need to call super.processPunctuation because that just forwards
        // window punctuation marks to the output ports and this operator
        // doesn't support output ports.
        
        // If a final marker comes in then need to flush out any remaining tuples.
        if (mark == Punctuation.FINAL_MARKER) {
            flushTuples();
        }
    }
    
    /**
     * Process all batched tuples from this port.
     */
    private synchronized void flushTuples() throws Exception {
    	if (tracer.isInfoEnabled()) { // use info level since it is called at final marker only
    		tracer.log(TraceLevel.INFO, "flushTuples >>");
    	}	
        LinkedList</*Row*/Tuple> asyncBatch = null;

        if( batch != null && !batch.isEmpty() ){
            asyncBatch = batch;
            batch = newBatch();
            batchQueue.put(asyncBatch);
        }
        
        if( batchQueue != null && batchQueue.peek() != null ) {
            while (batchQueue != null && batchQueue.peek() != null) {
                Thread.sleep(1000);
            }
        }
        if (hasResultsPort) {
	        Thread.sleep(1000);
			while (getActiveInsertsMetric().getValue() >= 1) {
				Thread.sleep(100);
			}
        }
    	if (tracer.isInfoEnabled()) {
    		tracer.log(TraceLevel.INFO, "flushTuples <<");
    	}
    }
    
    /**
     * Shutdown this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public synchronized void shutdown() throws Exception {
        OperatorContext context = getOperatorContext();
        if (tracer.isInfoEnabled()) {
    		tracer.log(TraceLevel.INFO, "Operator " + context.getName() + " shutting down in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );
    	}
        shutdown = true;

        if( batchQueue != null ){
	    batchQueue.clear();
	}
        if(impl != null) {
            impl.shutdown();
            impl = null;
        }
        // Must call super.shutdown()
        super.shutdown();
    }

    /**
     * Get the batch size.
     * 
     * @return Current batch size, defaults to 1 if setBatchSize() has not been
     *         called.
     */
    public final synchronized int getBatchSize() {
        return batchSize;
    }

    @Parameter(name="databaseName", optional=true, description = "The name of an existing IBM Db2 Event Store database in order to connect.")
    public void setDatabaseName(String s) {databaseName = s;}

    @Parameter(name="tableName", description = "The name of the table into which you want to insert data from the IBM Streams application. If the table does not exist, the table is automatically created in IBM Db2 Event Store.")
    public void setTableName(String s) {tableName = s;}

    @Parameter(name="schemaName",  optional=true,
               description = "The name of the table schema name of the table into which to insert data. If not used the default will be the user id.")
    public void setSchemaName(String s) {
    	if (!("".equals(s))) {
    		schemaName = s;
    	}
    }

    private String stringifyStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Set the IBM Db2 Event Store connection string
     * @param connectionString
     *            If null then the connectString must be set in application configuration
     */
    @Parameter(name="connectionString", optional=true,
            description="Specifies the IBM Db2 Event Store connection endpoint as a set of IP addresses and ports: <IP>:<jdbc-port>;<IP>:<port>. Separate multiple entries with a comma.")
    public synchronized void setConnectionString( String connectString ) {
        connectionString = connectString;
    }

    /**
     * If set to true then it will allow the connection to be used as part of a Secure Gateway for
     * enterprise > version 1.1.2 and desktop > 1.1.4.
     * 
     * @param batchTimeout
     *            Timeout value
     * @param unit
     *            Unit of the timeout value
     */
    @Parameter(name="frontEndConnectionFlag", optional=true,
            description="Set to true to connect through a Secure Gateway for Event Store")
    public synchronized void setUseFrontendConnectionEndpoints(boolean frontEndConnectionFlag ) {
        this.frontEndConnectionFlag = frontEndConnectionFlag;
    }

    /**
     * Set the current batch size. If at least batchSize tuples are currently batched
     * then batch processing will be initiated asynchronously.
     * 
     * @param batchSize
     *            New batch size, must be one or greater.
     */
    @Parameter(name="batchSize", optional=true,
            description="Specifies the batch size for the number of rows that will be batched in the operator before the batch is inserted into IBM Db2 Event Store by using the `batchInsertAsync` method. If you do not specify this parameter, the batchSize defaults to the estimated number of rows that could fit into an 8K memory page.")
    public synchronized void setBatchSize(int batchSize) throws Exception {
        if (batchSize <= 0)
            throw new IllegalArgumentException(
                    "Sink batchSize must be not be zero or negative.");

        this.batchSize = batchSize;
        batchSizeParmSet = true;

        if( batchSize > maxBatchSize ){
            tracer.log(TraceLevel.ERROR, "You exceeded the maximum batchsize so resetting to max = " + maxBatchSize );
            this.batchSize = maxBatchSize;
        }

        // When called via the Parameter annotation
        // there will not batch set up.
        if (batch != null) {
            if (batch.size() >= batchSize && batchQueue != null && batchQueue.size() < numMaxActiveBatches*maxNumActiveBatches ) {
                try {
                    batchQueue.put(batch);
                    batch = newBatch();
                    //submitBatch(false);
                } catch(Exception e) {
                    tracer.log(TraceLevel.ERROR, "Could not add the current batch to the batchQueue");
                }
            }
        }
    }

    /**
     * Set the maxNumActiveBatches information which will be the number of active batches
     * allowed while executing in the sink operator. There will be one extra batch that will be
     * filled with rows at the same time. The number of active batches needs to be >= 1.
     *
     * @param maxNumActiveBatches
     */
    @Parameter(name="maxNumActiveBatches", optional=true,
            description="Set the maximum number of active batches.")
    public synchronized void setMaxNumActiveBatches(int maxNumActiveBatches){
        if( maxNumActiveBatches >= 1 ) {
            this.maxNumActiveBatches = maxNumActiveBatches;
        }
    }

    /**
     * Set the nullMapString information which will be null or a json string
     * that will have the column name and a value in the column's data domain
     * that represents a NULL value since IBM streams does not pass null values.
     * 
     * @param nullMapString
     */
    @Parameter(name="nullMapString", optional=true,
            description="Set the null map JSON represented by a string.")
    public synchronized void setNullMapString(String nullMapString){
        this.nullMapString = nullMapString;
    }

    /**
     * Set the configObject which is the name of the IBM Streams configuration object that
     * will contain parameter settings that will override user parameter
     * settings and is used to hide security related information such
     * as eventStoreUser and eventStorePassword and connectionString, databaseName
     * 
     * @param configObject
     */

    @Parameter(name = "configObject", optional=true, 
               description = "Specify the application configuration name. An application configuration can be created in the Streams Console or using the `streamtool mkappconfig ... <configObject name>`. If you specify parameter values (properties) in the configuration object, they override the values that are configured for the EventStoreSink operator. Supported properties are: `connectionString`, `databaseName`, `eventStoreUser`, `eventStorePassword`, `keyStorePassword`, `trustStorePassword`, `pluginName`, `pluginFlag`, `sslConnection`")
    public void setConfigObject(String s) { 
    	if (!("".equals(s))) {
    		cfgObjectName = s;
    	}
    }

    /**
     * Set the Userid information which will be null or a string
     * 
     * @param eventStoreUser
     */

    @Parameter(name="eventStoreUser", optional=true,
		description = "Name of the IBM Db2 Event Store User in order to connect.")
    public void setEventStoreUser(String s) {
    	if (!("".equals(s))) {
    		eventStoreUser = s;
    	}
    }

    /**
     * Set the password information which will be null or a string
     * 
     * @param eventStorePassword
     */

    @Parameter(name="eventStorePassword", optional=true,
		description = "Password for the IBM Db2 Event Store User in order to connect.")
    public void setEventStorePassword(String s) {
    	if (!("".equals(s))) {
    		eventStorePassword = s;
    	}
    }

    /**
     * Set the shard/partitioning key where if its not in the input or empty
     * or not defined then the default will be the first column of the table
     * This is only used when we dynamically have to create the table
     * 
     * @param partitioningKey
     */

    @Parameter(name="partitioningKey", optional=true,
		description = "Partitioning key for the table. A string of attribute names separated by commas. The order of the attribute names defines the order of entries in the sharding key for the IBM Db2 Event Store table. The attribute names are the names of the fields in the stream. The `partitioningKey` parameter is used only if the table does not yet exist in the IBM Db2 Event Store database. If you do not specify this parameter or if the key string is empty, the key defaults to making the first column in the stream as the shard key. For example, \\\"col1, col2, col3\\\"")
    public void setPartitioningKey(String s) {partitioningKey = s;}

    /**
     * Set the primary key where if its not in the input or empty
     * or not defined then the default will be no primary key.
     * This is only used when we dynamically have to create the table
     * 
     * @param primaryKey
     */

    @Parameter(name="primaryKey", optional=true,
		description = "Primary key for the table. A string of attribute names separated by commas. The order of the attribute names defines the order of entries in the primary key for the IBM Db2 Event Store table. The attribute names are the names of the fields in the stream. The `primaryKey` parameter is used only, if the table does not yet exist in the IBM Db2 Event Store database. If you do not specify this parameter, the resulting table has an empty primary key.")
    public void setPrimaryKey(String s) {primaryKey = s;}

    
	@Parameter(name = "sslConnection", optional = true, 
			description = "This optional parameter specifies whether an SSL connection should be made to the database. The default value is `true`.")
	public void setSslConnection(boolean sslConnection) {
		this.sslConnection = sslConnection;
	}
    
	public boolean isSslConnection() {
		return sslConnection;
	}

	// Parameter keyStore
	@Parameter(name = "keyStore" , optional = true, 
			description = "This parameter specifies the path to the keyStore file for the SSL connection. If a relative path is specified, the path is relative to the application directory.")
	public void setKeyStore(String keyStore) {
		if (!("".equals(keyStore))) {
			this.keyStore = keyStore;
		}
	}

	public String getKeyStore() {
		return keyStore;
	}

	// Parameter keyStorePassword
	@Parameter(name = "keyStorePassword", optional = true, 
			description = "This parameter specifies the password for the keyStore given by the **keyStore** parameter.")
	public void setKeyStorePassword(String keyStorePassword) {
		if (!("".equals(keyStorePassword))) {
			this.keyStorePassword = keyStorePassword;
		}
	}

	public String getKeyStorePassword() {
		return keyStorePassword;
	}

	// Parameter trustStore
	@Parameter(name = "trustStore", optional = true, 
			description = "This parameter specifies the path to the trustStore file for the SSL connection. If a relative path is specified, the path is relative to the application directory.")
	public void setTrustStore(String trustStore) {
		if (!("".equals(trustStore))) {
			this.trustStore = trustStore;
		}
	}

	public String getTrustStore() {
		return trustStore;
	}

	// Parameter trustStorePassword
	@Parameter(name = "trustStorePassword", optional = true, 
			description = "This parameter specifies the password for the trustStore given by the **trustStore** parameter.")
	public void setTrustStorePassword(String trustStorePassword) {
		if (!("".equals(trustStorePassword))) {
			this.trustStorePassword = trustStorePassword;
		}
	}

	public String getTrustStorePassword() {
		return trustStorePassword;
	}
	
	// Parameter pluginName
	@Parameter(name = "pluginName", optional = true, 
			description = "This parameter specifies the plug-in name for the SSL connection. The default value is `IBMPrivateCloudAuth`.")
	public void setPluginName(String pluginName) {
		if (!("".equals(pluginName))) {
			this.pluginName = pluginName;
		}
	}

	public String getPluginName() {
		return pluginName;
	}
	
	//Parameter pluginFlag
	@Parameter(name = "pluginFlag", optional = true, 
			description = "This parameter specifies whether plug-in is enabled for the SSL connection. The default value is `true`.")
	public void setPluginFlag(boolean pluginFlag) {
		this.pluginFlag = pluginFlag;
	}
    
	public boolean getPluginFlag() {
		return pluginFlag;
	}
    
	protected String getAbsolutePath(String filePath) {
		if (filePath == null)
			return null;

		Path p = Paths.get(filePath);
		if (p.isAbsolute()) {
			return filePath;
		} else {
			File f = new File(getOperatorContext().getPE().getApplicationDirectory(), filePath);
			return f.getAbsolutePath();
		}
	}	
	
    /**
     * Get the batch timeout value.
     * 
     * @return Current batch timeout, defaults to 0 (no timeout) if
     *         setBatchTimeout has not been called.
     */
    public synchronized final long getBatchTimeout() {
        return batchTimeout;
    }

    /**
     * (NOT used at the moment) Set the batch timeout. The batch timeout is the amount of time the
     * operator will wait after the first tuple in the batch before processing
     * the batch. For example with a batch size of 100 and a timeout of two
     * seconds a batch will be processed when whichever comes first out of 100
     * tuples are in the batch or 2 seconds have passed since the first tuple
     * was added. <BR>
     * This method should be made at initialization time, within the initialize
     * method. Calling it once tuples are being received and batched may have
     * unpredictable effects.
     * 
     * A timeout of 0 means no timeout.
     * 
     * @param batchTimeout
     *            Timeout value
     * @param unit
     *            Unit of the timeout value
     */
    public final synchronized void setBatchTimeout(long batchTimeout,
            TimeUnit unit) {
        this.batchTimeout = batchTimeout;
        this.batchTimeoutUnit = unit;
    }

    private void submitResultTuple(LinkedList</*Row*/Tuple> batch, boolean inserted) throws Exception {
        // If we were not able to carry out the insertions
        /// then send the error result through the output port
        // but if the batch was inserted properly then send
        // successful message to the output port
    	
    	if (hasResultsPort) {
    		// Now send all tuples with result to the output port
    		for (Iterator</*Row*/Tuple> it = batch.iterator();
    				it.hasNext(); ) {
    			/*Row r*/Tuple tuple = it.next(); 

            	// Create a new tuple for output port 0
            	OutputTuple outTuple = resultsOutputPort.newTuple();

            	// Copy incoming attributes to output tuple if they're
            	//in the output schema
            	outTuple.assign(tuple);

            	try {
            		// Get attribute from input tuple and manipulate
            		// where a true value means the row was inserted 
            		outTuple.setBoolean("_Inserted_", inserted);
            	} catch (Exception e) {
            		tracer.log(TraceLevel.ERROR, "Failed to add inserted field to output");
            	}

            	// Submit new tuple to output port 0
            	resultsOutputPort.submit(outTuple);
    		}
    	}  
    }
    
	public void updateInsertSpeedMetrics (long insertDuration) {
		if (false == isInsertSpeedMetricSet) {
			// set initial values after first upload
			this.lowestInsertTime.setValue(insertDuration);
			this.highestInsertTime.setValue(insertDuration);
			this.averageInsertTime.setValue(insertDuration);
			isInsertSpeedMetricSet = true;
		}
		else {
			// metrics for insert duration (time to insert a batch)
			if (insertDuration < this.lowestInsertTime.getValue()) {
				this.lowestInsertTime.setValue(insertDuration);
			}
			if (insertDuration > this.highestInsertTime.getValue()) {
				this.highestInsertTime.setValue(insertDuration);
			}
			insertTimes.add(insertDuration);
			// calculate average
			long total = 0;
			for(int i = 0; i < insertTimes.size(); i++) {
			    total += insertTimes.get(i);
			}
			this.averageInsertTime.setValue(total / insertTimes.size());
			// avoid that arrayList is growing unlimited
			if (insertTimes.size() > 10000) {
				insertTimes.remove(0);
			}
		}
	}   
    
    /**
     * Process a batch of tuples.
     * 
     * This call is made asynchronously with incoming tuples and tuples that
     * arrive after this processing was initiated will not be added to set
     * defined by the input collection batch. Such tuples will be processed
     * in a future batch. <BR>
     * The expected case is that this method will process all tuples in the
     * batch. 
     * No assumptions should be made about the number of tuples in the passed in
     * collection batch, it may be less than, equal to or greater than the
     * current batch size. <BR>
     * 
     * @param batch
     *            Batch of tuples to be processed, its contents are not modified
     *            by any incoming tuples.
     * 
     * @return True there are tuples left in batch in a future batch, false no
     *         tuples are left to be processed
     */
    protected /*abstract*/ boolean processBatch(LinkedList</*Row*/Tuple> batch)
            throws Exception {
      if (tracer.isDebugEnabled()) {
    		tracer.log(TraceLevel.DEBUG, "Process a new BATCH ********** Number of tuples = " + batch.size());
      }

      if( batch.size() <= 0 )
          return false;

      // Now put all the tuples into a list and pass to the insertTuple API

      if(impl != null){
          if( numBatchesPerInsert != null) numBatchesPerInsert.setValue((long)1);//numInQueue +1);
          getActiveInsertsMetric().increment();
          long startTime = 0;
          long endTime = 0;
          try{
              startTime = System.currentTimeMillis();

              impl.insertTuple(batch); 

              endTime = System.currentTimeMillis() - startTime;
              if (tracer.isDebugEnabled()) {
          		tracer.log(TraceLevel.DEBUG, "Insert (success time) = " + endTime);
          		tracer.log(TraceLevel.DEBUG, "*** SUCCESSFUL INSERT");
              }
              getWriteSuccessesMetric().increment();
              // update time metrics
              updateInsertSpeedMetrics(endTime);
              // submit result tuple if output port is present
              submitResultTuple(batch, true);
              batch.clear();
              getActiveInsertsMetric().incrementValue(-1);
          } catch(Exception e) {
              endTime = System.currentTimeMillis() - startTime;
              if (tracer.isDebugEnabled()) {
          		tracer.log(TraceLevel.DEBUG, "Insert (failure time) = " + endTime);
              }
              getWriteFailuresMetric().increment();
              tracer.log(TraceLevel.ERROR, "Failed to write tuple to EventStore.\n"+ stringifyStackTrace(e) );
              // submit result tuple if output port is present
              submitResultTuple(batch, false);
              getActiveInsertsMetric().incrementValue(-1);
              if (inConsistentRegion()) {
            	  // In a consitent region just throw the error so we can restart gracefully
                  tracer.log(TraceLevel.ERROR, "Failed to write tuple to EventStore so now THROW exception in processBatch");
                  throw e;
              } else {
                  return true;
              }
          }
      }
      return false; // Do not re-insert // true;
    }


    /**
     * (Not used as we are always in order) Set if order of tuples is preserved in the batch and in the calls to
     * processBatch. If set to true then no concurrent calls will be made to
     * processBatch. <BR>
     * This method should be made at initialization time, within the initialize
     * method. Calling it once tuples are being received and batched may have
     * unpredictable effects.
     * 
     * @param preserveOrder
     *            True to preserve order, false to not care about ordering.
     */
    @Parameter(name="preserveOrder", optional=true,
            description = "Preserve order if true")
    public final synchronized void setPreserveOrder(boolean preserveOrder) {
        this.preserveOrder = preserveOrder;
    }

    /**
     *  (Not used as we are always in order) True if arrival order is to be preserved when processing tuples.
     * @return True if arrival order is to be preserved.
     */
    public synchronized final boolean isPreserveOrder() {
        return preserveOrder;
    }

    public synchronized TimeUnit getBatchTimeoutUnit() {
        return batchTimeoutUnit;
    }
    
	public Metric getActiveInsertsMetric() {
		return nActiveInserts;
	}

	public Metric getWriteFailuresMetric() {
		return nWriteFailures;
	}
	
	public Metric getWriteSuccessesMetric() {
		return nWriteSuccesses;
	}
	
	// operator and port documentation -------------------------------------------------------------------------------------------------------

	static final String operatorDescription = 
			"The EventStoreSink inserts IBM Streams tuples in to an IBM Db2 Event Store table."
			;
	
	static final String iport0Description =
			"Port that ingests tuples which are inserted into Db2 Event Store database table. "
			+ "The tuple field types and positions in the IBM Streams schema must match the field names in your IBM Db2 Event Store table schema exactly."
			+ "Incoming tuples are processed in batches, where the processing will be to send rows to IBM Db2 Event Store, and is driven by the size of the batch. A batch is processed when it reaches at least the batch size."
			;

	static final String oport0Description =
			"Port that optionally produces tuple insert results."
			+ "\\nThis output port is intended to output the information on whether a tuple was successful or not when it was inserted into the database. EventStoreSink looks for a Boolean field called `_Inserted_` in the output stream. EventStoreSink sets the field to `true` if the data was successfully inserted and `false` if the insert failed." 
			+ "\\nBesides the `_Inserted_` column, the output will include the original tuple attributes (from input stream) that was processed by the EventStoreSink operator. "
			;

    public static final String DATA_TYPES =
			"\\n"+
			"\\n+ Data Types\\n"+
			"\\n"
            + "---\\n"
            + "| SPL type | Support status | Event Store type |\\n"
            + "|===|\\n"
            + "| boolean | Supported | Boolean |\\n"
            + "|---|\\n"
            + "| enum | Not supported | N/A |\\n"
            + "|---|\\n"
            + "| int8 | Supported | Byte |\\n"
            + "|---|\\n"
            + "| int16 | Supported | Short |\\n"
            + "|---|\\n"
            + "| int32 | Supported | Int |\\n"
            + "|---|\\n"
            + "| int64 | Supported | Long |\\n"
            + "|---|\\n"
            + "| uint8 | Supported | Byte |\\n"
            + "|---|\\n"
            + "| uint16 | Supported | Short |\\n"
            + "|---|\\n"
            + "| uint32 | Supported | Int |\\n"
            + "|---|\\n"
            + "| uint64 | Supported | Long |\\n"
            + "|---|\\n"
            + "| float32 | Supported | Float |\\n"
            + "|---|\\n"
            + "| float64 | Supported | Double |\\n"
            + "|---|\\n"
            + "| decimal32 | Not supported | N/A |\\n"
            + "|---|\\n"
            + "| decimal64 | Not supported | N/A |\\n"
            + "|---|\\n"
            + "| decimal128 | Not supported | N/A |\\n"
            + "|---|\\n"
            + "| complex32 | Not supported | N/A |\\n"
            + "|---|\\n"
            + "| complex64 | Not supported | N/A |\\n"
            + "|---|\\n"
            + "| timestamp | Supported | java.sql.Timestamp |\\n"
            + "|---|\\n"
            + "| rstring | Supported | String |\\n"
            + "|---|\\n"
            + "| ustring | Supported | String |\\n"
            + "|---|\\n"
            + "| blob | Not supported | N/A |\\n"
            + "|---|\\n"
            + "| xml | Not supported | N/A |\\n"
            + "|---|\\n"
            + "| list<T> | Only one level supported | Array<T> |\\n"
            + "|---|\\n"
            + "| bounded list type | Not supported | N/A |\\n"
            + "|---|\\n"
            + "| set<T> | Only one level supported | Array<T> |\\n"
            + "|---|\\n"
            + "| bounded set type | Not supported | N/A |\\n"
            + "|---|\\n"
            + "| map<K,V> | Only one level supported | Map<K,V> |\\n"
            + "|---|\\n"
            + "| bounded map type | Not supported | N/A |\\n"
            + "|---|\\n"
            + "| tuple<T name, ...> | Not supported | N/A |\\n"
            + "---\\n"
            +"\\nIn the preceding table \\\"only one level supported\\\" means the array or map has elements and keys that are primitive data types."           
            ;
	
	public static final String SPL_EXAMPLES_DESC =
			"\\n"+
			"\\n+ Example\\n"+
			"\\nSPL example demonstrates the usage of the EventStoreSink operator:\\n" +
		    "\\n    composite Main {"+
		    "\\n        param"+
		    "\\n            expression<rstring> $connectionString: getSubmissionTimeValue(\\\"connectionString\\\");"+
		    "\\n            expression<int32>   $batchSize: (int32)getSubmissionTimeValue(\\\"batchSize\\\", \\\"1000\\\");"+
		    "\\n            expression<rstring> $databaseName: getSubmissionTimeValue(\\\"databaseName\\\");"+
		    "\\n            expression<rstring> $tableName: getSubmissionTimeValue(\\\"tableName\\\");"+
		    "\\n            expression<rstring> $schemaName: getSubmissionTimeValue(\\\"schemaName\\\");"+
		    "\\n            expression<rstring> $eventStoreUser: getSubmissionTimeValue(\\\"eventStoreUser\\\", \\\"\\\");"+
		    "\\n            expression<rstring> $eventStorePassword: getSubmissionTimeValue(\\\"eventStorePassword\\\", \\\"\\\");"+ 
		    "\\n    "+
			"\\n        graph"+
			"\\n    "+
			"\\n            stream<rstring key, uint64 dummy> Rows = Beacon() {"+
			"\\n                param"+
			"\\n                    period: 0.01;"+
			"\\n                output"+
			"\\n                    Rows : key = \\\"SAMPLE\\\"+(rstring) IterationCount();"+
			"\\n            }"+
			"\\n    "+
			"\\n            () as Db2EventStoreSink = com.ibm.streamsx.eventstore::EventStoreSink(Rows) {"+
			"\\n                param"+
			"\\n                    batchSize: $batchSize;"+
			"\\n                    connectionString: $connectionString;"+
			"\\n                    databaseName: $databaseName;"+
			"\\n                    primaryKey: 'key';"+
			"\\n                    tableName: $tableName;"+
			"\\n                    schemaName: $schemaName;"+
			"\\n                    eventStoreUser: $eventStoreUser;"+
			"\\n                    eventStorePassword: $eventStorePassword;"+
			"\\n            }"+
			"\\n    }"+	
			"\\n"			
			;	
	
	public static final String CR_DESC =
			"\\n"+
			"\\n+ Behavior in a consistent region\\n"+
			"\\n"+
			"\\nThe operator can participate in a consistent region. " +
			"The operator can be part of a consistent region, but cannot be at the start of a consistent region.\\n" +
			"\\nConsistent region supports that tuples are processed at least once.\\n" +
			"\\nFailures during tuple processing or drain are handled by the operator and consistent region support.\\n" +
			"\\n# Inserts\\n"+
			"\\nIncoming tuples are processed in batches, where the queued tuples will be to send rows to IBM Db2 Event Store, when the configured size of the batch is reached.\\n"+
			"\\nOn drain, the operator flushes its internal buffer and inserts the (remaining) queued tuples as batch into the database.\\n" +
			"\\n# Checkpoint and restore\\n"+
			"\\nOn checkpoint, the values of the metrics (`nWriteSuccesses` and `nWriteFailures`) are written to the checkpoint.\\n" + 
			"\\nOn reset, the internal buffer is cleared and metrics are restored with values that are read from checkpoint.\\n"
			;	
}

