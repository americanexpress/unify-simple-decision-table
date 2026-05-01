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

import com.americanexpress.unify.base.ErrorTuple;
import com.americanexpress.unify.base.UnifyException;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * @author Deepak Arora
 */
public class JsonToExcel {

  private static final Logger logger = LoggerFactory.getLogger(JsonToExcel.class);

  private JsonToExcel() {
    // nothing to do
  }

  public static List<String> createExcelFiles(String baseDirPath, String filePattern) {
    // Please specify two arguments as below:
    // Argument 1 -> path to the root folder without trailing backslash i.e. C:/folder
    // Argument 2 -> json file pattern i.e. .json
    if ((baseDirPath == null) || (filePattern == null)) {
      logger.error("Please specify two arguments as below:");
      logger.error("Argument 1 -> path to the root folder without trailing backslash i.e. C:/folder");
      logger.error("Argument 2 -> json file pattern i.e. .json");
      throw new UnifyException(new ErrorTuple("error", "Invalid arguments"));
    }

    List<String> result = null;
    try (Stream<Path> walk = Files.walk(Paths.get(baseDirPath))) {
      walk.map(Path::toString)
              .filter(f -> f.endsWith(filePattern))
              .collect(Collectors.toList())
              .forEach(s -> createExcelFile(s));
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    return result;
  }

  public static void createExcelFile(String filePath) {
    // please specify the path to the JSON file i.e. C:/folder/1.json
    if (filePath == null) {
      logger.error("Invalid json file path");
      throw new UnifyException(new ErrorTuple("error", "Invalid json file path"));
    }

    int index = filePath.lastIndexOf('.');
    String excelFilePath = filePath.substring(0, index);
    excelFilePath = excelFilePath + ".xlsx";
    logger.info("Creating file -> {}", excelFilePath);

    JsonReader jr = new JsonReader();
    DecisionTable dt = jr.loadDecisionTableFromFilePath(filePath);
    try {
      writeToExcel(dt, excelFilePath);
    }
    catch (IOException e) {
      throw new UnifyException("error", e);
    }
  }

  private static void writeToExcel(DecisionTable dt, String outFilePath) throws IOException {
    Workbook wb = null;
    Sheet sheet = null;
    Sheet sheetValues = null;

    try {
      // open the template workbook
      try (InputStream is = JsonToExcel.class.getResourceAsStream("/com/americanexpress/unify/decision_table/DecisionTableBlankTemplate.xlsx")) {
        wb = WorkbookFactory.create(is);
        sheet = wb.getSheet("Decision Table");
        sheetValues = wb.getSheet("Values");
      }

      writeHeaders(dt, sheet, sheetValues);
      writeColumnMarkers(dt, sheet, sheetValues);
      writeColumnNameRow(dt, sheet, sheetValues);
      writeColumnTypeRow(dt, sheet, sheetValues);
      writeColumnDataTypeRow(dt, sheet, sheetValues);
      writeRows(dt, sheet, sheetValues);
      autoSizeCols(dt, sheet);

      // Write the output to a file
      try (OutputStream fileOut = new FileOutputStream(outFilePath)) {
        wb.write(fileOut);
      }
    }
    finally {
      if (wb != null) {
        wb.close();
      }
    }
  }

  private static void autoSizeCols(DecisionTable dt, Sheet sheet) {
    for (int i = 0; i <= dt.colList.size(); i++) {
      sheet.autoSizeColumn(i);
    }
  }

  private static void writeRows(DecisionTable dt, Sheet sheet, Sheet sheetValues) {
    for (int i = 0; i < dt.dtRows.size(); i++) {
      Row row = sheet.createRow(i + 8);

      if (i == 0) {
        Cell c = row.createCell(0);
        c.setCellValue("First Row");
        c.setCellStyle(sheetValues.getRow(8).getCell(2).getCellStyle());
      }

      if (i == (dt.dtRows.size() - 1)) {
        Cell c = row.createCell(0);
        c.setCellValue("Default Row");
        c.setCellStyle(sheetValues.getRow(8).getCell(2).getCellStyle());
      }

      int colIndex = 1;
      for (DTColumn dtCol : dt.colList) {
        String cellValue = null;
        ColumnType colType = dtCol.getColumnType();
        switch (colType) {
          case RULE_ID: {
            cellValue = dt.dtRows.get(i).getRuleIdCell().getValue();
            break;
          }

          case COMMENTS: {
            cellValue = dt.dtRows.get(i).getCommentsCell().getValue();
            break;
          }

          case EVALUATE: {
            DTCell dtCell = dt.dtRows.get(i).getEvalDTCells().get(dtCol.getName());
            if (dtCell != null) {
              cellValue = dtCell.getOprType().getOperatorString() + " " + dtCell.getValue();
            }
            break;
          }

          case RETURN: {
            DTCell dtCell = dt.dtRows.get(i).getRetDTCells().get(dtCol.getName());
            if (dtCell != null) {
              cellValue = dtCell.getValue();
            }
            break;
          }

          default: {
            throw new UnifyException(new ErrorTuple("error", "Invalid column type"));
          }
        }

        Cell c = row.createCell(colIndex++);
        c.setCellValue(cellValue);
        c.setCellStyle(sheetValues.getRow(18).getCell(2).getCellStyle());
      }
    }
  }

  private static void writeColumnNameRow(DecisionTable dt, Sheet sheet, Sheet sheetValues) {
    Row row = sheet.createRow(5);
    Cell c = row.createCell(0);
    c.setCellValue("Col name ->");
    c.setCellStyle(sheetValues.getRow(16).getCell(2).getCellStyle());
    int colIndex = 1;
    for (DTColumn dtCols : dt.colList) {
      c = row.createCell(colIndex++);
      c.setCellValue(dtCols.getName());
      c.setCellStyle(sheetValues.getRow(12).getCell(2).getCellStyle());
    }
  }

  private static void writeColumnTypeRow(DecisionTable dt, Sheet sheet, Sheet sheetValues) {
    Row row = sheet.createRow(6);
    Cell c = row.createCell(0);
    c.setCellValue("Col type ->");
    c.setCellStyle(sheetValues.getRow(16).getCell(2).getCellStyle());
    int colIndex = 1;
    for (DTColumn dtCols : dt.colList) {
      c = row.createCell(colIndex++);
      c.setCellValue(dtCols.getColumnType().toString());
      c.setCellStyle(sheetValues.getRow(12).getCell(2).getCellStyle());
    }
  }

  private static void writeColumnDataTypeRow(DecisionTable dt, Sheet sheet, Sheet sheetValues) {
    Row row = sheet.createRow(7);
    Cell c = row.createCell(0);
    c.setCellValue("Col data type ->");
    c.setCellStyle(sheetValues.getRow(16).getCell(2).getCellStyle());
    int colIndex = 1;
    for (DTColumn dtCols : dt.colList) {
      c = row.createCell(colIndex++);
      c.setCellValue(dtCols.getDataType().toString());
      c.setCellStyle(sheetValues.getRow(12).getCell(2).getCellStyle());
    }
  }

  private static void writeColumnMarkers(DecisionTable dt, Sheet sheet, Sheet sheetValues) {
    Row row = sheet.createRow(4);
    Cell cell = row.createCell(1);
    cell.setCellValue("First Column");
    cell.setCellStyle(sheetValues.getRow(10).getCell(2).getCellStyle());
    cell = row.createCell(dt.colList.size());
    cell.setCellValue("Last Column");
    cell.setCellStyle(sheetValues.getRow(10).getCell(2).getCellStyle());
  }

  private static void writeHeaders(DecisionTable dt, Sheet sheet, Sheet sheetValues) {
    // set version
    Row row = sheet.createRow(0);
    Cell c = row.createCell(0);
    c.setCellValue("Version");
    c.setCellStyle(sheetValues.getRow(16).getCell(2).getCellStyle());

    row.createCell(1).setCellValue("1");

    // set match policy
    row = sheet.createRow(1);
    c = row.createCell(0);
    c.setCellValue("Match Policy");
    c.setCellStyle(sheetValues.getRow(16).getCell(2).getCellStyle());

    if (dt.matchPolicy == MatchPolicy.FIRST_MATCH) {
      c = row.createCell(1);
      c.setCellValue("First match");
    }
    else if (dt.matchPolicy == MatchPolicy.ALL_MATCHES) {
      c = row.createCell(1);
      c.setCellValue("All matches");
    }
    else {
      throw new UnifyException(new ErrorTuple("error", "Invalid match policy"));
    }

    // set no match policy
    row = sheet.createRow(2);
    c = row.createCell(0);
    c.setCellValue("No Match Policy");
    c.setCellStyle(sheetValues.getRow(16).getCell(2).getCellStyle());

    if (dt.noMatchPolicy == NoMatchPolicy.RETURN_DEFAULT) {
      row.createCell(1).setCellValue("Return default");
    }
    else if (dt.noMatchPolicy == NoMatchPolicy.RETURN_NONE) {
      row.createCell(1).setCellValue("Return none");
    }
    else {
      throw new UnifyException(new ErrorTuple("error", "Invalid no match policy"));
    }
  }

}
