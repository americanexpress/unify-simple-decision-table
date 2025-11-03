/*
 * Copyright 2025 American Express Travel Related Services Company, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.americanexpress.unify.decision_table;

import java.math.BigDecimal;

/*
 * @author Deepak Arora
 */
public final class RetDTCell {

  private String columnName = null;
  private Object value = null;
  private DataType dataType = null;

  public RetDTCell(String columnName, DataType dataType, Object value) {
    this.columnName = columnName;
    this.dataType = dataType;
    this.value = value;
  }

  public String getColumnName() {
    return columnName;
  }

  public String getString() {
    return (String)value;
  }

  public Integer getInteger() {
    return (Integer)value;
  }

  public Long getLong() {
    return (Long)value;
  }

  public Double getDouble() {
    return (Double)value;
  }

  public Boolean getBoolean() {
    return (Boolean)value;
  }

  public DataType getDataType() {
    return dataType;
  }

  public BigDecimal getBigDecimal() {
    return (BigDecimal)value;
  }

  public Object getValue() {
    return value;
  }

}
