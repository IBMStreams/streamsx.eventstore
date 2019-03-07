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

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.spark.sql.catalyst.expressions.GenericRow;
import org.apache.spark.sql.Row;

import com.ibm.streams.operator.AbstractOperator;
import com.ibm.streams.operator.OperatorContext;
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
import com.ibm.streams.operator.metrics.*;
import com.ibm.streams.operator.state.Checkpoint;
import com.ibm.streams.operator.state.ConsistentRegionContext;
import com.ibm.streams.operator.state.StateHandler;

import com.ibm.streamsx.eventstore.EventStoreSinkImpl;
//import com.ibm.streamsx.eventstore.EventStoreSinkJImplObject;
//import com.ibm.streamsx.eventstore.EventStoreSinkJImpl;
import org.apache.log4j.Logger;
import scala.collection.JavaConversions;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * IBM Db2 Event Store streams sink operator class 
 * that consumes tuples and does not
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
@PrimitiveOperator(name="EventStoreSink", namespace="com.ibm.streamsx.eventstore",
        description="Java Operator EventStoreSink")
@InputPorts({
        @InputPortSet(description="Port that ingests tuples", cardinality=1, optional=false, windowingMode=WindowMode.NonWindowed, windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious),
        @InputPortSet(description="Optional input ports", optional=true, windowingMode=WindowMode.NonWindowed, windowPunctuationInputMode=WindowPunctuationInputMode.Oblivious)
})
@OutputPorts({
    @OutputPortSet(description="Port that optionally produces tuple insert results", cardinality=1, optional=true)
})

public class EventStoreSink extends AbstractOperator implements StateHandler {
    /* begin_generated_IBM_copyright_code */
    public static final String IBM_COPYRIGHT = 
	"/*******************************************************************************" +
	"* Copyright (C) 2017, International Business Machines Corporation" +
	"* All Rights Reserved" +
	"*******************************************************************************/";

    Logger log = Logger.getLogger(this.getClass());


    EventStoreSinkImpl/* EventStoreSinkJImpl*/ impl = null;

    String databaseName = null;
    String tableName = null;
    String connectionString = null;
    boolean frontEndConnectionFlag = false;
    String eventStoreUser = null;
    String eventStorePassword = null;
    String partitioningKey = "";
    String primaryKey = "";
    private String cfgObjectName = "";
    private Map<String, String> cfgMap;

    // Flag to specify if the optional tuple insert result port is used
    private boolean hasResultsPort = false;
    // Variable to specify tuple insert result output port
    private StreamingOutput<OutputTuple> resultsOutputPort;

    String nullMapString = null;

    OperatorMetrics opMetrics = null;

    /* Metrics include the count of the number of batches that failed to be processed by IBM Db2 Event Store
     *  (failures), the number of successful batches sent and processed by Event Store (successes), 
     *  the time spent by Event Store to process a batch (insertGaugeTime), and at any given batch processing
     *  time there is a count of the number of batches processed together (numBatchesPerInsert).
     */
    Metric failures = null;
    Metric successes = null;
    Metric insertGaugeTime = null;
    Metric numBatchesPerInsert = null;

    /* end_generated_IBM_copyright_code */

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

