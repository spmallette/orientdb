/*
 * Copyright 2010-2012 Luca Garulli (l.garulli--at--orientechnologies.com)
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
package com.orientechnologies.orient.core.sql.functions.math;

import com.orientechnologies.common.collection.OMultiValue;
import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.metadata.schema.OType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compute the average value for a field. Uses the context to save the last average number. When different Number class are used,
 * take the class with most precision.
 * 
 * @author Luca Garulli (l.garulli--at--orientechnologies.com)
 * 
 */
public class OSQLFunctionAverage extends OSQLFunctionMathAbstract {
  public static final String NAME  = "avg";

  private Number             sum;
  private int                total = 0;

  public OSQLFunctionAverage() {
    super(NAME, 1, -1);
  }

  public Object execute(Object iThis, OIdentifiable iCurrentRecord, Object iCurrentResult, final Object[] iParams,
      OCommandContext iContext) {
    if (iParams.length == 1) {
      if (iParams[0] instanceof Number)
        sum((Number) iParams[0]);
      else if (OMultiValue.isMultiValue(iParams[0]))
        for (Object n : OMultiValue.getMultiValueIterable(iParams[0]))
          sum((Number) n);

    } else {
      sum = null;
      for (int i = 0; i < iParams.length; ++i)
        sum((Number) iParams[i]);
    }

    return getResult();
  }

  protected void sum(Number value) {
    if (value != null) {
      total++;
      if (sum == null)
        // FIRST TIME
        sum = value;
      else
        sum = OType.increment(sum, value);
    }
  }

  public String getSyntax() {
    return "avg(<field> [,<field>*])";
  }

  @Override
  public Object getResult() {
    if (returnDistributedResult()) {
      final Map<String, Object> doc = new HashMap<String, Object>();
      doc.put("sum", sum);
      doc.put("total", total);
      return doc;
    } else {
      if (sum instanceof Integer)
        return sum.intValue() / total;
      else if (sum instanceof Long)
        return sum.longValue() / total;
      else if (sum instanceof Float)
        return sum.floatValue() / total;
      else if (sum instanceof Double)
        return sum.doubleValue() / total;
      else if (sum instanceof BigDecimal)
        return ((BigDecimal) sum).divide(new BigDecimal(total));
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object mergeDistributedResult(List<Object> resultsToMerge) {
    Number sum = null;
    int total = 0;
    for (Object iParameter : resultsToMerge) {
      final Map<String, Object> item = (Map<String, Object>) iParameter;
      if (sum == null)
        sum = (Number) item.get("sum");
      else
        sum = OType.increment(sum, (Number) item.get("sum"));

      total += (Integer) item.get("total");
    }

    if (sum instanceof Integer)
      return sum.intValue() / total;
    else if (sum instanceof Long)
      return sum.longValue() / total;
    else if (sum instanceof Float)
      return sum.floatValue() / total;
    else if (sum instanceof Double)
      return sum.doubleValue() / total;
    else if (sum instanceof BigDecimal)
      return ((BigDecimal) sum).divide(new BigDecimal(total), RoundingMode.HALF_UP);

    return null;
  }

  @Override
  public boolean aggregateResults() {
    return configuredParameters.length == 1;
  }
}
