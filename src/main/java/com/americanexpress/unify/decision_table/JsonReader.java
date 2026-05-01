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

import com.americanexpress.unify.base.BaseUtils;
import com.americanexpress.unify.base.UnifyException;
import com.americanexpress.unify.jdocs.Document;
import com.americanexpress.unify.jdocs.JDocument;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/*
 * @author Deepak Arora
 */
class JsonReader extends DecisionTableReader {

  private Map<String, DTColumn> colsMap = new HashMap<>();

  public DecisionTable loadDecisionTableFromFilePath(String filePath) {
    String json = null;
    try {
      InputStream is = new BufferedInputStream(new FileInputStream(filePath));
      json = BaseUtils.getStringFromStream(is);
      is.close();
    }
    catch (IOException e) {
      throw new UnifyException("sdt_err_20", e, filePath);
    }

    return getDecisionTableFromJson(filePath, json);
  }

  public DecisionTable loadDecisionTableFromResourcePath(String resourcePath) {
    String json = BaseUtils.getResourceAsString(JsonReader.class, resourcePath);
    return getDecisionTableFromJson(resourcePath, json);
  }

  public DecisionTable getDecisionTableFromJson(String name, String json) {
    loadDocumentModel();
    Document d = new JDocument("decision_table", json);

    this.name = name;
    version = d.getString("decision_table$.decision_table.version");
    mp = getMatchPolicy(d);
    nmp = getNoMatchPolicy(d);
    rvp = getRowValidationPolicy(d);
    cols = getColumns(d);
    rows = getRows(d);

    return getDecisionTable();
  }

  private DecisionTable getDecisionTable() {
    DecisionTable dt = new DecisionTable(name, mp, nmp);

    for (DTColumn col : cols) {
      switch (col.getColumnType()) {
        case EVALUATE:
          dt.addEvalColumn(col);
          break;

        case RETURN:
          dt.addRetColumn(col);
          break;

        case COMMENTS:
        case RULE_ID:
          // nothing to do
          break;

        default:
          // this will never be reached
          break;
      }
    }

    // add rows
    dt.addRows(rows);

    dt.colList = cols;

    return dt;
  }

  private MatchPolicy getMatchPolicy(Document d) {
    String val = d.getString("decision_table$.decision_table.match_policy");
    return MatchPolicy.valueOf(val.toUpperCase());
  }

  private NoMatchPolicy getNoMatchPolicy(Document d) {
    String val = d.getString("decision_table$.decision_table.no_match_policy");
    return NoMatchPolicy.valueOf(val.toUpperCase());
  }

  private RowValidationPolicy getRowValidationPolicy(Document d) {
    RowValidationPolicy policy = null;
    String val = d.getString("decision_table$.decision_table.row_validation_policy");
    if (val != null) {
      policy = RowValidationPolicy.valueOf(val.toUpperCase());
    }
    return policy;
  }

  private ArrayList<DTColumn> getColumns(Document d) {
    ArrayList<DTColumn> cols = new ArrayList<>();
    int size = d.getArraySize("decision_table$.decision_table.cols[]");
    boolean doesRuleIdColExist = false;
    boolean doesCommentsColExist = false;
    Set<String> colNames = new HashSet<>();

    for (int i = 0; i < size; i++) {
      String name = d.getString("decision_table$.decision_table.cols[%].name", i + "");

      // duplicate name validation
      if (colNames.contains(name) == true) {
        throw new UnifyException("sdt_err_40", name, super.name);
      }
      colNames.add(name);

      String type = d.getString("decision_table$.decision_table.cols[%].type", i + "");
      String dataType = d.getString("decision_table$.decision_table.cols[%].data_type", i + "");
      ColumnType colType = ColumnType.valueOf(type.toUpperCase());

      // rule id col validation
      if ((colType == ColumnType.RULE_ID) && (doesRuleIdColExist == true)) {
        throw new UnifyException("sdt_err_37", super.name);
      }
      doesRuleIdColExist = true;

      // comments col validation
      if ((colType == ColumnType.COMMENTS) && (doesCommentsColExist == true)) {
        throw new UnifyException("sdt_err_38", super.name);
      }
      doesCommentsColExist = true;

      DataType edt = DataType.valueOf(dataType.toUpperCase());

      // data type validation
      if (((colType == ColumnType.RULE_ID) || (colType == ColumnType.COMMENTS)) && edt != DataType.STRING) {
        throw new UnifyException("sdt_err_39", super.name);
      }

      DTColumn dtCol = new DTColumn(name, colType, edt);
      cols.add(dtCol);
      colsMap.put(name, dtCol);
    }

    return cols;
  }

