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

import com.americanexpress.unify.base.UnifyException;
import org.apache.poi.ss.usermodel.*;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/*
 * @author Deepak Arora
 */
class ExcelReader extends DecisionTableReader {

  private int firstColIndex = 1;
  private int lastColIndex = 1;
  private DataFormatter df = new DataFormatter();

  private static final int VERSION_ROW_INDEX = 0;
  private static final int MATCH_POLICY_ROW_INDEX = 1;
  private static final int NO_MATCH_POLICY_ROW_INDEX = 2;
  private static final int COL_MARKERS_ROW_INDEX = 4;
  private static final int COL_OFFSET = 1;

  public final DecisionTable loadDecisionTableFromFilePath(String filePath) {
    name = filePath;
    DecisionTable dt = null;
    try {
      InputStream is = new BufferedInputStream(new FileInputStream(filePath));
      dt = getDecisionTable(is);
      is.close();
    }
    catch (IOException e) {
      throw new UnifyException("sdt_err_20", e, filePath);
    }
    return dt;
  }

  public final DecisionTable loadDecisionTableFromResourcePath(String resourcePath) {
    name = resourcePath;
    InputStream is = ExcelReader.class.getResourceAsStream(resourcePath);
    DecisionTable dt = getDecisionTable(is);
    try {
      is.close();
    }
    catch (IOException e) {
      throw new UnifyException("sdt_err_20", e, resourcePath);
    }
    return dt;
  }

  private DecisionTable getDecisionTable(InputStream is) {
    DecisionTable dt = null;

    Workbook wb = null;
    try {
      wb = WorkbookFactory.create(is);
      Sheet sheet = wb.getSheet("Decision Table");

      version = getVersion(sheet);
      mp = getMatchPolicy(sheet);
      nmp = getNoMatchPolicy(sheet);
      cols = getColumns(sheet);
      rows = getRows(sheet);

      dt = new DecisionTable(name, mp, nmp);

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
    }
    catch (IOException e) {
      throw new UnifyException("sdt_err_20", e, name);
    }
    finally {
      if (wb != null) {
        try {
          wb.close();
        }
        catch (IOException e) {
          // do nothing
        }
      }
    }
    return dt;
  }

  private String getVersion(Sheet sheet) {
    Row row = sheet.getRow(VERSION_ROW_INDEX);

    Cell c = null;
    String s = null;

    c = row.getCell(0);
    s = df.formatCellValue(c);
    if (s.equalsIgnoreCase("Version") == false) {
      throw new UnifyException("sdt_err_21", name);
    }

    c = row.getCell(1);
    s = df.formatCellValue(c);
    return s;
  }

  private MatchPolicy getMatchPolicy(Sheet sheet) {
    Row row = sheet.getRow(MATCH_POLICY_ROW_INDEX);

    Cell c = null;
    String s = null;

    c = row.getCell(0);
    s = df.formatCellValue(c);
    if (s.equalsIgnoreCase("Match Policy") == false) {
      throw new UnifyException("sdt_err_22", name);
    }

    c = row.getCell(1);
    s = df.formatCellValue(c);

    return MatchPolicy.valueOf(s.toUpperCase());
  }

  private NoMatchPolicy getNoMatchPolicy(Sheet sheet) {
    Row row = sheet.getRow(NO_MATCH_POLICY_ROW_INDEX);

    Cell c = null;
    String s = null;

    c = row.getCell(0);
    s = df.formatCellValue(c);
    if (s.equalsIgnoreCase("No Match Policy") == false) {
      throw new UnifyException("sdt_err_23", name);
    }

    c = row.getCell(1);
    s = df.formatCellValue(c);

    return NoMatchPolicy.valueOf(s.toUpperCase());
  }

