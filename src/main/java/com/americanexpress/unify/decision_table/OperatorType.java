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
enum OperatorType {
  GT_EQ(">="),
  GT(">"),
  EQ("="),
  LT("<"),
  LT_EQ("<="),
  NOT_EQ("<>"),
  IN("IN"),
  NOT_IN("NOT_IN"),
  ANY_CONTAINED_IN("ANY_CONTAINED_IN"),
  NOT_ANY_CONTAINED_IN("NOT_ANY_CONTAINED_IN"),
  ALL_CONTAINED_IN("ALL_CONTAINED_IN"),
  NOT_ALL_CONTAINED_IN("NOT_ALL_CONTAINED_IN"),
  CONTAINS_ALL("CONTAINS_ALL"),
  NOT_CONTAINS_ALL("NOT_CONTAINS_ALL"),
  ALL_EQUAL("ALL_EQUAL"),
  MATCHES_REGEX("MATCHES_REGEX");

  private String opr = null;

  OperatorType(String opr) {
    this.opr = opr;
  }

  public String getOperatorString() {
    return opr;
  }

  public static OperatorType from(String s) {
    OperatorType opr = null;

    switch (s) {
      case ">=":
        opr = GT_EQ;
        break;

      case ">":
        opr = GT;
        break;

      case "=":
        opr = EQ;
        break;

      case "<":
        opr = LT;
        break;

      case "<=":
        opr = LT_EQ;
        break;

      case "<>":
      case "!=":
        opr = NOT_EQ;
        break;

      case "IN":
        opr = IN;
        break;

      case "NOT_IN":
        opr = NOT_IN;
        break;

      case "ANY_CONTAINED_IN":
        opr = ANY_CONTAINED_IN;
        break;

      case "NOT_ANY_CONTAINED_IN":
        opr = NOT_ANY_CONTAINED_IN;
        break;

      case "ALL_CONTAINED_IN":
        opr = ALL_CONTAINED_IN;
        break;

      case "NOT_ALL_CONTAINED_IN":
        opr = NOT_ALL_CONTAINED_IN;
        break;

      case "CONTAINS_ALL":
        opr = CONTAINS_ALL;
        break;

      case "NOT_CONTAINS_ALL":
        opr = NOT_CONTAINS_ALL;
        break;

      case "ALL_EQUAL":
        opr = ALL_EQUAL;
        break;

      case "MATCHES_REGEX":
        opr = MATCHES_REGEX;
        break;

      default:
        // nothing to do. We return null
    }
    return opr;
  }
}
