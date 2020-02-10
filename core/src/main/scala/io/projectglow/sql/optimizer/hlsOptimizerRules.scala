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

package io.projectglow.sql.optimizer

import org.apache.spark.sql.catalyst.analysis.UnresolvedAlias
import org.apache.spark.sql.catalyst.expressions.{Alias, Expression}
import org.apache.spark.sql.catalyst.plans.logical.{LogicalPlan, Project}
import org.apache.spark.sql.catalyst.rules.Rule

import io.projectglow.common.GlowLogging
import io.projectglow.sql.expressions._
import io.projectglow.sql.util.RewriteAfterResolution

/**
 * Simple optimization rule that handles expression rewrites
 */
object ReplaceExpressionsRule extends Rule[LogicalPlan] with GlowLogging {
  override def apply(plan: LogicalPlan): LogicalPlan = {
    logger.info(s"Trying to rewrite expressions in ${plan}")
    plan.transformAllExpressions {
      case expr: RewriteAfterResolution =>
        logger.info(s"Rewriting $expr")
        ExpressionHelper.wrapAggregate(expr.rewrite)
      case expr =>
        logger.info(s"Not rewriting $expr")
        expr
    }
  }
}

/**
 * This rule is needed by [[AggregateByIndex]].
 *
 * Spark's analyzer only wraps AggregateFunctions in AggregateExpressions immediately after
 * resolution. Since [[AggregateByIndex]] is first resolved as a higher order function, it is
 * not correctly wrapped. Note that it's merely a coincidence that it is first resolved as a higher
 * order function.
 */
object ResolveAggregateFunctionsRule extends Rule[LogicalPlan] {
  override def apply(plan: LogicalPlan): LogicalPlan = plan.transformExpressions {
    case agg: UnwrappedAggregateFunction =>
      ExpressionHelper.wrapAggregate(agg.asWrapped)
  }
}

object ResolveExpandStructRule extends Rule[LogicalPlan] with GlowLogging {
  override def apply(plan: LogicalPlan): LogicalPlan = {
    logger.info(plan.toString())
    plan.resolveOperatorsUp {
      case Project(projectList, child) if canExpand(projectList) =>
        val expandedList = projectList.flatMap {
          case UnresolvedAlias(e: ExpandStruct, _) => e.expand()
          case Alias(e: ExpandStruct, _) => e.expand()
          case e: ExpandStruct => e.expand()
          case e => Seq(e)
        }
        Project(expandedList, child)
    }
  }

  private def canExpand(projectList: Seq[Expression]): Boolean = projectList.exists {
    case e: ExpandStruct => e.childrenResolved
    case UnresolvedAlias(e: ExpandStruct, _) => e.childrenResolved
    case Alias(e: ExpandStruct, _) => e.childrenResolved
    case _ => false
  }
}
