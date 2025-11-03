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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * @author Deepak Arora
 */
final class DTRow {

  private Map<String, DTCell> retDTCells = new HashMap<>();
  private List<DTCell> retDTCellsList = new ArrayList<>();

  private Map<String, DTCell> evalDTCells = new HashMap<>();
  private List<DTCell> evalDTCellsList = new ArrayList<>();

  private DTCell ruleIdCell = null;
  private DTCell commentsCell = null;

  public void addCell(DTCell c) {
    if (c != null) {  // this is just a safety check
      switch (c.getColumnType()) {
        case EVALUATE:
          evalDTCells.put(c.getColumnName(), c);
          evalDTCellsList.add(c);
          break;

        case RETURN:
          retDTCells.put(c.getColumnName(), c);
          retDTCellsList.add(c);
          break;

        case COMMENTS:
          commentsCell = c;
          break;

        case RULE_ID:
          ruleIdCell = c;
          break;

        default:
          // will never reach here
          break;
      }
    }
  }

  protected Map<String, DTCell> getEvalDTCells() {
    return evalDTCells;
  }

  protected List<DTCell> getEvalDTCellsList() {
    return evalDTCellsList;
  }

  protected Map<String, DTCell> getRetDTCells() {
    return retDTCells;
  }

  protected List<DTCell> getRetDTCellsAsList() {
    return retDTCellsList;
  }

  protected DTCell getRuleIdCell() {
    return ruleIdCell;
  }

  protected DTCell getCommentsCell() {
    return commentsCell;
  }

}