    /* InsertRunnable is the process that obtains a batch from a queue and processes it by sending to IBM Db2 Event Store.
     * It should be noted that this will execute asynchronously with the process that receives rows from the input.
     */
    private class InsertRunnable implements Runnable {
        @Override
	public void run(){
			long threadId = Thread.currentThread().getId();
                        while(!shutdown ) {
                            try {
                                // Takea batch from the queue and process it
                                LinkedList</*Row*/Tuple> asyncBatch = batchQueue.take();
                                if( asyncBatch == null ){
                                    continue;
                                }

                                log.info("Found a non-empty batch so call insert from submitbatch: thread " + threadId);
                                //log.info("Before processBatch KB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024);

                                // Process the batch with sending it to IBM Db2 Event Store
                                boolean addLeftovers = processBatch(asyncBatch);

                                //log.info("After processBatch KB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024);

                                log.info("In submitBatch call after processbatch with addLeftovers = " + addLeftovers + 
					" with thread " + threadId);

                            } catch (Exception e) {
				log.error("Found exception in InsertRunnable thread " + threadId + " messagee:" + e.getMessage() );
				throw new RuntimeException(e);
	    		    } finally {
                		if(crContext != null) {
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
        log.info( "CLOSE the IBM Db2 Event Store sink operator"  );
    }

    @Override
    public void checkpoint(Checkpoint checkpoint) throws Exception {
        log.info( "CHECKPOINT the IBM Db2 Event Store sink operator Checkpoint sequence ID = " + checkpoint.getSequenceId()  );

        // Write out a checkpoint state and not the order of writes must be adhere to in reset for order of reads
        checkpoint.getOutputStream().writeLong(failures.getValue());
        checkpoint.getOutputStream().writeLong(successes.getValue());
        //checkpoint.getOutputStream().writeLong(insertGaugeTime.getValue()); does not need to be save as its a single value in time
        //checkpoint.getOutputStream().writeLong(numBatchesPerInsert.getValue()); does not need to be save as its a single value in time
        log.info( "CHECKPOINT completed with failcount = " + failures.getValue() + " success count = " + successes.getValue());
    }

    @Override
    public void drain() throws Exception {
        log.info( "DRAIN called for the IBM Db2 Event Store sink operator" );

        synchronized(this) {
            if( batch != null && !batch.isEmpty() ){
                LinkedList</*Row*/Tuple> asyncBatch = null;
                asyncBatch = batch;
                batch = newBatch();
                batchQueue.put(asyncBatch);
            }
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
        log.info( "DRAIN completed" );
    }

    @Override
    public void reset(Checkpoint checkpoint) throws Exception {
        log.info( "RESET called for the IBM Db2 Event Store sink operator" + checkpoint.getSequenceId() );

        long failCount = checkpoint.getInputStream().readLong();
        long sucessCount = checkpoint.getInputStream().readLong();
        log.info( "RESET failcount = " + failCount + " success count = " + sucessCount);
        impl = null; // unset the connection information to Event Store
        startOperatorSetup(getOperatorContext(), failCount, sucessCount);
        log.info( "RESET completed" );
    }

    @Override
    public void resetToInitialState() throws Exception {
        log.info( "RESETToINITIALSTATE called for the IBM Db2 Event Store sink operator" );
        // Initialize the operator information from scratch
        impl = null; // unset the connection information to Event Store
        startOperatorSetup(getOperatorContext(), 0L, 0L);
        log.info( "RESETToINITIALSTATE completed" );
    }

    @Override
    public void retireCheckpoint(long id) throws Exception {
        log.info( "RETIRECHECKPOINT called for the IBM Db2 Event Store sink operator id= " + id );
    }

    private boolean inConsistentRegion() {
        // check if this operator is within a consistent region
        ConsistentRegionContext cContext = getOperatorContext().getOptionalContext(ConsistentRegionContext.class);

        if(cContext != null) {
            return true;
        } else {
            return false;
        }
    }

    private synchronized void startOperatorSetup(OperatorContext context, Long failCount, Long successCount)
            throws Exception {
        log.trace("Operator " + context.getName() + " initializing in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );

        opMetrics = null;
        failures = null;
        successes = null;
        insertGaugeTime = null;
	numBatchesPerInsert = null;

        // Get the consistent region context
        crContext = context.getOptionalContext(ConsistentRegionContext.class);

        batchQueue = new ArrayBlockingQueue<LinkedList</*Row*/Tuple>>(numMaxActiveBatches*maxNumActiveBatches);

        // Set up the metrics to count the number of batch insert failures and successes.
        // If the metrics exists then use those, otherwise create them. Then use the input counts
        // to set them to previous values that existed at a checkpoint when consistent regions are used.
        opMetrics = getOperatorContext().getMetrics();

        try {
            failures = opMetrics.createCustomMetric("nWriteFailures",
                    "Number of tuples that failed to get written to IBM Db2 Event Store", Metric.Kind.COUNTER);
        } catch(Exception e) {
            log.error("nWriteFailures metric exists.\n" + stringifyStackTrace(e));
            failures = opMetrics.getCustomMetric("nWriteFailures");
        }

        try {
            successes = opMetrics.createCustomMetric("nWriteSuccesses",
                    "Number of tuples that were written to IBM Db2 Event Store successfully", Metric.Kind.COUNTER);
        } catch(Exception e) {
            log.error("nWriteSuccesses metric exists.\n" + stringifyStackTrace(e));
            successes = opMetrics.getCustomMetric("nWriteSuccesses");
        }

        try {
            insertGaugeTime = opMetrics.createCustomMetric("PerInsertTime",
                    "Time it takes to perform a successful batch insert", Metric.Kind.GAUGE);
        } catch(Exception e) {
            log.error("PerInsertTime metric exists.\n" + stringifyStackTrace(e));
            insertGaugeTime = opMetrics.getCustomMetric("PerInsertTime");
        }

        try {
            numBatchesPerInsert = opMetrics.createCustomMetric("NumBatchesPerInsert",
                    "Number of batches inserted together", Metric.Kind.GAUGE);
        } catch(Exception e) {
            log.error("NumBatchesPerInsert metric exists.\n" + stringifyStackTrace(e));
            numBatchesPerInsert = opMetrics.getCustomMetric("NumBatchesPerInsert");
        }

        if( failCount > 0 ){
            failures.setValue(failCount);
        }

        if( successCount > 0 ){
            successes.setValue(successCount);
        }

        // Determine if we have the output port related to sending insert rows to it when inserts have failed
        if (context.getNumberOfStreamingOutputs() == 1) {
            hasResultsPort = true;
            resultsOutputPort = getOutput(0);
        }

        //Obtain the configuration inforamtion using the configuration name
        if( cfgObjectName != null && cfgObjectName != "" ){
            cfgMap = context.getPE().getApplicationConfiguration(cfgObjectName);

            log.info("Found config object");

            if( cfgMap.size() > 0 ){
                // Override input parameters
                // Check if we have a eventStoreUser
                if( cfgMap.containsKey("eventStoreUser") ){
                    eventStoreUser = cfgMap.get("eventStoreUser");
                    log.info("Config override eventStoreUser = " + eventStoreUser );
                }

                if( cfgMap.containsKey("eventStorePassword") ){
                    eventStorePassword = cfgMap.get("eventStorePassword");
                    log.info("Config override eventStorePassword = *****" );//+ eventStorePassword );
                }

            }
        }
        log.info("Resulting eventStoreUser = " + eventStoreUser +
                " and passwd = *****"); // + eventStorePassword);

        log.info("The max number of active batches is " + maxNumActiveBatches);

        // Set up connection to the IBM Db2 Event Store engine
        try {
            StreamingInput<Tuple> streamingInput = context.getStreamingInputs().get(0);
            StreamSchema streamSchema = streamingInput.getStreamSchema();

            if (impl == null) {
                if( databaseName == null || databaseName == "" ||
                        tableName == null || tableName == "" ){
                    log.error( "No database or table name was given so we cannot carry out the insert");
                    impl = EventStoreSinkImpl/*EventStoreSinkJImplObject*/.mkWriter(databaseName, tableName,
                            connectionString, frontEndConnectionFlag, streamSchema, nullMapString,
                            eventStoreUser, eventStorePassword,
                            partitioningKey, primaryKey);
                }
                else{
                    log.info( "databaseName= " + databaseName +
                            " tabename= " + tableName );
                    impl = EventStoreSinkImpl/*EventStoreSinkJImplObject*/.mkWriter(databaseName, tableName,
                            connectionString, frontEndConnectionFlag, streamSchema, nullMapString,
                            eventStoreUser, eventStorePassword,
                            partitioningKey, primaryKey);
                }
            }
            // If the batch size if not provided calculate the default batchSize
            if( !batchSizeParmSet ){
                batchSize = impl.calcDefaultBatchSize();
                log.info( "Set default batch size: " + batchSize );
            }

            batch = newBatch();
        } catch(Exception e) {
            log.error( "Failed to connect to IBM Db2 Event Store.\n"+ stringifyStackTrace(e) );
            throw e;
        }
    }

     
    private LinkedList</*Row*/Tuple> newBatch() {
        return new LinkedList</*Row*/Tuple>();
    }

    private Future<Object> activeTimeout;

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
    @SuppressWarnings("incomplete-switch")
    public void processPunctuation(StreamingInput<Tuple> port, Punctuation mark)
          throws Exception {
        // No need to call super.processPunctuation because that just forwards
        // window punctuation marks to the output ports and this operator
        // doesn't support output ports.
        
        // If a final marker comes in then need to flush out any remaining tuples.
        if (mark != Punctuation.FINAL_MARKER)
            return;
        switch (mark) {
        case WINDOW_MARKER:
            break;
        case FINAL_MARKER:
            flushTuples(); 
            break;
        }
    }
    
    /**
     * Process all batched tuples from this port.
     */
    private synchronized void flushTuples() throws Exception {

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
    }
    
    /**
     * Set of the background tasks that have been kicked off
     * for batch processing (calling processBatch()).
     * 
     */
    private final Set<Future<?>> activeBatches = new HashSet<Future<?>>();
    
    /**
     * Test to see if all the batches are complete.
     */
    private synchronized boolean batchesComplete() {
        boolean allComplete = true;
        for (Iterator<Future<?>> i = activeBatches.iterator(); i.hasNext(); )
        {
            Future<?> batch = i.next();
            if (batch.isDone())
                i.remove();
            else
                allComplete = false;
        }
        return allComplete;
    }
    
    /**
     * Wait for any outstanding batch item.
     */
    private void batchWait() {
        Future<?> waitBatch = null;
        
        synchronized (this) {
            for (Iterator<Future<?>> i = activeBatches.iterator(); i.hasNext();) {
                Future<?> batch = i.next();
                if (!batch.isDone())
                {
                    waitBatch = batch;
                    break;
                }
            }
        }
        if (waitBatch != null)
            try {
                waitBatch.get();
            } catch (InterruptedException e) {
                ;
            } catch (ExecutionException e) {
                ;
            }
    }
    

    /**
     * Submit a batch of tuples to be processed by the sub-classes processBatch
     * using a asynchronous task.
     * @param submitter True if this is being called from a task that itself was
     * created by submitBatch.
     */
    private /*synchronized*/ void submitBatch(boolean submitter) throws Exception {
        // We are processing so remove any existing timeout
        /*if (activeTimeout != null) {
            activeTimeout.cancel(false);
            activeTimeout = null;
        }*/
        // Async threads may mean this is called just after
        // another submit batch so if nothing to do, just complete.
        synchronized (this) {
            //boolean allComplete = batchesComplete();

            log.info("The number of activebatches in submitbatch = " + activeBatches.size());

            // Limit the number of outstanding tasks.
            if (/*!submitter && !allComplete &&*/ activeBatches.size() >= 1){ //maxNumActiveBatches) {
                log.error("Inside submitBatch too many batch processes");
                return;
            }
        }

        activeBatches.add(getOperatorContext().getScheduledExecutorService()
                .submit(new Callable<Object>() {
                    public Object call() throws Exception {
                        while(!shutdown ) {
                                LinkedList</*Row*/Tuple> asyncBatch = batchQueue.take();
                                if( asyncBatch == null ){
                                    continue;
                                }

                                log.info("Found a non-empty batch so call insert from submitbatch");
                                //log.info("Before processBatch KB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024);

                                boolean addLeftovers = processBatch(asyncBatch);

                                //log.info("After processBatch KB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024);

                                log.info("In submitBatch call after processbatch with addLeftovers = " + addLeftovers);

                        }
                        return null;
                    }
                }));
    }

    /**
     * Shutdown this operator.
     * @throws Exception Operator failure, will cause the enclosing PE to terminate.
     */
    @Override
    public synchronized void shutdown() throws Exception {
        OperatorContext context = getOperatorContext();
        log.trace("Operator " + context.getName() + " shutting down in PE: " + context.getPE().getPEId() + " in Job: " + context.getPE().getJobId() );

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

    @Parameter(name="databaseName", description = "Name of the EventStore database in order to connect")
    public void setDatabaseName(String s) {databaseName = s;}

    @Parameter(name="tableName", description = "Name of the EventStore table to insert tuples")
    public void setTableName(String s) {tableName = s;}

    private String stringifyStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    /**
     * Set the IBM Db2 Event Store connection string
     * @param connectionString
     *            If null then the code will use a Event Store config file to connect
     */
    @Parameter(name="connectionString", 
            description="Set the IBM Db2 Event Store connection string: <IP>:<port>")
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
            description="Set to true to connect through a Secure Gateway for Event Store Enterprise Edition version >= 1.1.2 and Developer Edition version > 1.1.4")
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
            description="Set the batch size.")
    public synchronized void setBatchSize(int batchSize) throws Exception {
        if (batchSize <= 0)
            throw new IllegalArgumentException(
                    "Sink batchSize must be not be zero or negative.");

        this.batchSize = batchSize;
        batchSizeParmSet = true;

        if( batchSize > maxBatchSize ){
            log.error("You exceeded the maximum batchsize so resetting to max = " + maxBatchSize );
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
                    log.error("Could not add the current batch to the batchQueue");
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
     * as eventStoreUser and eventStorePassword
     * 
     * @param configObject
     */

    @Parameter(name = "configObject", optional=true, 
               description = "Application configuration name")
    public void setConfigObject(String s) { cfgObjectName = s; }

    /**
     * Set the Userid information which will be null or a string
     * 
     * @param eventStoreUser
     */

    @Parameter(name="eventStoreUser", optional=true,
		description = "Name of the IBM Db2 Event Store User in order to connect")
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
		description = "Password for the IBM Db2 Event Store User in order to connect")
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
		description = "Partitioning key for the table")
    public void setPartitioningKey(String s) {partitioningKey = s;}

    /**
     * Set the primary key where if its not in the input or empty
     * or not defined then the default will be no primary key.
     * This is only used when we dynamically have to create the table
     * 
     * @param primaryKey
     */

    @Parameter(name="primaryKey", optional=true,
		description = "Primary key for the table")
    public void setPrimaryKey(String s) {primaryKey = s;}

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
            		log.error("Failed to add inserted field to output");
            	}

            	// Submit new tuple to output port 0
            	resultsOutputPort.submit(outTuple);
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
      log.info("Process a new BATCH ********** Number of tuples = " + batch.size());

      if( batch.size() <= 0 )
          return false;

      // Now put all the tuples into a list and pass to the insertTuple API

      if(impl != null){
          if( numBatchesPerInsert != null) numBatchesPerInsert.setValue((long)1);//numInQueue +1);
          long startTime = 0;
          long endTime = 0;
          try{
              startTime = System.currentTimeMillis();

              impl.insertTuple(batch); 

              endTime = System.currentTimeMillis() - startTime;
              log.info( "Insert (success time) = " + endTime );
              log.info("*** SUCCESSFUL INSERT");
              if(successes != null) successes.increment();
              if(insertGaugeTime != null) insertGaugeTime.setValue(endTime);
              submitResultTuple(batch, true);           
              batch.clear();
          } catch(Exception e) {
              endTime = System.currentTimeMillis() - startTime;
              log.error( "Insert (failure time) = " + endTime );
              if(failures != null) failures.increment();
              log.error("Failed to write tuple to EventStore.\n"+ stringifyStackTrace(e) );
              submitResultTuple(batch, false);
              if(crContext != null) {
            	  // In a consitent region just throw the error so we can restart gracefully
                  log.error("Failed to write tuple to EventStore so now THROW exception in processBatch");
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
}

