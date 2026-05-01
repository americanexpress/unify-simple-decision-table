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

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @author Deepak Arora
 */
public class TestHandler implements EventHandler {

  @Override
  public void invoke(DecisionTable dt, EventType event, Document eventInfo) {
    String name = null;
    if (event == EventType.MATCH_FIRED) {
      eventInfo.deletePath("decision_table_event_match_fired$.decision_table_event.timestamp");
      name = eventInfo.getString("decision_table_event_match_fired$.decision_table_event.table_name");
    }
    else {
      eventInfo.deletePath("decision_table_event_rules_loaded$.decision_table_event.timestamp");
      name = eventInfo.getString("decision_table_event_rules_loaded$.decision_table_event.table_name");
    }
    String eventJson = eventInfo.getJson();

    if (name.equals("/com/americanexpress/unify/decision_table/DTTestEvent.json") == false) {
      return;
    }

    switch (event) {
      case MATCH_FIRED: {
        String expectedJson = BaseUtils.getResourceAsString(TestHandler.class, "/com/americanexpress/unify/decision_table/DTExpectedEventMatchFired.json");
        Document exp = new JDocument(expectedJson);
        exp.deletePath("$.decision_table_event.timestamp");
        expectedJson = exp.getJson();
        assertEquals(expectedJson, eventJson);
        break;
      }

      case RULES_LOADED: {
        String expectedJson = BaseUtils.getResourceAsString(TestHandler.class, "/com/americanexpress/unify/decision_table/DTExpectedEventRulesLoaded.json");
        Document exp = new JDocument(expectedJson);
        exp.deletePath("$.decision_table_event.timestamp");
        expectedJson = exp.getJson();
        assertEquals(expectedJson, eventJson);
        break;
      }
    }

  }

}
