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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * @author Deepak Arora
 */
public class ExcelToJson {

  private static final Logger logger = LoggerFactory.getLogger(ExcelToJson.class);

  private ExcelToJson() {
    // nothing to do
  }

  public static List<String> createJsonFiles(String baseDirPath, String filePattern) {
    // Please specify two arguments as below:
    // Argument 1 -> path to the root folder without trailing backslash i.e. C:/folder
    // Argument 2 -> excel file pattern i.e. .xlsx
    List<String> result = null;
    try (Stream<Path> walk = Files.walk(Paths.get(baseDirPath))) {
      walk.map(Path::toString)
              .filter(f -> f.endsWith(filePattern))
              .collect(Collectors.toList())
              .forEach(s -> createJsonFile(s));
    }
    catch (IOException e) {
      e.printStackTrace();
    }

    return result;
  }

  public static void createJsonFile(String filePath) {
    int index = filePath.lastIndexOf('.');
    String jsonFilePath = filePath.substring(0, index);
    jsonFilePath = jsonFilePath + ".json";
    logger.error("Creating file -> {}", jsonFilePath);

    ExcelReader er = new ExcelReader();
    er.loadDecisionTableFromFilePath(filePath);
    String json = er.getJson();
    try (PrintStream out = new PrintStream(new FileOutputStream(jsonFilePath))) {
      out.print(json);
    }
    catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

}
