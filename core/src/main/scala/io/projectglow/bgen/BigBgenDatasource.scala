/*
 * Copyright 2019 The Glow Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.projectglow.bgen

import java.io.ByteArrayOutputStream

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.sources.DataSourceRegister

import io.projectglow.common.logging.{HlsMetricDefinitions, HlsTagDefinitions, HlsTagValues, HlsUsageLogging}
import io.projectglow.sql.BigFileDatasource
import io.projectglow.sql.util.ComDatabricksDataSource

class BigBgenDatasource extends BigFileDatasource with DataSourceRegister with HlsUsageLogging {

  override def shortName(): String = "bigbgen"

  override def serializeDataFrame(options: Map[String, String], data: DataFrame): RDD[Array[Byte]] =
    BigBgenDatasource.serializeDataFrame(options, data)

}

class ComDatabricksBigBgenDatasource extends BigBgenDatasource with ComDatabricksDataSource

object BigBgenDatasource extends HlsUsageLogging {

  val BITS_PER_PROB_KEY = "bitsPerProbability"
  val BITS_PER_PROB_DEFAULT_VALUE = "16"

  val MAX_PLOIDY_KEY = "maximumInferredPloidy"
  val MAX_PLOIDY_VALUE = "10"

  val DEFAULT_PLOIDY_KEY = "defaultInferredPloidy"
  val DEFAULT_PLOIDY_VALUE = "2"

  val DEFAULT_PHASING_KEY = "defaultInferredPhasing"
  val DEFAULT_PHASING_VALUE = "false"

  def serializeDataFrame(options: Map[String, String], data: DataFrame): RDD[Array[Byte]] = {

    val dSchema = data.schema
    val numVariants = data.count
    val bitsPerProb = options.getOrElse(BITS_PER_PROB_KEY, BITS_PER_PROB_DEFAULT_VALUE).toInt
    val maxPloidy = options.getOrElse(MAX_PLOIDY_KEY, MAX_PLOIDY_VALUE).toInt
    val defaultPloidy = options.getOrElse(DEFAULT_PLOIDY_KEY, DEFAULT_PLOIDY_VALUE).toInt
    val defaultPhasing = options.getOrElse(DEFAULT_PHASING_KEY, DEFAULT_PHASING_VALUE).toBoolean

    // record bgenWrite event in the log
    val logOptions = Map(
      BITS_PER_PROB_KEY -> bitsPerProb,
      MAX_PLOIDY_KEY -> maxPloidy,
      DEFAULT_PLOIDY_KEY -> defaultPloidy,
      DEFAULT_PHASING_KEY -> defaultPhasing
    )
    recordHlsUsage(
      HlsMetricDefinitions.EVENT_HLS_USAGE,
      Map(
        HlsTagDefinitions.TAG_EVENT_TYPE -> HlsTagValues.EVENT_BGEN_WRITE
      ),
      blob = hlsJsonBuilder(logOptions)
    )

    data.queryExecution.toRdd.mapPartitionsWithIndex {
      case (idx, it) =>
        val baos = new ByteArrayOutputStream()

        val writeHeader = idx == 0
        val writer = new BgenRecordWriter(
          baos,
          dSchema,
          writeHeader,
          numVariants,
          bitsPerProb,
          maxPloidy,
          defaultPloidy,
          defaultPhasing
        )

        it.foreach { row =>
          writer.write(row)
        }

        writer.close()
        Iterator(baos.toByteArray)
    }
  }
}