  private ArrayList<DTRow> getRows(Document d) {
    RowValidationPolicy rvp = DecisionTable.getDefaultRowValidationPolicy();
    if (super.rvp != null) {
      rvp = super.rvp;
    }

    ArrayList<DTRow> rows = new ArrayList<>();
    int numRows = d.getArraySize("decision_table$.decision_table.rows[]");
    Set<String> ruleIdSet = new HashSet<>();

    for (int i = 0; i < numRows; i++) {
      DTRow row = new DTRow();
      int numCols = d.getArraySize("decision_table$.decision_table.rows[%].cols[]", i + "");

      // validate that number of cols should be equal to number of defined cols
      if (rvp == RowValidationPolicy.STRICT && numCols != cols.size()) {
        throw new UnifyException("sdt_err_41", i + "", super.name);
      }

      // get all cols in a set
      Set<String> colsSet = new HashSet<>();
      colsSet.addAll(colsMap.keySet());

      Set<String> nonEvalColsSet = getNonEvalColsSet();

      // for each column defined in the row
      for (int j = 0; j < numCols; j++) {
        String name = d.getString("decision_table$.decision_table.rows[%].cols[%].name", i + "", j + "");
        String value = d.getString("decision_table$.decision_table.rows[%].cols[%].value", i + "", j + "");
        OperatorType oprType = null;

        DTColumn dtCol = colsMap.get(name);

        if (dtCol == null) {
          // column not found
          throw new UnifyException("sdt_err_47", i + "", name, super.name);
        }

        colsSet.remove(name);

        ColumnType colType = dtCol.getColumnType();
        switch (colType) {
          case EVALUATE:
            if ((value != null) && (value.isEmpty() == true)) {
              // treat it like a null - null values will not be added to the row as
              // we don't want them to be evaluated
              value = null;
            }

            if (value != null) {
              int delimIndex = value.indexOf(' ');
              String opr = value.substring(0, delimIndex);
              oprType = OperatorType.from(opr);
              if (oprType == null) {
                throw new UnifyException("sdt_err_33", opr, super.name, value);
              }
              value = value.substring(delimIndex + 1);
              value = DecisionTable.dedupeValue(value, oprType);
            }
            break;

          case RETURN:
          case COMMENTS:
            // here we are retaining the columns even if null to maintain compatibility with excel based
            // tables
            if (value == null) {
              value = "";
            }
            nonEvalColsSet.remove(name);
            break;

          case RULE_ID:
            if ((value == null) || (value.isEmpty() == true)) {
              // set the value to be the row number -> note it is 1 index based and not 0 index based
              value = (i + 1) + "";
            }

            // check that it is unique
            if (ruleIdSet.contains(value) == true) {
              throw new UnifyException("sdt_err_36", value, i + "", super.name);
            }
            ruleIdSet.add(value);
            nonEvalColsSet.remove(name);

            break;

          default:
            throw new UnifyException("sdt_err_35", name, super.name);
        }

        if (value != null) {
          DTCell cell = new DTCell(name, colType, value, oprType);
          row.addCell(cell);
        }
      }

      if (nonEvalColsSet.isEmpty() == false) {
        // not all return columns are defined in the row
        throw new UnifyException("sdt_err_49", i + "", super.name);
      }

      if (rvp == RowValidationPolicy.STRICT && (colsSet.isEmpty() == false)) {
        // not all columns are defined in the row
        throw new UnifyException("sdt_err_48", i + "", super.name);
      }

      rows.add(row);
    }

    rows.add(getDefaultRow(d, ruleIdSet, numRows));

    // a decision table should always have at least 2 rows in case of return default and 1 row in case of return none
    if (rows.size() < 2) {
      throw new UnifyException("sdt_err_46", super.name);
    }

    return rows;
  }

  private DTRow getDefaultRow(Document d, Set<String> ruleIdSet, int rowNum) {
    // we add an empty row if it is not defined or the default row if it is defined
    DTRow row = new DTRow();

    // check that we should not have a default row with return none policy
    if ((nmp == NoMatchPolicy.RETURN_NONE) && (d.pathExists("decision_table$.decision_table.default_row") == true)) {
      throw new UnifyException("sdt_err_44", super.name);
    }

    // if return none, return an empty row
    if (nmp == NoMatchPolicy.RETURN_NONE) {
      return row;
    }

    Set<String> colsSet = getNonEvalColsSet();

    // we have the return default policy
    int numCols = d.getArraySize("decision_table$.decision_table.default_row[]");
    for (int i = 0; i < numCols; i++) {
      String name = d.getString("decision_table$.decision_table.default_row[%].name", i + "");
      String value = d.getString("decision_table$.decision_table.default_row[%].value", i + "");

      DTColumn dtCol = colsMap.get(name);

      // column is not defined
      if (dtCol == null) {
        throw new UnifyException("sdt_err_45", name, super.name);
      }

      colsSet.remove(name);

      ColumnType colType = dtCol.getColumnType();
      switch (colType) {
        case EVALUATE:
          // we cannot have evaluate columns in the default row
          throw new UnifyException("sdt_err_42", super.name);

        case RETURN:
        case COMMENTS:
          if (value == null) {
            value = "";
          }
          break;

        case RULE_ID:
          if ((value == null) || (value.isEmpty() == true)) {
            // set the value to be the row number
            value = rowNum + "";
          }

          // check that it is unique
          if (ruleIdSet.contains(value) == true) {
            throw new UnifyException("sdt_err_36", value, rowNum + "", super.name);
          }
          ruleIdSet.add(value);

          break;

        default:
          throw new UnifyException("sdt_err_35", name, super.name);
      }

      DTCell cell = new DTCell(name, colType, value, null);
      row.addCell(cell);
    }

    if (colsSet.isEmpty() == false) {
      // not all columns are defined in the row
      throw new UnifyException("sdt_err_43", super.name);
    }

    return row;
  }

  private Set<String> getNonEvalColsSet() {
    Set<String> colsSet = new HashSet<>();
    for (DTColumn col : cols) {
      if (col.getColumnType() != ColumnType.EVALUATE) {
        colsSet.add(col.getName());
      }
    }
    return colsSet;
  }

}
