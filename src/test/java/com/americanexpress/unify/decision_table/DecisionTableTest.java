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

import com.americanexpress.unify.jdocs.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/*
 * @author Deepak Arora
 */
class DecisionTableTest {

  @BeforeAll
  static void beforeAll() {
    JDocument.init(new Initializer());
    JDocument.configure(new Configurator()
                                .ignoreDocTypePrefixForBaseDocs(true)
                                .docTypePrefixPolicy(new DocTypePrefixPolicyEnforceForAll()));
    DecisionTable.init("", null);
  }

  @Test
  void testEvent() {
    try {
      Configuration conf = new Configuration().setEventHandler(new TestHandler());
      DecisionTable.init("Test", conf);
      DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTestEvent.json");

      Map<String, String> values = new HashMap<>();
      values.put("score", "90");
      values.put("yob", "2014");
      values.put("code", "4GG");

      List<MatchedRow> list = dt.evaluate(values);

      assertEquals("foo4", list.get(0).get("function").getString());
      assertEquals("", list.get(0).get("name").getString());
      assertEquals(4, (int)list.get(0).get("value").getInteger());
    }
    finally {
      DecisionTable.init("Test", null);
    }
  }

  @Test
  void testEvent1() {
    try {
      Configuration conf = new Configuration().setEventHandler(new TestHandler());
      DecisionTable.init("Test", conf);
      DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTestEvent1.json");

      Map<String, String> values = new HashMap<>();
      values.put("score", "90");
      values.put("yob", "");
      values.put("code", "4GG");
      values.put("is_married", "");

      List<MatchedRow> list = dt.evaluate(values);
      String s = list.get(0).get("function").getString();
      assertEquals("foo default", s);
    }
    finally {
      DecisionTable.init("Test", null);
    }
  }

  @Test
  void test2() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest1.json");

    Map<String, String> values = new HashMap<>();
    values.put("score", "90");
    values.put("yob", "2014");
    values.put("code", "4GG");

    List<MatchedRow> list = dt.evaluate(values);

