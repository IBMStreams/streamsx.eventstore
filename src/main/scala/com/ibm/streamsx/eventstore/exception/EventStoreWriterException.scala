package com.ibm.streamsx.eventstore.exception

import java.lang.Throwable

/**
  * Created by zilio on 2016-11-16.
  */
case class EventStoreWriterException(m: String, ex: Throwable = new Exception) extends Exception(m,ex)
