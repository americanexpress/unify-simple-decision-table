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

/*
 * @author Deepak Arora
 */
final class DTCell {

  private String columnName = null;
  private String value = null;
  private OperatorType oprType = null;
  private ColumnType columnType = null;

  public DTCell(String columnName, ColumnType columnType, String value, OperatorType oprType) {
    this.columnName = columnName;
    this.columnType = columnType;
    this.value = value;
    this.oprType = oprType;
  }

  public String getColumnName() {
    return columnName;
  }

  public String getValue() {
    return value;
  }

  public OperatorType getOprType() {
    return oprType;
  }

  public ColumnType getColumnType() {
    return columnType;
  }

}