    assertEquals("foo4", list.get(0).get("function").getString());
    assertEquals("", list.get(0).get("name").getString());
    assertEquals(4, (int)list.get(0).get("value").getInteger());
  }

  @Test
  void test3() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest1.json");

    Map<String, String> values = new HashMap<>();
    values.put("score", "45");
    values.put("yob", "2014");
    values.put("code", "4GG");

    List<MatchedRow> list = dt.evaluate(values);

    assertEquals("", list.get(0).get("function").getString());
    assertEquals("", list.get(0).get("name").getString());
    assertNull(list.get(0).get("value").getValue());
  }

  @Test
  void testJexl() {
    List<Class<?>> allowedList = new ArrayList<>();
    allowedList.add(Integer.class);
    Configuration conf = new Configuration().setAllowedClasses(allowedList);
    DecisionTable.init("", conf);
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTestJexl.json");

    Map<String, String> values = new HashMap<>();
    values.put("score", "1");
    values.put("yob", "1900");
    values.put("code", "jexl");
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals(1910, list.get(0).get("value").getValue());
    assertEquals("Hello from jexl", list.get(0).get("name").getString());
  }

  @Test
  void testJexl1() {
    List<Class<?>> allowedList = new ArrayList<>();
    allowedList.add(Long.class);
    Configuration conf = new Configuration().setAllowedClasses(allowedList);
    DecisionTable.init("", conf);
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTestJexl.json");

    Map<String, String> values = new HashMap<>();
    values.put("score", "2");
    values.put("yob", "1900");
    values.put("code", "jexl_2");
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals(1920, (long)list.get(0).get("value").getValue());
    assertEquals("Hello from jexl_2", list.get(0).get("name").getString());
  }

  @Test
  void testJexl2() {
    List<Class<?>> allowedList = new ArrayList<>();
    Configuration conf = new Configuration().setAllowedClasses(allowedList);
    DecisionTable.init("", conf);
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTestJexl.json");

    Map<String, String> values = new HashMap<>();
    values.put("score", "3");
    values.put("yob", "1900");
    values.put("code", "jexl_3");
    try {
      dt.evaluate(values);
      fail();
    }
    catch (Exception e) {
      assertTrue(true);
    }
  }

  @Test
  void testInvokable() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest2.json");
    Map<String, String> values = new HashMap<>();
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals("480", list.get(0).get("phone").getString());
    assertEquals("name_1", list.get(0).get("name").getString());
    assertEquals("xyz", list.get(1).get("phone").getString());
    assertEquals("From Invokable Return", list.get(1).get("name").getString());
    assertEquals(200, list.get(1).get("income_long").getLong());
    assertEquals(200, list.get(1).get("income_integer").getInteger());
    assertEquals(206.65, list.get(1).get("income_decimal").getBigDecimal().doubleValue());
    assertEquals(206.65, list.get(1).get("income_double").getDouble());
  }

  @Test
  void testInOperator() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest3.json");

    Map<String, String> values = new HashMap<>();
    values.put("code", "4GG");
    values.put("score", "5");
    List<MatchedRow> list = dt.evaluate(values);
    assertNull(list.get(0).get("new_score").getValue());
    assertEquals("", list.get(0).get("value").getString());

    values = new HashMap<>();
    values.put("code", "4GG");
    values.put("score", null);
    list = dt.evaluate(values);
    assertNull(list.get(0).get("new_score").getValue());
    assertEquals("", list.get(0).get("value").getString());
  }

  @Test
  void testDefaultRow() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest5.json");

    Map<String, String> values = new HashMap<>();
    values.put("code", "4GGZZZ");
    values.put("score", "5");
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals(2, (int)list.get(0).get("new_score").getInteger());
    assertEquals("row default", list.get(0).get("value").getString());
  }

  @Test
  void test8() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest6.json");

    Map<String, String> values = new HashMap<>();
    values.put("code", "4GG, 5FF");
    values.put("score", "5");
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals(0, (int)list.get(0).get("new_score").getInteger());
    assertEquals("row 0", list.get(0).get("value").getString());
  }

  @Test
  void test9() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest7.json");

    Map<String, String> values = new HashMap<>();
    values.put("code", "4GG, 5FF");
    values.put("score", "5");
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals(0, (int)list.get(0).get("new_score").getInteger());
    assertEquals("row 0", list.get(0).get("value").getString());

    values = new HashMap<>();
    values.put("code", "4GG, 5FZZ");
    values.put("score", "5");
    list = dt.evaluate(values);
    assertEquals(2, (int)list.get(0).get("new_score").getInteger());
    assertEquals("row default", list.get(0).get("value").getString());
  }

  @Test
  void test10() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest8.json");

    Map<String, String> values = new HashMap<>();
    values.put("code1", "CR, CV, CL, CI , CE");
    values.put("code2", "");
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals("row 0", list.get(0).get("value").getString());
  }

  @Test
  void testNotAnyIn() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest9.json");

    Map<String, String> values = new HashMap<>();
    values.put("code1", "1, 7, 8");
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals("row 0", list.get(0).get("value").getString());

    values.put("code1", "1, 2");
    list = dt.evaluate(values);
    assertEquals("row default", list.get(0).get("value").getString());

    values.put("code1", "7, 8");
    list = dt.evaluate(values);
    assertEquals("row 0", list.get(0).get("value").getString());
  }

  @Test
  void testNotAllIn() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest10.json");

    Map<String, String> values = new HashMap<>();
    values.put("code1", "1, 7, 2");
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals("row default", list.get(0).get("value").getString());

    values.put("code1", "7, 8");
    list = dt.evaluate(values);
    assertEquals("row 0", list.get(0).get("value").getString());
  }

  @Test
  void testAllEqualString() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest11.json");

    Map<String, String> values = new HashMap<>();
    values.put("code1", "1, 2");
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals("row default", list.get(0).get("value").getString());

    values.put("code1", "1, 1, 1");
    list = dt.evaluate(values);
    assertEquals("row default", list.get(0).get("value").getString());

    values.put("code1", "1, 5, 3, 2, 4");
    list = dt.evaluate(values);
    assertEquals("row 0", list.get(0).get("value").getString());

    values.put("code1", "1, 5, 3, 2, 1");
    list = dt.evaluate(values);
    assertEquals("row default", list.get(0).get("value").getString());
  }

  @Test
  void testAllEqualLong() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest12.json");

    Map<String, String> values = new HashMap<>();
    values.put("code1", "100, 200");
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals("row default", list.get(0).get("value").getString());

    values.put("code1", "100, 100, 100");
    list = dt.evaluate(values);
    assertEquals("row default", list.get(0).get("value").getString());

    values.put("code1", "100, 500, 300, 200, 400");
    list = dt.evaluate(values);
    assertEquals("row 0", list.get(0).get("value").getString());

    values.put("code1", "100, 500, 300, 200, 100");
    list = dt.evaluate(values);
    assertEquals("row default", list.get(0).get("value").getString());
  }

  @Test
  void testContainsAllString() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest13.json");

    Map<String, String> values = new HashMap<>();
    values.put("code1", "1, 2, 3, 4");
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals("row 0", list.get(0).get("value").getString());

    values.put("code1", "1, 1, 1");
    list = dt.evaluate(values);
    assertEquals("row default", list.get(0).get("value").getString());

    values.put("code1", "1");
    list = dt.evaluate(values);
    assertEquals("row default", list.get(0).get("value").getString());

    values.put("code1", "1, 2");
    list = dt.evaluate(values);
    assertEquals("row 0", list.get(0).get("value").getString());
  }

  @Test
  void testMissingCols() {
    try {
      DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest15.json");
      fail();
    }
    catch (Exception e) {
      assertTrue(true);
    }

  }

  @Test
  void testLenient() {
    try {
      DecisionTable.init("Test", null);
      DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTest14.json");

      Map<String, String> values = new HashMap<>();
      values.put("code1", "1");
      values.put("code2", null);
      List<MatchedRow> list = dt.evaluate(values);
      assertEquals("", list.get(0).get("value1").getString());
    }
    finally {
      DecisionTable.init("Test", null);
    }
  }

  @Test
  void testTemp() {
    // this test case is to run any temp code and see the result on console
    try {
      Configuration conf = new Configuration().setEventHandler(new TestHandler1());
      DecisionTable.init("Test", conf);
      DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTestTemp.json");

      Map<String, String> values = new HashMap<>();
      values.put("ecol1", "CR");
      values.put("ecol2", null);
      dt.evaluate(values);
      assertTrue(true);
    }
    finally {
      Configuration conf = new Configuration().setEventHandler(null);
      DecisionTable.init("Test", conf);
    }
  }

  @Test
  void testTemp1() {
    try {
      Configuration conf = new Configuration()
              .setEventHandler(new TestHandler())
              .setDefaultRowValidationPolicy(RowValidationPolicy.STRICT);
      DecisionTable.init("Test", conf);
    }
    catch (Exception e) {
      fail();
    }
  }

  @Test
  void testScenariosEq() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosEq.xlsx");

    Map<String, String> values = new HashMap<>();
    values.put("code", "c1");
    values.put("yob", "1770");
    values.put("score", "1000");
    values.put("match_double", "100.25");
    values.put("match_decimal", "100.525");
    values.put("is_member", "true");
    List<MatchedRow> list = dt.evaluate(values);

    // eq
    assertEquals("name_1", list.get(0).get("name").getString());
    assertEquals(25, list.get(0).get("age").getInteger());
    assertEquals(100000, list.get(0).get("income").getLong());
    assertEquals(38.15, list.get(0).get("rank_double").getDouble());
    assertEquals(BigDecimal.valueOf(39.125), list.get(0).get("rank_decimal").getBigDecimal());
    assertEquals(true, list.get(0).get("is_self_employed").getBoolean());

    // eq
    values.put("is_member", "false");
    list = dt.evaluate(values);
    assertEquals("name_2", list.get(0).get("name").getString());
    assertEquals(false, list.get(0).get("is_self_employed").getBoolean());

    // eq
    values.put("code", "c4");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("name").getString());
  }

  @Test
  void testScenariosGt() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosGt.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c1");
    values.put("yob", "1770");
    values.put("score", "1000");
    values.put("match_double", "100.25");
    values.put("match_decimal", "100.525");
    values.put("is_member", "false");
    List<MatchedRow> list;

    // gt
    value = values.get("code");
    values.put("code", "c2");
    list = dt.evaluate(values);
    assertEquals("name_gt_1", list.get(0).get("name").getString());
    values.put("code", value);

    // gt
    value = values.get("yob");
    values.put("yob", "1800");
    list = dt.evaluate(values);
    assertEquals("name_gt_2", list.get(0).get("name").getString());
    values.put("yob", value);

    // gt
    value = values.get("score");
    values.put("score", "1100");
    list = dt.evaluate(values);
    assertEquals("name_gt_3", list.get(0).get("name").getString());
    values.put("score", value);

    // gt
    value = values.get("match_double");
    values.put("match_double", "200.25");
    list = dt.evaluate(values);
    assertEquals("name_gt_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    // gt
    value = values.get("match_decimal");
    values.put("match_decimal", "200.25");
    list = dt.evaluate(values);
    assertEquals("name_gt_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);
  }

  @Test
  void testScenariosGteq() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosGteq.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c1");
    values.put("yob", "1770");
    values.put("score", "1000");
    values.put("match_double", "100.25");
    values.put("match_decimal", "100.525");
    values.put("is_member", "false");
    List<MatchedRow> list;

    // gteq
    value = values.get("code");
    values.put("code", "c0");
    list = dt.evaluate(values);
    assertEquals("name_gteq_1", list.get(0).get("name").getString());
    values.put("code", value);

    // gteq
    value = values.get("yob");
    values.put("yob", "1600");
    list = dt.evaluate(values);
    assertEquals("name_gteq_2", list.get(0).get("name").getString());
    values.put("yob", value);

    // gteq
    value = values.get("score");
    values.put("score", "500");
    list = dt.evaluate(values);
    assertEquals("name_gteq_3", list.get(0).get("name").getString());
    values.put("score", value);

    // gteq
    value = values.get("match_double");
    values.put("match_double", "60.25");
    list = dt.evaluate(values);
    assertEquals("name_gteq_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    // gteq
    value = values.get("match_decimal");
    values.put("match_decimal", "60.25");
    list = dt.evaluate(values);
    assertEquals("name_gteq_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);
  }

  @Test
  void testScenariosLteq() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosLteq.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c1");
    values.put("yob", "1770");
    values.put("score", "1000");
    values.put("match_double", "100.25");
    values.put("match_decimal", "100.525");
    values.put("is_member", "false");
    List<MatchedRow> list;

    // lteq
    value = values.get("code");
    values.put("code", "b0");
    list = dt.evaluate(values);
    assertEquals("name_lteq_1", list.get(0).get("name").getString());
    values.put("code", value);

    // lteq
    value = values.get("yob");
    values.put("yob", "1500");
    list = dt.evaluate(values);
    assertEquals("name_lteq_2", list.get(0).get("name").getString());
    values.put("yob", value);

    // lteq
    value = values.get("score");
    values.put("score", "400");
    list = dt.evaluate(values);
    assertEquals("name_lteq_3", list.get(0).get("name").getString());
    values.put("score", value);

    // lteq
    value = values.get("match_double");
    values.put("match_double", "40.25");
    list = dt.evaluate(values);
    assertEquals("name_lteq_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    // lteq
    value = values.get("match_decimal");
    values.put("match_decimal", "40.25");
    list = dt.evaluate(values);
    assertEquals("name_lteq_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);
  }

  @Test
  void testScenariosNoteq() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosNoteq.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c1");
    values.put("yob", "1770");
    values.put("score", "1000");
    values.put("match_double", "100.25");
    values.put("match_decimal", "100.525");
    values.put("is_member", "false");
    List<MatchedRow> list;

    // noteq
    value = values.get("code");
    values.put("code", "ifywsaxde5");
    list = dt.evaluate(values);
    assertEquals("name_noteq_1", list.get(0).get("name").getString());
    values.put("code", value);

    // noteq
    value = values.get("yob");
    values.put("yob", "1500");
    list = dt.evaluate(values);
    assertEquals("name_noteq_2", list.get(0).get("name").getString());
    values.put("yob", value);

    // noteq
    value = values.get("score");
    values.put("score", "400");
    list = dt.evaluate(values);
    assertEquals("name_noteq_3", list.get(0).get("name").getString());
    values.put("score", value);

    // noteq
    value = values.get("match_double");
    values.put("match_double", "40.25");
    list = dt.evaluate(values);
    assertEquals("name_noteq_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    // noteq
    value = values.get("match_decimal");
    values.put("match_decimal", "40.25");
    list = dt.evaluate(values);
    assertEquals("name_noteq_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);
  }

  @Test
  void testScenariosIn() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosIn.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c1");
    values.put("yob", "1770");
    values.put("score", "1000");
    values.put("match_double", "100.25");
    values.put("match_decimal", "100.525");
    values.put("is_member", "false");
    List<MatchedRow> list;

    value = values.get("code");
    values.put("code", "c2");
    list = dt.evaluate(values);
    assertEquals("name_1", list.get(0).get("name").getString());
    values.put("code", value);

    value = values.get("yob");
    values.put("yob", "600");
    list = dt.evaluate(values);
    assertEquals("name_2", list.get(0).get("name").getString());
    values.put("yob", value);

    value = values.get("score");
    values.put("score", "600");
    list = dt.evaluate(values);
    assertEquals("name_3", list.get(0).get("name").getString());
    values.put("score", value);

    value = values.get("match_double");
    values.put("match_double", "300.56");
    list = dt.evaluate(values);
    assertEquals("name_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "300.56");
    list = dt.evaluate(values);
    assertEquals("name_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);

    value = values.get("code");
    values.put("code", "c4");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("name").getString());
    values.put("code", value);
  }

  @Test
  void testScenariosNotIn() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosNotIn.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c1");
    values.put("yob", "1770");
    values.put("score", "1000");
    values.put("match_double", "100.25");
    values.put("match_decimal", "100.525");
    values.put("is_member", "false");
    List<MatchedRow> list;

    value = values.get("code");
    values.put("code", "c4");
    list = dt.evaluate(values);
    assertEquals("name_1", list.get(0).get("name").getString());
    values.put("code", value);

    value = values.get("yob");
    values.put("yob", "800");
    list = dt.evaluate(values);
    assertEquals("name_2", list.get(0).get("name").getString());
    values.put("yob", value);

    value = values.get("score");
    values.put("score", "900");
    list = dt.evaluate(values);
    assertEquals("name_3", list.get(0).get("name").getString());
    values.put("score", value);

    value = values.get("match_double");
    values.put("match_double", "600.56");
    list = dt.evaluate(values);
    assertEquals("name_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "600.56");
    list = dt.evaluate(values);
    assertEquals("name_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);

    value = values.get("code");
    values.put("code", "c3");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("name").getString());
    values.put("code", value);
  }

  @Test
  void testScenariosAnyContainedIn() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosAnyContainedIn.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c1");
    values.put("yob", "1770");
    values.put("score", "1000");
    values.put("match_double", "100.25");
    values.put("match_decimal", "100.525");
    values.put("is_member", "false");
    List<MatchedRow> list;

    value = values.get("code");
    values.put("code", "c1, c2");
    list = dt.evaluate(values);
    assertEquals("name_1", list.get(0).get("name").getString());
    values.put("code", value);

    value = values.get("yob");
    values.put("yob", "500, 600");
    list = dt.evaluate(values);
    assertEquals("name_2", list.get(0).get("name").getString());
    values.put("yob", value);

    value = values.get("score");
    values.put("score", "500, 600");
    list = dt.evaluate(values);
    assertEquals("name_3", list.get(0).get("name").getString());
    values.put("score", value);

    value = values.get("match_double");
    values.put("match_double", "300.56, 500.56");
    list = dt.evaluate(values);
    assertEquals("name_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "300.56");
    list = dt.evaluate(values);
    assertEquals("name_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);

    value = values.get("code");
    values.put("code", "c4");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("name").getString());
    values.put("code", value);
  }

  @Test
  void testScenariosNotAnyContainedIn() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosNotAnyContainedIn.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c4");
    values.put("yob", "2000");
    values.put("score", "2000");
    values.put("match_double", "111.25");
    values.put("match_decimal", "111.525");
    values.put("is_member", "false");
    List<MatchedRow> list;

    value = values.get("code");
    values.put("code", "c5, c6");
    list = dt.evaluate(values);
    assertEquals("name_1", list.get(0).get("name").getString());
    values.put("code", value);

    value = values.get("yob");
    values.put("yob", "1900, 2100");
    list = dt.evaluate(values);
    assertEquals("name_2", list.get(0).get("name").getString());
    values.put("yob", value);

    value = values.get("score");
    values.put("score", "1800, 2100");
    list = dt.evaluate(values);
    assertEquals("name_3", list.get(0).get("name").getString());
    values.put("score", value);

    value = values.get("match_double");
    values.put("match_double", "102.25, 500.56");
    list = dt.evaluate(values);
    assertEquals("name_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "101.55");
    list = dt.evaluate(values);
    assertEquals("name_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "100.55");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("name").getString());
    values.put("match_decimal", value);
  }

  @Test
  void testScenariosAllContainedIn() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosAllContainedIn.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c3");
    values.put("yob", "1500");
    values.put("score", "1000, 1100");
    values.put("match_double", "100.25, 300.56, 500.56");
    values.put("match_decimal", "100.525");
    values.put("is_member", "false");
    List<MatchedRow> list;

    value = values.get("code");
    values.put("code", "c1, c2");
    list = dt.evaluate(values);
    assertEquals("name_1", list.get(0).get("name").getString());
    values.put("code", value);

    value = values.get("yob");
    values.put("yob", "1500, 1600");
    list = dt.evaluate(values);
    assertEquals("name_2", list.get(0).get("name").getString());
    values.put("yob", value);

    value = values.get("score");
    values.put("score", "1000, 1100, 1200");
    list = dt.evaluate(values);
    assertEquals("name_3", list.get(0).get("name").getString());
    values.put("score", value);

    value = values.get("match_double");
    values.put("match_double", "600.56");
    list = dt.evaluate(values);
    assertEquals("name_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "300.56");
    list = dt.evaluate(values);
    assertEquals("name_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "100.55");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("name").getString());
    values.put("match_decimal", value);
  }

  @Test
  void testScenariosNotAllContainedIn() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosNotAllContainedIn.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c4, c5");
    values.put("yob", "2000, 3000");
    values.put("score", "2000, 3000");
    values.put("match_double", "110.25, 111.25");
    values.put("match_decimal", "110.25, 111.25");
    values.put("is_member", "false");
    List<MatchedRow> list;

    list = dt.evaluate(values);
    assertEquals("name_1", list.get(0).get("name").getString());

    value = values.get("yob");
    values.put("yob", "1900");
    list = dt.evaluate(values);
    assertEquals("name_2", list.get(0).get("name").getString());
    values.put("yob", value);

    value = values.get("score");
    values.put("score", "1800");
    list = dt.evaluate(values);
    assertEquals("name_3", list.get(0).get("name").getString());
    values.put("score", value);

    value = values.get("match_double");
    values.put("match_double", "102.25");
    list = dt.evaluate(values);
    assertEquals("name_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "101.25");
    list = dt.evaluate(values);
    assertEquals("name_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "100.25");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("name").getString());
    values.put("match_decimal", value);
  }

  @Test
  void testScenariosContainsAll() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosContainsAll.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c1, c2, c3");
    values.put("yob", "1500, 1600, 1700");
    values.put("score", "1000, 1100");
    values.put("match_double", "100.25, 101.25");
    values.put("match_decimal", "100.25, 101.25");
    values.put("is_member", "false");
    List<MatchedRow> list;

    list = dt.evaluate(values);
    assertEquals("name_1", list.get(0).get("name").getString());

    value = values.get("yob");
    values.put("yob", "1500");
    list = dt.evaluate(values);
    assertEquals("name_2", list.get(0).get("name").getString());
    values.put("yob", value);

    value = values.get("score");
    values.put("score", "1000");
    list = dt.evaluate(values);
    assertEquals("name_3", list.get(0).get("name").getString());
    values.put("score", value);

    value = values.get("match_double");
    values.put("match_double", "100.25");
    list = dt.evaluate(values);
    assertEquals("name_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "100.25");
    list = dt.evaluate(values);
    assertEquals("name_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "101.25");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("name").getString());
    values.put("match_decimal", value);
  }

  @Test
  void testScenariosNotContainsAll() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosNotContainsAll.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c3");
    values.put("yob", "1400");
    values.put("score", "900");
    values.put("match_double", "199.25");
    values.put("match_decimal", "199.25");
    values.put("is_member", "false");
    List<MatchedRow> list;

    list = dt.evaluate(values);
    assertEquals("name_1", list.get(0).get("name").getString());

    value = values.get("yob");
    values.put("yob", "1900");
    list = dt.evaluate(values);
    assertEquals("name_2", list.get(0).get("name").getString());
    values.put("yob", value);

    value = values.get("score");
    values.put("score", "1800");
    list = dt.evaluate(values);
    assertEquals("name_3", list.get(0).get("name").getString());
    values.put("score", value);

    value = values.get("match_double");
    values.put("match_double", "101.25");
    list = dt.evaluate(values);
    assertEquals("name_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "101.25");
    list = dt.evaluate(values);
    assertEquals("name_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "100.25");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("name").getString());
    values.put("match_decimal", value);
  }

  @Test
  void testScenariosAllEquals() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTestScenariosAllEqual.xlsx");

    String value;

    Map<String, String> values = new HashMap<>();
    values.put("code", "c1, c2, c1, c2");
    values.put("yob", "1600, 1500, 1500");
    values.put("score", "1000, 1000, 1100, 1100");
    values.put("match_double", "100.25, 101.25");
    values.put("match_decimal", "100.25, 101.25");
    values.put("is_member", "false");
    List<MatchedRow> list;

    list = dt.evaluate(values);
    assertEquals("name_1", list.get(0).get("name").getString());

    value = values.get("yob");
    values.put("yob", "1500, 1500");
    list = dt.evaluate(values);
    assertEquals("name_2", list.get(0).get("name").getString());
    values.put("yob", value);

    value = values.get("score");
    values.put("score", "1000");
    list = dt.evaluate(values);
    assertEquals("name_3", list.get(0).get("name").getString());
    values.put("score", value);

    value = values.get("match_double");
    values.put("match_double", "100.25, 100.25");
    list = dt.evaluate(values);
    assertEquals("name_4", list.get(0).get("name").getString());
    values.put("match_double", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "100.25, 100.25");
    list = dt.evaluate(values);
    assertEquals("name_5", list.get(0).get("name").getString());
    values.put("match_decimal", value);

    value = values.get("match_decimal");
    values.put("match_decimal", "107.25");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("name").getString());
    values.put("match_decimal", value);
  }

  @Test
  void testExcel1() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTest1.xlsx");

    Map<String, String> values = new HashMap<>();
    values.put("score", "100");
    values.put("yob", "1974");
    values.put("code", "4GG");

    List<MatchedRow> list = dt.evaluate(values);
    assertEquals("foo1", list.get(0).get("function").getString());
    assertEquals("From Invokable Return", list.get(0).get("name").getString());
    assertEquals(11, list.get(0).get("value").getInteger());

    values = new HashMap<>();
    values.put("score", "90");
    values.put("yob", "2014");
    values.put("code", "4GG");

    list = dt.evaluate(values);
    assertEquals("foo4", list.get(0).get("function").getString());
    assertEquals("", list.get(0).get("name").getString());
    assertEquals(4, list.get(0).get("value").getInteger());

    values = new HashMap<>();
    values.put("score", "45");
    values.put("yob", "2014");
    values.put("code", "4GG");

    list = dt.evaluate(values);
    assertEquals("", list.get(0).get("function").getString());
    assertEquals("", list.get(0).get("name").getString());
    assertNull(list.get(0).get("value").getInteger());
  }

  @Test
  void testExcel4() {
    DecisionTable dt = DecisionTable.fromExcel("/com/americanexpress/unify/decision_table/DTTest2.xlsx");

    Map<String, String> values = new HashMap<>();
    values.put("logo", "L1");

    List<MatchedRow> list = dt.evaluate(values);
    assertEquals("Second", list.get(0).get("name").getString());
  }

  @Test
  void testExcelToJson() {
    try {
      ExcelReader er = new ExcelReader();
      er.loadDecisionTableFromResourcePath("/com/americanexpress/unify/decision_table/DTTest1.xlsx");
      er.getJson();
    }
    catch (Exception e) {
      fail();
    }

  }

  @Test
  void testDedupe() {
    String value = "c3, c2, c1, c\\1, c3, c3, c2, c\\1, c1, c\\,1, c\\,1, c\\\\,1, c\\\\,1";
    String dv = DecisionTable.dedupeValue(value);
    assertEquals("c,1,c1,c2,c3,c\\,1,c\\1", dv);
  }

  @Test
  void testGetTrimmedValues() {
    String value = "c\\,c, c3, c\\1";
    String[] tv = DecisionTable.getTrimmedValues(value);
    assertEquals("c,c", tv[0]);
    assertEquals("c3", tv[1]);
    assertEquals("c\\1", tv[2]);
  }

  @Test
  public void testMatchesRegexOperator() {
    DecisionTable dt = DecisionTable.fromJson("/com/americanexpress/unify/decision_table/DTTestRegex.json");

    Map<String, String> values = new HashMap<>();
    values.put("c_long", "9001");
    List<MatchedRow> list = dt.evaluate(values);
    assertEquals("long", list.get(0).get("value").getString());
    values.clear();

    values.put("c_int", "7");
    list = dt.evaluate(values);
    assertEquals("integer", list.get(0).get("value").getString());
    values.clear();

    values.put("c_double", "12.0");
    list = dt.evaluate(values);
    assertEquals("double", list.get(0).get("value").getString());
    values.clear();

    values.put("c_decimal", "45.670");
    list = dt.evaluate(values);
    assertEquals("bigdecimal", list.get(0).get("value").getString());
    values.clear();

    values.put("c_string", "AB12");
    list = dt.evaluate(values);
    assertEquals("string", list.get(0).get("value").getString());
    values.clear();

    // now we run negative test cases
    values.put("c_long", "900");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("value").getString());
    values.clear();

    values.put("c_int", "70");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("value").getString());
    values.clear();

    values.put("c_double", "12.1");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("value").getString());
    values.clear();

    values.put("c_decimal", "45.67");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("value").getString());
    values.clear();

    values.put("c_string", "AC12");
    list = dt.evaluate(values);
    assertEquals("default", list.get(0).get("value").getString());

  }

}
