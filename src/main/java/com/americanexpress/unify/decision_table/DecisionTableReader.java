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
import com.americanexpress.unify.jdocs.Document;
import com.americanexpress.unify.jdocs.JDocument;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class DecisionTableReader {

  protected String version = null;
  protected String name = null;
  protected MatchPolicy mp = null;
  protected NoMatchPolicy nmp = null;
  protected RowValidationPolicy rvp = null;
  protected List<DTColumn> cols = null;
  protected List<DTRow> rows = null;

  public abstract DecisionTable loadDecisionTableFromResourcePath(String resourcePath);

  public abstract DecisionTable loadDecisionTableFromFilePath(String filePath);

  protected final void loadDocumentModel() {
    if (JDocument.isDocumentModelLoaded("decision_table") == false) {
      String json = BaseUtils.getResourceAsString(JsonReader.class, "/com/americanexpress/unify/decision_table/decision_table.json");
      JDocument.loadDocumentModel("decision_table", json);
    }
  }

  private Map<String, DTCell> getCellsMap(List<DTCell> evalCells) {
    Map<String, DTCell> map = new HashMap<>();
    for (DTCell cell : evalCells) {
      map.put(cell.getColumnName(), cell);
    }
    return map;
  }

  public final String getJson() {
    loadDocumentModel();
    Document d = new JDocument("decision_table", null);

    d.setString("decision_table$.decision_table.version", version);
    d.setString("decision_table$.decision_table.match_policy", mp.toString().toLowerCase());
    d.setString("decision_table$.decision_table.no_match_policy", nmp.toString().toLowerCase());
    if (rvp != null) {
      d.setString("decision_table$.decision_table.row_validation_policy", rvp.toString().toLowerCase());
    }

    // write the cols
    for (int i = 0; i < cols.size(); i++) {
      DTColumn dtCol = cols.get(i);
      d.setString("decision_table$.decision_table.cols[%].name", dtCol.getName(), i + "");
      d.setString("decision_table$.decision_table.cols[%].type", dtCol.getColumnType().toString().toLowerCase(), i + "");
      d.setString("decision_table$.decision_table.cols[%].data_type", dtCol.getDataType().toString().toLowerCase(), i + "");
    }

    // write rows
    for (int i = 0; i < (rows.size() - 1); i++) {
      DTRow row = rows.get(i);

      Map<String, DTCell> evalCellsMap = getCellsMap(row.getEvalDTCellsList());
      Map<String, DTCell> retCellsMap = getCellsMap(row.getRetDTCellsAsList());

      for (int j = 0; j < cols.size(); j++) {
        String colName = cols.get(j).getName();
        DTCell cell = evalCellsMap.get(colName);
        if (cell == null) {
          cell = retCellsMap.get(colName);
        }
        String s = null;
        if (cell != null) {
          if (cols.get(j).getColumnType() == ColumnType.EVALUATE) {
            s = cell.getOprType().getOperatorString() + " " + cell.getValue();
            if (s != null && s.isEmpty() == true) {
              s = null;
            }

          }
          else {
            s = cell.getValue();
          }
        }
        d.setString("decision_table$.decision_table.rows[%].cols[%].name", colName, i + "", j + "");
        d.setString("decision_table$.decision_table.rows[%].cols[%].value", s, i + "", j + "");
      }
    }

    // the last row is either the default row or an empty row
    DTRow row = rows.get(rows.size() - 1);

    if (row.getRetDTCells().size() > 0) {
      // it is a default row
      // write the rule id and the comments columns if found
      int i = 0;
      DTCell ruleIdCell = row.getRuleIdCell();
      if (ruleIdCell != null) {
        d.setString("decision_table$.decision_table.default_row[%].name", ruleIdCell.getColumnName(), i + "");
        d.setString("decision_table$.decision_table.default_row[%].value", ruleIdCell.getValue(), i + "");
        i++;
      }

      DTCell commentsCell = row.getCommentsCell();
      if (commentsCell != null) {
        d.setString("decision_table$.decision_table.default_row[%].name", commentsCell.getColumnName(), i + "");
        d.setString("decision_table$.decision_table.default_row[%].value", commentsCell.getValue(), i + "");
        i++;
      }

      List<DTCell> retCells = row.getRetDTCellsAsList();
      for (DTCell cell : retCells) {
        d.setString("decision_table$.decision_table.default_row[%].name", cell.getColumnName(), i + "");
        d.setString("decision_table$.decision_table.default_row[%].value", cell.getValue(), i + "");
        i++;
      }
    }
    else {
      // it is an empty row and so nothing to be done
    }

    return d.getPrettyPrintJson();
  }

}