  private ArrayList<DTColumn> getColumns(Sheet sheet) {
    Row row = sheet.getRow(COL_MARKERS_ROW_INDEX);

    Cell c = null;
    String s = null;

    // check first column marker
    c = row.getCell(COL_OFFSET);
    s = df.formatCellValue(c);
    if (s.equalsIgnoreCase("First Column") == false) {
      throw new UnifyException("sdt_err_24", name);
    }

    // get the last column marker. Arbitrarily using a max of 1000 cols possible
    {
      boolean found = false;
      int i;
      for (i = COL_OFFSET + 1; i < 1002; i++) {
        c = row.getCell(i);
        s = df.formatCellValue(c);
        if (s.isEmpty() == false) {
          if (s.equalsIgnoreCase("Last Column") == false) {
            throw new UnifyException("sdt_err_25", name);
          }
          found = true;
          break;
        }
      }

      if (found == false) {
        throw new UnifyException("sdt_err_26", name);
      }

      lastColIndex = i;
    }

    // get columns now
    ArrayList<DTColumn> cols = new ArrayList<>();
    {
      String colName = null;
      Row nameRow = sheet.getRow(COL_MARKERS_ROW_INDEX + 1);
      Row colTypeRow = sheet.getRow(COL_MARKERS_ROW_INDEX + 2);
      Row dataTypeRow = sheet.getRow(COL_MARKERS_ROW_INDEX + 3);
      boolean doesRuleIdColExist = false;
      boolean doesCommentsColExist = false;
      Set<String> colNames = new HashSet<>();

      for (int i = firstColIndex; i <= lastColIndex; i++) {
        // get col name
        c = nameRow.getCell(i);
        s = df.formatCellValue(c);
        if (s.isEmpty() == true) {
          throw new UnifyException("sdt_err_27", s, 6 + "", i + "");
        }
        colName = s;

        // duplicate name validation
        if (colNames.contains(colName) == true) {
          throw new UnifyException("sdt_err_40", colName, super.name);
        }
        colNames.add(colName);

        // get col type
        c = colTypeRow.getCell(i);
        s = df.formatCellValue(c);
        ColumnType colType = null;
        if (s.isEmpty() == true) {
          throw new UnifyException("sdt_err_28", colName, 7 + "", i + "");
        }
        if (s.equalsIgnoreCase("Evaluate") == true) {
          colType = ColumnType.EVALUATE;
        }
        else if (s.equalsIgnoreCase("Return") == true) {
          colType = ColumnType.RETURN;
        }
        else if (s.equalsIgnoreCase("Rule_Id") == true) {
          colType = ColumnType.RULE_ID;
          if (doesRuleIdColExist == true) {
            throw new UnifyException("sdt_err_37", super.name);
          }
          doesRuleIdColExist = true;
        }
        else if (s.equalsIgnoreCase("Comments") == true) {
          colType = ColumnType.COMMENTS;
          if (doesCommentsColExist == true) {
            throw new UnifyException("sdt_err_38", super.name);
          }
          doesCommentsColExist = true;
        }
        else {
          throw new UnifyException("sdt_err_35", colName, super.name);
        }

        // get data type
        c = dataTypeRow.getCell(i);
        s = df.formatCellValue(c);
        DataType dataType = null;
        if (s.isEmpty() == true) {
          throw new UnifyException("sdt_err_29", colName, 8 + "", i + "");
        }
        dataType = DataType.valueOf(s.toUpperCase());

        // validation to ensure that comments and rule id are string data types
        if (((colType == ColumnType.RULE_ID) || (colType == ColumnType.COMMENTS)) && dataType != DataType.STRING) {
          throw new UnifyException("sdt_err_39", super.name);
        }

        DTColumn col = new DTColumn(colName, colType, dataType);
        cols.add(col);
      }
    }

    return cols;
  }

  private ArrayList<DTRow> getRows(Sheet sheet) {
    Row row = sheet.getRow(COL_MARKERS_ROW_INDEX + 4);

    Cell c = null;
    String s = null;

    // check first row marker
    c = row.getCell(0);
    s = df.formatCellValue(c);
    if (s.equalsIgnoreCase("First Row") == false) {
      throw new UnifyException("sdt_err_31", name);
    }

    ArrayList<DTRow> rows = new ArrayList<>();
    {
      int index = COL_MARKERS_ROW_INDEX + 4;
      boolean found = false;
      int rowNum = 0;
      Set<String> ruleIdSet = new HashSet<>();

      // assuming a max of 10K rows
      for (int i = index; i < (index + 10000); i++) {
        rowNum++;
        DTRow dtRow = new DTRow();
        row = sheet.getRow(i);
        for (int j = firstColIndex; j <= lastColIndex; j++) {
          c = row.getCell(j);
          s = df.formatCellValue(c);
          DTCell dtCell = getDTCell(s, j, ruleIdSet, rowNum);
          if (dtCell != null) {
            dtRow.addCell(dtCell);
          }
        }

        rows.add(dtRow);

        c = row.getCell(0);
        s = df.formatCellValue(c);
        if (s.equalsIgnoreCase("Default Row") == true) {
          found = true;
          break;
        }
      }

      if (found == false) {
        throw new UnifyException("sdt_err_32", name);
      }
    }

    return rows;
  }

  private DTCell getDTCell(String content, int colIndex, Set<String> ruleIdSet, int rowNum) {
    DTCell dtCell = null;
    String value = null;
    OperatorType oprType = null;

    colIndex = colIndex - COL_OFFSET;
    String colName = cols.get(colIndex).getName();
    ColumnType colType = cols.get(colIndex).getColumnType();

    if ((colType == ColumnType.EVALUATE) && (content.isEmpty())) {
      content = null;
    }

    if (content != null) {
      switch (colType) {
        case EVALUATE:
          // we need to split the string
          int delimIndex = content.indexOf(' ');
          String opr = content.substring(0, delimIndex);
          oprType = OperatorType.from(opr);
          if (oprType == null) {
            throw new UnifyException("sdt_err_33", opr, name, content);
          }
          value = content.substring(delimIndex + 1);
          value = DecisionTable.dedupeValue(value, oprType);
          break;

        case RETURN:
        case COMMENTS:
          value = content;
          break;

        case RULE_ID:
          value = content.isEmpty() ? rowNum + "" : content;

          // check that it is unique
          if (ruleIdSet.contains(value) == true) {
            throw new UnifyException("sdt_err_36", value, rowNum + "", super.name);
          }
          ruleIdSet.add(value);

          break;

        default:
          break;
      }

      dtCell = new DTCell(colName, colType, value, oprType);
    }

    return dtCell;
  }

}
