/*******************************************************************************
 * Copyright (c) 2016 Logimethods
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the MIT License (MIT)
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/MIT
 *******************************************************************************/

package com.logimethods.nats.connector.spark.app

import java.util.Properties;
import java.io.File
import java.io.Serializable

import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.streaming._
import com.datastax.spark.connector.streaming._
import com.datastax.spark.connector.SomeColumns

//import io.nats.client.Constants._
import io.nats.client.ConnectionFactory._
import java.nio.ByteBuffer

import org.apache.log4j.{Level, LogManager, PropertyConfigurator}

import com.logimethods.connector.nats.to_spark._
import com.logimethods.scala.connector.spark.to_nats._

import java.util.function._

import java.time.{LocalDateTime, ZoneOffset}

object SparkMaxProcessor extends App with SparkStreamingProcessor {
  val log = LogManager.getRootLogger
  log.setLevel(Level.WARN)
  
  val (properties, logLevel, sc, ssc, inputNatsStreaming, inputSubject, outputSubject, clusterId, outputNatsStreaming, natsUrl) = setupStreaming(args)
//  ssc.checkpoint("/spark/storage")
  
  def dataDecoder: Array[Byte] => Tuple2[Long,Float] = bytes => {
        val buffer = ByteBuffer.wrap(bytes);
        val epoch = buffer.getLong()
        val voltage = buffer.getFloat()
        (epoch, voltage)  
      }
  
  val messages =
    if (inputNatsStreaming) {
      NatsToSparkConnector
        .receiveFromNatsStreaming(classOf[Tuple2[Long,Float]], StorageLevel.MEMORY_ONLY, clusterId)
        .withNatsURL(natsUrl)
        .withSubjects(inputSubject)
        .withDataDecoder(dataDecoder)
        .asStreamOfKeyValue(ssc)
    } else {
      NatsToSparkConnector
        .receiveFromNats(classOf[Tuple2[Long,Float]], StorageLevel.MEMORY_ONLY)
        .withProperties(properties)
        .withSubjects(inputSubject)
        .withDataDecoder(dataDecoder)
        .asStreamOfKeyValue(ssc)
    }

  if (logLevel.equals("MESSAGES")) {
    messages.print()
  }
  
  // MAXIMUM values
  
  val voltages = messages.filter({case (s, v) => s.startsWith("smartmeter.voltage.raw.data")})  
  /** (subject, (epoch, voltage)) **/
  val max_voltage = voltages.map({case (subject, (epoch, voltage)) => (voltage) })
                            .reduce(Math.max(_, _))
                            .map({case (voltage) => (s"""{"voltage": $voltage}""") })

  if (logLevel.equals("MAX")) {
    max_voltage.print()
  }
          
  SparkToNatsConnectorPool.newPool()
                          .withProperties(properties)
                          .withSubjects(outputSubject)
                          .publishToNats(max_voltage)
  
  // Start
  ssc.start();		
  
  ssc.awaitTermination()
}