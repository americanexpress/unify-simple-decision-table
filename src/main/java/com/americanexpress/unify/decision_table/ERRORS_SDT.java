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

import com.americanexpress.unify.base.ErrorMap;

import java.util.Map;

/*
 * @author Deepak Arora
 */
public class ERRORS_SDT extends ErrorMap {

  public static void load() {
    Map<String, String> map = errors;

    // decision table errors
    map.put("sdt_err_20", "IOException while trying to read excel file -> {0}");
    map.put("sdt_err_21", "Invalid first row in file -> {0}. A0 cell does not contain version as text");
    map.put("sdt_err_22", "Invalid third row in file -> {0}. A2 cell does not contain match policy as text");
    map.put("sdt_err_23", "Invalid fourth row in file -> {0}. A3 cell does not contain no match policy as text");
    map.put("sdt_err_24", "Could not find first column marker in cell B6 in file -> {0}");
    map.put("sdt_err_25", "Invalid value found in sixth row in file -> {0}. Value expected is Last Column");
    map.put("sdt_err_26", "Last Column marker not found on sixth row in file -> {0}");
    map.put("sdt_err_27", "Column name cannot be empty in file -> {0}. Row index -> {1}, Column index -> {2}");
    map.put("sdt_err_28", "Column type cannot be empty in file -> {0}. Row index -> {1}, Column index -> {2}");
    map.put("sdt_err_29", "Column data type cannot be empty in file -> {0}. Row index -> {1}, Column index -> {2}");
    map.put("sdt_err_30", "Invalid data type encountered in column definition -> {0} in file -> {1}");
    map.put("sdt_err_31", "First row marker not found in A10 in file -> {0}");
    map.put("sdt_err_32", "Last / Default row marker not found in 10000 rows in file -> {0}");
    map.put("sdt_err_33", "Invalid operator type encountered -> {0} in file {1} in value {2}");
    map.put("sdt_err_35", "Invalid column type -> {0} in file -> {1}");
    map.put("sdt_err_36", "Duplicate rule id value -> {0} at row -> {1} in file -> {2}");
    map.put("sdt_err_37", "More than one rule_id column is not allowed, file -> {0}");
    map.put("sdt_err_38", "More than one comments column is not allowed, file -> {0}");
    map.put("sdt_err_39", "Invalid data type for rule id or comments column. Only String data type is allowed, file -> {0}");
    map.put("sdt_err_40", "Duplicate column name -> {0}, file -> {1}");
    map.put("sdt_err_41", "Number of columns in row does not match the number of columns defined. Row index -> {0}, file -> {1}");
    map.put("sdt_err_42", "Evaluate columns cannot be defined in the default row, file -> {0}");
    map.put("sdt_err_43", "Default row does not contain all return, rule_id or comments columns, file -> {0}");
    map.put("sdt_err_44", "Decision tables with no match policy of return none cannot contain a default row, file -> {0}");
    map.put("sdt_err_45", "Unknown column in default row, col name -> {0}, file -> {1}");
    map.put("sdt_err_46", "A decision table has to have at least one row and one default row for return default policy and at least one row for return none policy, file -> {0}");
    map.put("sdt_err_47", "Unknown column in row index -> {0}, col name -> {1}, file -> {2}");
    map.put("sdt_err_48", "Row does not contain all columns defined, row index -> {0}, file -> {1}");
    map.put("sdt_err_49", "Row does not contain all return, rule_id or comments columns, row index -> {0}, file -> {1}");
    map.put("sdt_err_50", "JDocument is not initialized. Please initialize the underlying JDocument library before initializing the decision table");
  }

}
