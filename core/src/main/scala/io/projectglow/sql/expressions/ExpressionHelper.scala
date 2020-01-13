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

package io.projectglow.sql.expressions

import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateFunction

import io.projectglow.sql.util.Rewrite

object ExpressionHelper {
  def wrapAggregate(e: Expression): Expression = e match {
    case agg: AggregateFunction => agg.toAggregateExpression()
    case expr => expr
  }

  def rewrite(e: Expression): Expression = e match {
    case r: Rewrite => r.rewrite
    case expr => expr
  }
}
