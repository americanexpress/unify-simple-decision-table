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
import com.americanexpress.unify.base.ErrorTuple;
import com.americanexpress.unify.base.UnifyException;
import com.americanexpress.unify.jdocs.Document;
import com.americanexpress.unify.jdocs.JDocument;
import com.americanexpress.unify.utils.IdleExpiryCache;
import com.americanexpress.unify.utils.IdleExpiryCacheFactory;
import org.apache.commons.jexl3.*;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * @author Deepak Arora
 */
public final class DecisionTable {

  private static final Logger logger = LoggerFactory.getLogger(DecisionTable.class);
  public static final String UTC_TS_FMT = "uuuu-MM-dd HH:mm:ss.SSS VV";

  private String name = null;

  // package visibility
  final Map<String, DTColumn> evalColumns = new HashMap<>();
  final Map<String, DTColumn> retColumns = new HashMap<>();
  List<DTColumn> colList = null;
  List<DTRow> dtRows = new ArrayList<>();
  MatchPolicy matchPolicy = null;
  NoMatchPolicy noMatchPolicy = null;

  private static final Lock rlock = new ReentrantLock(true);
  private static JexlEngine jexlEngine = null;
  static EventHandler eventHandler = null;
  static String systemName = "";
  static RowValidationPolicy defaultRowValidationPolicy = RowValidationPolicy.STRICT;
  static Set<Class<?>> allowedClasses = null;

  // init IdleExpiryCaches
  static {
    IdleExpiryCacheFactory.createCache(getCacheName("decision_table_cache"), 300000L, DecisionTable.class); // 5m
    IdleExpiryCacheFactory.createCache(getCacheName("decision_table_invokable_cache"), 300000L, Object.class); // 5m
    IdleExpiryCacheFactory.createCache(getCacheName("decision_table_jexl_cache"), 300000L, JexlScript.class); // 5m
  }

  private static String getCacheName(String name) {
    return DecisionTable.class.getName() + "." + name;
  }

  public static void init(String systemName, Configuration conf) {
    // check if underlying library is not initialized
    if (JDocument.isInitialized() == false) {
      throw new UnifyException("sdt_err_50");
    }

    IdleExpiryCacheFactory.clear(getCacheName("decision_table_cache"));
    IdleExpiryCacheFactory.clear(getCacheName("decision_table_invokable_cache"));
    IdleExpiryCacheFactory.clear(getCacheName("decision_table_jexl_cache"));

    ERRORS_SDT.load();
    DecisionTable.systemName = systemName;

    if (conf == null) {
      conf = new Configuration();
    }

    // init event handler
    DecisionTable.eventHandler = conf.eventHandler;
    if (DecisionTable.eventHandler != null) {
      String json = BaseUtils.getResourceAsString(DecisionTable.class, "/com/americanexpress/unify/decision_table/decision_table_event_match_fired.json");
      JDocument.loadDocumentModel("decision_table_event_match_fired", json);
      json = BaseUtils.getResourceAsString(DecisionTable.class, "/com/americanexpress/unify/decision_table/decision_table_event_rules_loaded.json");
      JDocument.loadDocumentModel("decision_table_event_rules_loaded", json);
    }

    // init jexl engine
    // new set being created so that clients cannot change the value if they hold on to the conf reference
    DecisionTable.allowedClasses = (conf.allowedClasses == null) ? new HashSet<>() : new HashSet<>(conf.allowedClasses);
    JexlScriptClassPermissions sp = new JexlScriptClassPermissions(allowedClasses);

    {
      JexlBuilder builder = new JexlBuilder();
      builder.strict(true).cache(-1).permissions(sp);
      jexlEngine = builder.create();
    }

    // init default validation policy
    DecisionTable.defaultRowValidationPolicy = (conf.defaultRowValidationPolicy != null) ? conf.defaultRowValidationPolicy : RowValidationPolicy.STRICT;
  }

  public static RowValidationPolicy getDefaultRowValidationPolicy() {
    return defaultRowValidationPolicy;
  }

  // package protected
  DecisionTable(String name, MatchPolicy matchPolicy, NoMatchPolicy noMatchPolicy) {
    this.name = name;
    this.matchPolicy = matchPolicy;
    this.noMatchPolicy = noMatchPolicy;
  }

  public static void close() {
    // close everything. should be called in a single threaded context
    logger.info("Closing decision table expiry cache");
    IdleExpiryCacheFactory.close(getCacheName("decision_table_cache"));

    logger.info("Closing invokables expiry cache");
    IdleExpiryCacheFactory.close(getCacheName("decision_table_invokable_cache"));

    logger.info("Closing scripts expiry cache");
    IdleExpiryCacheFactory.close(getCacheName("decision_table_jexl_cache"));
  }

  /**
   * @deprecated Use validate(String) method instead.
   */
  public static void validate(String name, String json) {
    // try loading the decision table
    // will throw an exception if the decision table is not valid
    new JsonReader().getDecisionTableFromJson(name, json);
  }

  public static void validate(String json) {
    // try loading the decision table
    // will throw an exception is the decision table is not valid
    new JsonReader().getDecisionTableFromJson("", json);
  }

  public static DecisionTable fromJson(String resourcePath) {
    IdleExpiryCache<DecisionTable> cache = IdleExpiryCacheFactory.instanceOf(getCacheName("decision_table_cache"));
    DecisionTable dt = cache.get(resourcePath);
    if (dt == null) {
      try {
        rlock.lock();
        dt = cache.get(resourcePath);
        if (dt == null) {
          dt = new JsonReader().loadDecisionTableFromResourcePath(resourcePath);
          cache.put(resourcePath, dt);
          dt.invokeEventHandler(EventType.RULES_LOADED);
        }
      }
      finally {
        rlock.unlock();
      }
    }

    return dt;
  }

  /**
   * @deprecated Use fromJsonString(String, string) method instead.
   */
  public static DecisionTable fromJson(String decisionTableName, String json) {
    IdleExpiryCache<DecisionTable> cache = IdleExpiryCacheFactory.instanceOf(getCacheName("decision_table_cache"));
    DecisionTable dt = cache.get(decisionTableName);
    if (dt == null) {
      try {
        rlock.lock();
        dt = cache.get(decisionTableName);
        if (dt == null) {
          dt = new JsonReader().getDecisionTableFromJson(decisionTableName, json);
          cache.put(decisionTableName, dt);
          dt.invokeEventHandler(EventType.RULES_LOADED);
        }
      }
      finally {
        rlock.unlock();
      }
    }

    return dt;
  }

  public static DecisionTable fromJsonString(String decisionTableName, String json) {
    // we do not use the cache for this. It is left up to the clients to cache the returned decision table object
    DecisionTable dt = new JsonReader().getDecisionTableFromJson(decisionTableName, json);
    dt.invokeEventHandler(EventType.RULES_LOADED);
    return dt;
  }

  public static DecisionTable fromExcel(String resourcePath) {
    IdleExpiryCache<DecisionTable> cache = IdleExpiryCacheFactory.instanceOf(getCacheName("decision_table_cache"));
    DecisionTable dt = cache.get(resourcePath);
    if (dt == null) {
      try {
        rlock.lock();
        dt = cache.get(resourcePath);
        if (dt == null) {
          dt = new ExcelReader().loadDecisionTableFromResourcePath(resourcePath);
          cache.put(resourcePath, dt);
        }
      }
      finally {
        rlock.unlock();
      }
    }

    return dt;
  }

  public static void unload(String decisionTableName) {
    IdleExpiryCache<DecisionTable> cache = IdleExpiryCacheFactory.instanceOf(getCacheName("decision_table_cache"));
    try {
      rlock.lock();
      cache.remove(decisionTableName);
    }
    finally {
      rlock.unlock();
    }
  }

  // package protected
  void addEvalColumn(DTColumn c) {
    evalColumns.put(c.getName(), c);
  }

  // package protected
  void addRetColumn(DTColumn c) {
    retColumns.put(c.getName(), c);
  }

  // package protected
  void addRows(List<DTRow> dtRows) {
    this.dtRows = dtRows;
  }

  public List<MatchedRow> evaluate(Map<String, String> values, Object input) {
    List<MatchedRow> listOfResults = new ArrayList<>();
    boolean isMatch = false;
    int numRows = dtRows.size();
    int i = 0;
    for (DTRow dtRow : dtRows) {
      if (i == (numRows - 1)) {
        // we are at the last row which is the default row in case of return default policy or empty row in case of return none policy
        if (isMatch == false) {
          // no match has yet been found. What we return depends on the no match policy
          if (noMatchPolicy == NoMatchPolicy.RETURN_DEFAULT) {
            Map<String, DTCell> dtCells = dtRow.getRetDTCells();
            Map<String, RetDTCell> retDTCells = processRetDTCells(dtCells, values, input);
            listOfResults.add(new MatchedRow(retDTCells, getRuleId(dtRow, i + 1), getComments(dtRow), i + 1));
          }
          else {
            // nothing to do
          }
        }
        else {
          // nothing to do as no match policy is not applicable here
        }
      }
      else {
        boolean b = evaluateRow(dtRow, values, input);
        if (b == true) {
          isMatch = true;
          Map<String, DTCell> dtCells = dtRow.getRetDTCells();
          Map<String, RetDTCell> retDTCells = processRetDTCells(dtCells, values, input);
          listOfResults.add(new MatchedRow(retDTCells, getRuleId(dtRow, i + 1), getComments(dtRow), i + 1));
          if (matchPolicy == MatchPolicy.FIRST_MATCH) {
            break;
          }
        }
      }

      i++;
    }

    invokeEventHandler(EventType.MATCH_FIRED, values, listOfResults);

    return listOfResults;
  }

  private String getRuleId(DTRow dtRow, int rowNum) {
    DTCell ruleIdCell = dtRow.getRuleIdCell();
    if (ruleIdCell != null) {
      return ruleIdCell.getValue();
    }
    else {
      return rowNum + "";
    }
  }

  private String getComments(DTRow dtRow) {
    DTCell cell = dtRow.getCommentsCell();
    if (cell != null) {
      return cell.getValue();
    }
    else {
      return "";
    }
  }

  @SuppressWarnings("unchecked")
  private void invokeEventHandler(EventType eventType, Object... params) {
    if (eventHandler == null) {
      return;
    }

    Document d = null;
    try {
      switch (eventType) {
        case MATCH_FIRED: {
          Map<String, String> values = (Map<String, String>)params[0];
          List<MatchedRow> listOfResults = (List<MatchedRow>)params[1];
          d = getMatchFiredEvent(values, listOfResults);
          break;
        }

        case RULES_LOADED: {
          d = getRulesLoadedEvent();
          break;
        }
      }
    }
    catch (Exception e) {
      String s = MessageFormat.format("Error encountered while creating decision table event, table name -> {0}, exception message -> {1}, stack trace -> {2}",
                                      name, e.getMessage(), BaseUtils.getStackTrace(e));
      logger.error(s);
      throw new UnifyException(new ErrorTuple("error", s));
    }

    // exception handling is the responsibility of the client
    eventHandler.invoke(this, eventType, d);
  }

  private List<String> getSortedList(Map<String, ?> map) {
    Set<String> keySet = map.keySet();
    List<String> keys = new ArrayList<>(keySet);
    Collections.sort(keys);
    return keys;
  }

  private Document getMatchFiredEvent(Map<String, String> values, List<MatchedRow> listOfResults) {
    Document d = new JDocument("decision_table_event_match_fired", null);
    d.setString("decision_table_event_match_fired$.decision_table_event.system_name", systemName);
    d.setString("decision_table_event_match_fired$.decision_table_event.event_name", EventType.MATCH_FIRED.toString());
    d.setString("decision_table_event_match_fired$.decision_table_event.table_name", name);
    d.setInteger("decision_table_event_match_fired$.decision_table_event.table_size", dtRows.size());
    d.setString("decision_table_event_match_fired$.decision_table_event.timestamp", BaseUtils.fromInstant(Instant.now(), UTC_TS_FMT, "UTC"));

    {
      // set input values
      List<String> keys = getSortedList(values);
      int index = 0;
      for (String key : keys) {
        DTColumn col = evalColumns.get(key);
        if (col == null) {
          // this is a safety check as people may pass in a value which is not a column in the decision table
          continue;
        }
        d.setString("decision_table_event_match_fired$.decision_table_event.input[%].col_name", key, index + "");
        d.setString("decision_table_event_match_fired$.decision_table_event.input[%].col_type", evalColumns.get(key).getDataType().toString(), index + "");
        d.setString("decision_table_event_match_fired$.decision_table_event.input[%].value", values.get(key), index + "");
        index++;
      }
    }

    {
      // set matched rows
      for (int i = 0; i < listOfResults.size(); i++) {
        MatchedRow r = listOfResults.get(i);
        d.setString("decision_table_event_match_fired$.decision_table_event.result[%].rule_id", r.getRuleId(), i + "");
        d.setString("decision_table_event_match_fired$.decision_table_event.result[%].comments", r.getComments(), i + "");
        d.setInteger("decision_table_event_match_fired$.decision_table_event.result[%].row_num", r.getRowNum(), i + "");
        List<String> keys = getSortedList(r.getMap());
        int index = 0;
        for (String key : keys) {
          d.setString("decision_table_event_match_fired$.decision_table_event.result[%].values[%].col_name", key, i + "", index + "");
          d.setString("decision_table_event_match_fired$.decision_table_event.result[%].values[%].col_type", retColumns.get(key).getDataType().toString(), i + "", index + "");
          Object value = r.get(key).getValue();
          if (value == null) {
            d.setString("decision_table_event_match_fired$.decision_table_event.result[%].values[%].value", null, i + "", index + "");
          }
          else {
            d.setString("decision_table_event_match_fired$.decision_table_event.result[%].values[%].value", value.toString(), i + "", index + "");
          }
          index++;
        }
      }
    }

    return d;
  }

  public Document getRulesLoadedEvent() {
    Document d = new JDocument("decision_table_event_rules_loaded", null);
    d.setString("decision_table_event_rules_loaded$.decision_table_event.system_name", systemName);
    d.setString("decision_table_event_rules_loaded$.decision_table_event.event_name", EventType.RULES_LOADED.toString());
    d.setString("decision_table_event_rules_loaded$.decision_table_event.table_name", name);

    int size = (noMatchPolicy == NoMatchPolicy.RETURN_DEFAULT) ? dtRows.size() : dtRows.size() - 1;

    d.setInteger("decision_table_event_rules_loaded$.decision_table_event.table_size", size);
    d.setString("decision_table_event_rules_loaded$.decision_table_event.timestamp", BaseUtils.fromInstant(Instant.now(), UTC_TS_FMT, "UTC"));

    {
      for (int i = 0; i < size; i++) {
        DTRow row = dtRows.get(i);

        // get rule id
        DTCell ruleIdCell = row.getRuleIdCell();
        String ruleId = (ruleIdCell == null) ? (i + 1) + "" : ruleIdCell.getValue();

        // get comment
        DTCell commentsCell = row.getCommentsCell();
        String comments = (commentsCell == null) ? "" : commentsCell.getValue();

        d.setString("decision_table_event_rules_loaded$.decision_table_event.rules[%].rule_id", ruleId, i + "");
        d.setString("decision_table_event_rules_loaded$.decision_table_event.rules[%].comments", comments, i + "");
      }
    }

    return d;
  }

  private Map<String, RetDTCell> processRetDTCells(Map<String, DTCell> dtCells, Map<String, String> values, Object input) {
    Map<String, RetDTCell> mapCells = new HashMap<>();
    Set<String> keys = dtCells.keySet();
    for (String key : keys) {
      DTCell dtCell = dtCells.get(key);
      String colName = dtCell.getColumnName();
      DataType dataType = retColumns.get(dtCell.getColumnName()).getDataType();
      Object value = getRetCellValue(dtCell, values, input);
      RetDTCell retCell = new RetDTCell(colName, dataType, value);
      mapCells.put(colName, retCell);
    }
    return mapCells;
  }

  private Object getRetCellValue(DTCell dtCell, Map<String, String> values, Object input) {
    String s = dtCell.getValue();
    Object value = null;
    DataType dataType = retColumns.get(dtCell.getColumnName()).getDataType();

    if (s.isEmpty() == false) {
      if (s.charAt(0) == '#') {
        value = processInvokable(s.substring(1).trim(), values, input);
      }
      else if (s.startsWith("\\#")) {
        value = getRetCellValue(s.substring(1), dataType);
      }
      else if (s.charAt(0) == '?') {
        value = processScript(s.substring(1).trim(), values);
      }
      else if (s.startsWith("\\?")) {
        value = getRetCellValue(s.substring(1), dataType);
      }
      else {
        value = getRetCellValue(s, dataType);
      }
    }
    else {
      value = getRetCellValue(s, dataType);
    }

    return value;
  }

  private Object getRetCellValue(String s, DataType dataType) {
    Object value = null;

    switch (dataType) {
      case BOOLEAN:
        if (s.isEmpty()) {
          value = null;
        }
        else {
          value = Boolean.valueOf(s);
        }
        break;

      case DOUBLE:
        if (s.isEmpty()) {
          value = null;
        }
        else {
          value = Double.valueOf(s);
        }
        break;

      case BIGDECIMAL:
        if (s.isEmpty()) {
          value = null;
        }
        else {
          value = new BigDecimal(s);
        }
        break;

      case INTEGER:
        if (s.isEmpty()) {
          value = null;
        }
        else {
          value = Integer.valueOf(s);
        }
        break;

      case LONG:
        if (s.isEmpty()) {
          value = null;
        }
        else {
          value = Long.parseLong(s);
        }
        break;

      case STRING:
        value = s;
        break;
    }

    return value;
  }

  private Object processInvokable(String methodName, Map<String, String> values, Object input) {
    IdleExpiryCache<Object> cache = IdleExpiryCacheFactory.instanceOf(getCacheName("decision_table_invokable_cache"));
    Object ret = null;
    try {
      int pos = methodName.lastIndexOf('.');
      String className = methodName.substring(0, pos);
      Class<?> c = Class.forName(className);
      Object o = cache.get(methodName);
      if (o == null) {
        o = c.getDeclaredMethod(methodName.substring(pos + 1), Map.class, Object.class);
        cache.put(methodName, o);
      }
      Method m = (Method)o;
      Object instance = c.getDeclaredConstructor().newInstance();
      ret = m.invoke(instance, values, input);
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException |
           InvocationTargetException e) {
      // should never happen
    }
    if (ret == null) {
      throw new UnifyException(new ErrorTuple("error", "Null return value is not allowed to be returned"));
    }
    return ret;
  }

  private Object processScript(String script, Map<String, String> values) {
    Object ret = null;
    IdleExpiryCache<JexlScript> cache = IdleExpiryCacheFactory.instanceOf(getCacheName("decision_table_jexl_cache"));
    JexlScript jexlScript = cache.get(script);
    if (jexlScript == null) {
      jexlScript = jexlEngine.createScript(script);
      cache.put(script, jexlScript);
    }

    JexlContext context = new MapContext();
    values.forEach(context::set);
    ret = jexlScript.execute(context);
    return ret;
  }

  public List<MatchedRow> evaluate(Map<String, String> values) {
    return evaluate(values, null);
  }

  private boolean evaluateRow(DTRow dtRow, Map<String, String> values, Object input) {
    boolean b = true;
    List<DTCell> dtCells = dtRow.getEvalDTCellsList();

    for (DTCell dtCell : dtCells) {
      b = evaluateCell(dtCell, values, input);
      if (b == false) {
        break;
      }
    }

    return b;
  }

  private boolean evaluateCell(DTCell dtCell, Map<String, String> values, Object input) {
    boolean b = true;

    String colName = dtCell.getColumnName();
    String value = values.get(colName);

    if (value == null) {
      return false;
    }

    DTColumn col = evalColumns.get(colName);
    DataType dataType = col.getDataType();
    switch (dataType) {
      case LONG:
      case INTEGER:
        b = evaluateLong(value, dtCell, values, input);
        break;

      case DOUBLE:
        b = evaluateDouble(value, dtCell, values, input);
        break;

      case BIGDECIMAL:
        b = evaluateDecimal(value, dtCell, values, input);
        break;

      case STRING:
        b = evaluateString(value, dtCell, values, input);
        break;

      case BOOLEAN:
        b = evaluateBoolean(value, dtCell, values, input);
        break;
    }

    return b;
  }

  static String[] getTrimmedValues(String values) {
    List<String> list = new ArrayList<>();

    int index = 0;
    int fromIndex = 0;
    String value = "";
    boolean bBreakWhile = false;

    while (true) {
      boolean bContinueWhile = false;

      index = values.indexOf(',', fromIndex);
      fromIndex = 0;
      switch (index) {
        case 0:
          values = values.substring(1);
          break;

        case -1:
          // comma not found
          value = values;
          list.add(value.trim());
          bBreakWhile = true;
          break;

        default:
          if (values.charAt(index - 1) == '\\') {
            String s = values.substring(0, index - 1);
            values = s + values.substring(index);
            fromIndex = index;
            bContinueWhile = true;
            break;
          }
          value = values.substring(0, index);
          values = values.substring(index + 1);
          break;
      }

      if (bBreakWhile == true) {
        break;
      }

      if (bContinueWhile == true) {
        continue;
      }

      list.add(value.trim());
    }
    String[] ret = new String[list.size()];
    return list.toArray(ret);
  }

  private Object[] getTypedValues(String[] svals, Class<?> clazz, Map<String, String> values, Object input) {
    Object[] ovals = null;

    if (clazz == Long.class) {
      Long[] lvals = new Long[svals.length];
      for (int i = 0; i < lvals.length; i++) {
        if (svals[i].charAt(0) == '#') {
          lvals[i] = (Long)processInvokable(svals[i].substring(1).trim(), values, input);
        }
        else {
          lvals[i] = Long.parseLong(svals[i]);
        }
      }
      ovals = lvals;
    }
    else if (clazz == Integer.class) {
      Integer[] ivals = new Integer[svals.length];
      for (int i = 0; i < ivals.length; i++) {
        if (svals[i].charAt(0) == '#') {
          ivals[i] = (Integer)processInvokable(svals[i].substring(1).trim(), values, input);
        }
        else {
          ivals[i] = Integer.valueOf(svals[i]);
        }
      }
      ovals = ivals;
    }
    else if (clazz == Double.class) {
      Double[] dvals = new Double[svals.length];
      for (int i = 0; i < dvals.length; i++) {
        if (svals[i].charAt(0) == '#') {
          dvals[i] = (Double)processInvokable(svals[i].substring(1).trim(), values, input);
        }
        else {
          dvals[i] = Double.valueOf(svals[i]);
        }
      }
      ovals = dvals;
    }
    else if (clazz == BigDecimal.class) {
      BigDecimal[] bdVals = new BigDecimal[svals.length];
      for (int i = 0; i < bdVals.length; i++) {
        if (svals[i].charAt(0) == '#') {
          bdVals[i] = (BigDecimal)processInvokable(svals[i].substring(1).trim(), values, input);
        }
        else {
          bdVals[i] = new BigDecimal(svals[i]);
        }
      }
      ovals = bdVals;
    }
    else if (clazz == String.class) {
      String[] svalsTemp = new String[svals.length];
      for (int i = 0; i < svalsTemp.length; i++) {
        if (svals[i].charAt(0) == '#') {
          svalsTemp[i] = (String)processInvokable(svals[i].substring(1).trim(), values, input);
        }
        else {
          svalsTemp[i] = svals[i];
        }
      }
      ovals = svalsTemp;
    }
    else {
      throw new UnifyException(new ErrorTuple("error", "Unknown data type -> " + clazz));
    }

    return ovals;
  }

  private Object[] getTypedValues(String[] svals, Class<?> clazz) {
    Object[] ovals = null;

    if (clazz == Long.class) {
      Long[] lvals = new Long[svals.length];
      for (int i = 0; i < lvals.length; i++) {
        lvals[i] = Long.parseLong(svals[i]);
      }
      ovals = lvals;
    }
    else if (clazz == Integer.class) {
      Integer[] ivals = new Integer[svals.length];
      for (int i = 0; i < ivals.length; i++) {
        ivals[i] = Integer.valueOf(svals[i]);
      }
      ovals = ivals;
    }
    else if (clazz == Double.class) {
      Double[] dvals = new Double[svals.length];
      for (int i = 0; i < dvals.length; i++) {
        dvals[i] = Double.valueOf(svals[i]);
      }
      ovals = dvals;
    }
    else if (clazz == BigDecimal.class) {
      BigDecimal[] bdVals = new BigDecimal[svals.length];
      for (int i = 0; i < bdVals.length; i++) {
        bdVals[i] = new BigDecimal(svals[i]);
      }
      ovals = bdVals;
    }
    else if (clazz == String.class) {
      ovals = Arrays.copyOf(svals, svals.length);
    }
    else {
      throw new UnifyException(new ErrorTuple("error", "Unknown data type -> " + clazz));
    }

    return ovals;
  }

  private boolean evaluateLong(String value, DTCell c, Map<String, String> values, Object input) {
    boolean b = false;

    while (true) {
      switch (c.getOprType()) {
        case ALL_CONTAINED_IN: {
          b = true;
          Long[] inputVals = (Long[])getTypedValues(getTrimmedValues(value), Long.class);
          Long[] lvals = (Long[])getTypedValues(getTrimmedValues(c.getValue()), Long.class, values, input);
          for (int i = 0; i < inputVals.length; i++) {
            long val1 = inputVals[i];
            boolean isFound = false;
            for (int j = 0; j < lvals.length; j++) {
              if (val1 == lvals[j]) {
                isFound = true;
                break;
              }
            }
            if (isFound == false) {
              b = false;
              break;
            }
          }
        }
        break;

        case ALL_EQUAL: {
          b = true;
          Long[] inputVals = (Long[])getTypedValues(getTrimmedValues(value), Long.class);
          Long[] evals = (Long[])getTypedValues(getTrimmedValues(c.getValue()), Long.class, values, input);

          inputVals = Arrays.stream(inputVals).distinct().toArray(Long[]::new);
          Arrays.sort(inputVals);

          evals = Arrays.stream(evals).distinct().toArray(Long[]::new);
          Arrays.sort(evals);

          if (inputVals.length != evals.length) {
            b = false;
            break;
          }

          for (int i = 0; i < inputVals.length; i++) {
            if (inputVals[i].equals(evals[i]) == false) {
              b = false;
              break;
            }
          }
        }
        break;

        case CONTAINS_ALL: {
          b = true;
          Long[] inputVals = (Long[])getTypedValues(getTrimmedValues(value), Long.class);
          Long[] evals = (Long[])getTypedValues(getTrimmedValues(c.getValue()), Long.class, values, input);
          for (long val1 : evals) {
            boolean isFound = false;
            for (Long inputVal : inputVals) {
              if (val1 == inputVal) {
                isFound = true;
                break;
              }
            }
            if (isFound == false) {
              b = false;
              break;
            }
          }
        }
        break;

        case NOT_CONTAINS_ALL: {
          b = true;
          Long[] inputVals = (Long[])getTypedValues(getTrimmedValues(value), Long.class);
          Long[] evals = (Long[])getTypedValues(getTrimmedValues(c.getValue()), Long.class, values, input);

          for (long val1 : evals) {
            for (Long inputVal : inputVals) {
              if (val1 == inputVal) {
                b = false;
                break;
              }
            }
            if (b == false) {
              break;
            }
          }
        }
        break;

        case NOT_ALL_CONTAINED_IN: {
          b = true;
          Long[] inputVals = (Long[])getTypedValues(getTrimmedValues(value), Long.class);
          Long[] lvals = (Long[])getTypedValues(getTrimmedValues(c.getValue()), Long.class, values, input);
          for (long val1 : inputVals) {
            for (Long lval : lvals) {
              if (val1 == lval) {
                b = false;
                break;
              }
            }
            if (b == false) {
              break;
            }
          }
        }
        break;

        case ANY_CONTAINED_IN: {
          Long[] inputVals = (Long[])getTypedValues(getTrimmedValues(value), Long.class);
          Long[] lvals = (Long[])getTypedValues(getTrimmedValues(c.getValue()), Long.class, values, input);
          for (long val1 : inputVals) {
            for (Long lval : lvals) {
              if (val1 == lval) {
                b = true;
                break;
              }
            }
            if (b == true) {
              break;
            }
          }
        }
        break;

        case NOT_ANY_CONTAINED_IN: {
          b = true;
          Long[] inputVals = (Long[])getTypedValues(getTrimmedValues(value), Long.class);
          Long[] lvals = (Long[])getTypedValues(getTrimmedValues(c.getValue()), Long.class, values, input);
          boolean isPresent = false;
          for (long val1 : inputVals) {
            for (Long lval : lvals) {
              if (val1 == lval) {
                isPresent = true;
                break;
              }
            }
            if (isPresent == true) {
              b = false;
              break;
            }
          }
        }
        break;

        case IN: {
          long val1 = Long.parseLong(value);
          Long[] lvals = (Long[])getTypedValues(getTrimmedValues(c.getValue()), Long.class, values, input);

          for (Long lval : lvals) {
            if (val1 == lval) {
              b = true;
              break;
            }
          }
        }
        break;

        case NOT_IN: {
          long val1 = Long.parseLong(value);
          Long[] lvals = (Long[])getTypedValues(getTrimmedValues(c.getValue()), Long.class, values, input);

          for (Long lval : lvals) {
            if (val1 == lval) {
              b = true;
              break;
            }
          }
          b = !b;
        }
        break;

        case MATCHES_REGEX: {
          long val1 = Long.valueOf(value);
          b = String.valueOf(val1).matches(c.getValue());
        }
        break;

        default: {
          long val1 = Long.parseLong(value);
          long val2 = 0;

          String s = c.getValue();
          if (s.charAt(0) == '#') {
            val2 = (Long)processInvokable(s.substring(1).trim(), values, input);
          }
          else {
            val2 = Long.parseLong(c.getValue());
          }

          switch (c.getOprType()) {
            case EQ:
              if (val1 == val2) {
                b = true;
              }
              break;

            case NOT_EQ:
              if (val1 != val2) {
                b = true;
              }
              break;

            case GT:
              if (val1 > val2) {
                b = true;
              }
              break;

            case LT:
              if (val1 < val2) {
                b = true;
              }
              break;

            case GT_EQ:
              if (val1 >= val2) {
                b = true;
              }
              break;

            case LT_EQ:
              if (val1 <= val2) {
                b = true;
              }
              break;

            default:
              throw new UnifyException(new ErrorTuple("error", "This scenario should never happen"));
          }
        }
        break;
      }
      break;
    }

    return b;
  }

  private boolean evaluateDouble(String value, DTCell c, Map<String, String> values, Object input) {
    boolean b = false;

    while (true) {
      switch (c.getOprType()) {
        case ALL_CONTAINED_IN: {
          b = true;
          Double[] inputVals = (Double[])getTypedValues(getTrimmedValues(value), Double.class);
          Double[] evals = (Double[])getTypedValues(getTrimmedValues(c.getValue()), Double.class, values, input);
          for (int i = 0; i < inputVals.length; i++) {
            double val1 = inputVals[i];
            boolean isFound = false;
            for (int j = 0; j < evals.length; j++) {
              if (Double.compare(val1, evals[j]) == 0) {
                isFound = true;
                break;
              }
            }
            if (isFound == false) {
              b = false;
              break;
            }
          }
        }
        break;

        case ALL_EQUAL: {
          b = true;
          Double[] inputVals = (Double[])getTypedValues(getTrimmedValues(value), Double.class);
          Double[] evals = (Double[])getTypedValues(getTrimmedValues(c.getValue()), Double.class, values, input);

          inputVals = Arrays.stream(inputVals).distinct().toArray(size -> new Double[size]);
          Arrays.sort(inputVals);

          evals = Arrays.stream(evals).distinct().toArray(size -> new Double[size]);
          Arrays.sort(evals);

          if (inputVals.length != evals.length) {
            b = false;
            break;
          }

          for (int i = 0; i < inputVals.length; i++) {
            if (Double.compare(inputVals[i], evals[i]) != 0) {
              b = false;
              break;
            }
          }
        }
        break;

        case CONTAINS_ALL: {
          b = true;
          Double[] inputVals = (Double[])getTypedValues(getTrimmedValues(value), Double.class);
          Double[] evals = (Double[])getTypedValues(getTrimmedValues(c.getValue()), Double.class, values, input);
          for (int i = 0; i < evals.length; i++) {
            Double val1 = evals[i];
            boolean isFound = false;
            for (Double inputVal : inputVals) {
              if (Double.compare(val1, inputVal) == 0) {
                isFound = true;
                break;
              }
            }
            if (isFound == false) {
              b = false;
              break;
            }
          }
        }
        break;

        case NOT_CONTAINS_ALL: {
          b = true;
          Double[] inputVals = (Double[])getTypedValues(getTrimmedValues(value), Double.class);
          Double[] evals = (Double[])getTypedValues(getTrimmedValues(c.getValue()), Double.class, values, input);
          for (Double val1 : evals) {
            for (Double inputVal : inputVals) {
              if (Double.compare(val1, inputVal) == 0) {
                b = false;
                break;
              }
            }
            if (b == false) {
              break;
            }
          }
        }
        break;

        case NOT_ALL_CONTAINED_IN: {
          b = true;
          Double[] inputVals = (Double[])getTypedValues(getTrimmedValues(value), Double.class);
          Double[] evals = (Double[])getTypedValues(getTrimmedValues(c.getValue()), Double.class, values, input);
          for (double val1 : inputVals) {
            for (Double eval : evals) {
              if (val1 == eval) {
                b = false;
                break;
              }
            }
            if (b == false) {
              break;
            }
          }
        }
        break;

        case ANY_CONTAINED_IN: {
          Double[] inputVals = (Double[])getTypedValues(getTrimmedValues(value), Double.class);
          Double[] evals = (Double[])getTypedValues(getTrimmedValues(c.getValue()), Double.class, values, input);
          for (int i = 0; i < inputVals.length; i++) {
            double val1 = inputVals[i];
            for (int j = 0; j < evals.length; j++) {
              if (val1 == evals[j]) {
                b = true;
                break;
              }
            }
            if (b == true) {
              break;
            }
          }
        }
        break;

        case NOT_ANY_CONTAINED_IN: {
          b = true;
          Double[] inputVals = (Double[])getTypedValues(getTrimmedValues(value), Double.class);
          Double[] evals = (Double[])getTypedValues(getTrimmedValues(c.getValue()), Double.class, values, input);
          boolean isPresent = false;
          for (double val1 : inputVals) {
            for (Double eval : evals) {
              if (val1 == eval) {
                isPresent = true;
                break;
              }
            }
            if (isPresent == true) {
              b = false;
              break;
            }
          }
        }
        break;

        case IN: {
          double val1 = Double.parseDouble(value);
          Double[] dvals = (Double[])getTypedValues(getTrimmedValues(c.getValue()), Double.class, values, input);

          for (int i = 0; i < dvals.length; i++) {
            if (val1 == dvals[i]) {
              b = true;
              break;
            }
          }
        }
        break;

        case NOT_IN: {
          double val1 = Double.parseDouble(value);
          Double[] dvals = (Double[])getTypedValues(getTrimmedValues(c.getValue()), Double.class, values, input);

          for (Double dval : dvals) {
            if (val1 == dval) {
              b = true;
              break;
            }
          }
          b = !b;
        }
        break;

        case MATCHES_REGEX: {
          double val1 = Double.valueOf(value);
          b = String.valueOf(val1).matches(c.getValue());
        }
        break;

        default: {
          double val1 = Double.parseDouble(value);
          double val2 = 0;

          String s = c.getValue();
          if (s.charAt(0) == '#') {
            val2 = (Double)processInvokable(s.substring(1).trim(), values, input);
          }
          else {
            val2 = Double.parseDouble(c.getValue());
          }

          switch (c.getOprType()) {
            case EQ:
              if (val1 == val2) {
                b = true;
              }
              break;

            case NOT_EQ:
              if (val1 != val2) {
                b = true;
              }
              break;

            case GT:
              if (val1 > val2) {
                b = true;
              }
              break;

            case LT:
              if (val1 < val2) {
                b = true;
              }
              break;

            case GT_EQ:
              if (val1 >= val2) {
                b = true;
              }
              break;

            case LT_EQ:
              if (val1 <= val2) {
                b = true;
              }
              break;

            default:
              throw new UnifyException(new ErrorTuple("error", "This scenario should never happen"));
          }
        }
        break;
      }
      break;
    }

    return b;
  }

  private boolean evaluateDecimal(String value, DTCell c, Map<String, String> values, Object input) {
    boolean b = false;

    while (true) {
      switch (c.getOprType()) {
        case ALL_CONTAINED_IN: {
          b = true;
          BigDecimal[] inputVals = (BigDecimal[])getTypedValues(getTrimmedValues(value), BigDecimal.class);
          BigDecimal[] evals = (BigDecimal[])getTypedValues(getTrimmedValues(c.getValue()), BigDecimal.class, values, input);
          for (BigDecimal val1 : inputVals) {
            boolean isFound = false;
            for (BigDecimal eval : evals) {
              if (val1.compareTo(eval) == 0) {
                isFound = true;
                break;
              }
            }
            if (isFound == false) {
              b = false;
              break;
            }
          }
        }
        break;

        case ALL_EQUAL: {
          b = true;
          BigDecimal[] inputVals = (BigDecimal[])getTypedValues(getTrimmedValues(value), BigDecimal.class);
          BigDecimal[] evals = (BigDecimal[])getTypedValues(getTrimmedValues(c.getValue()), BigDecimal.class, values, input);

          inputVals = Arrays.stream(inputVals).distinct().toArray(BigDecimal[]::new);
          Arrays.sort(inputVals);

          evals = Arrays.stream(evals).distinct().toArray(BigDecimal[]::new);
          Arrays.sort(evals);

          if (inputVals.length != evals.length) {
            b = false;
            break;
          }

          for (int i = 0; i < inputVals.length; i++) {
            if (inputVals[i].compareTo(evals[i]) != 0) {
              b = false;
              break;
            }
          }
        }
        break;

        case CONTAINS_ALL: {
          b = true;
          BigDecimal[] inputVals = (BigDecimal[])getTypedValues(getTrimmedValues(value), BigDecimal.class);
          BigDecimal[] evals = (BigDecimal[])getTypedValues(getTrimmedValues(c.getValue()), BigDecimal.class, values, input);
          for (BigDecimal val1 : evals) {
            boolean isFound = false;
            for (BigDecimal inputVal : inputVals) {
              if (val1.compareTo(inputVal) == 0) {
                isFound = true;
                break;
              }
            }
            if (isFound == false) {
              b = false;
              break;
            }
          }
        }
        break;

        case NOT_CONTAINS_ALL: {
          b = true;
          BigDecimal[] inputVals = (BigDecimal[])getTypedValues(getTrimmedValues(value), BigDecimal.class);
          BigDecimal[] evals = (BigDecimal[])getTypedValues(getTrimmedValues(c.getValue()), BigDecimal.class, values, input);
          for (BigDecimal val1 : evals) {
            for (BigDecimal inputVal : inputVals) {
              if (val1.compareTo(inputVal) == 0) {
                b = false;
                break;
              }
            }
            if (b == false) {
              break;
            }
          }
        }
        break;

        case NOT_ALL_CONTAINED_IN: {
          b = true;
          BigDecimal[] inputVals = (BigDecimal[])getTypedValues(getTrimmedValues(value), BigDecimal.class);
          BigDecimal[] evals = (BigDecimal[])getTypedValues(getTrimmedValues(c.getValue()), BigDecimal.class, values, input);
          for (BigDecimal val1 : inputVals) {
            for (BigDecimal eval : evals) {
              if (val1.compareTo(eval) == 0) {
                b = false;
                break;
              }
            }
            if (b == false) {
              break;
            }
          }
        }
        break;

        case ANY_CONTAINED_IN: {
          BigDecimal[] inputVals = (BigDecimal[])getTypedValues(getTrimmedValues(value), BigDecimal.class);
          BigDecimal[] evals = (BigDecimal[])getTypedValues(getTrimmedValues(c.getValue()), BigDecimal.class, values, input);
          for (BigDecimal val1 : inputVals) {
            for (BigDecimal eval : evals) {
              if (val1.compareTo(eval) == 0) {
                b = true;
                break;
              }
            }
            if (b == true) {
              break;
            }
          }
        }
        break;

        case NOT_ANY_CONTAINED_IN: {
          b = true;
          BigDecimal[] inputVals = (BigDecimal[])getTypedValues(getTrimmedValues(value), BigDecimal.class);
          BigDecimal[] evals = (BigDecimal[])getTypedValues(getTrimmedValues(c.getValue()), BigDecimal.class, values, input);
          boolean isPresent = false;
          for (BigDecimal val1 : inputVals) {
            for (BigDecimal eval : evals) {
              if (val1.compareTo(eval) == 0) {
                isPresent = true;
                break;
              }
            }
            if (isPresent == true) {
              b = false;
              break;
            }
          }
        }
        break;

        case IN: {
          BigDecimal val1 = new BigDecimal(value);
          BigDecimal[] bdVals = (BigDecimal[])getTypedValues(getTrimmedValues(c.getValue()), BigDecimal.class, values, input);

          for (BigDecimal bdVal : bdVals) {
            if (val1.compareTo(bdVal) == 0) {
              b = true;
              break;
            }
          }
        }
        break;

        case NOT_IN: {
          BigDecimal val1 = new BigDecimal(value);
          BigDecimal[] bdVals = (BigDecimal[])getTypedValues(getTrimmedValues(c.getValue()), BigDecimal.class, values, input);

          for (BigDecimal bdVal : bdVals) {
            if (val1.compareTo(bdVal) == 0) {
              b = true;
              break;
            }
          }
          b = !b;
        }
        break;

        case MATCHES_REGEX: {
          BigDecimal val1 = new BigDecimal(value);
          b = val1.toPlainString().matches(c.getValue());
        }
        break;

        default: {
          BigDecimal val1 = new BigDecimal(value);
          BigDecimal val2 = null;

          String s = c.getValue();
          if (s.charAt(0) == '#') {
            val2 = (BigDecimal)processInvokable(s.substring(1).trim(), values, input);
          }
          else {
            val2 = new BigDecimal(c.getValue());
          }

          int result = val1.compareTo(val2);

          switch (c.getOprType()) {
            case EQ:
              if (result == 0) {
                b = true;
              }
              break;

            case NOT_EQ:
              if (result != 0) {
                b = true;
              }
              break;

            case GT:
              if (result > 0) {
                b = true;
              }
              break;

            case LT:
              if (result < 0) {
                b = true;
              }
              break;

            case GT_EQ:
              if (result >= 0) {
                b = true;
              }
              break;

            case LT_EQ:
              if (result <= 0) {
                b = true;
              }
              break;

            default:
              throw new UnifyException(new ErrorTuple("error", "This scenario should never happen"));
          }
        }
        break;
      }
      break;
    }

    return b;
  }

  private boolean evaluateString(String value, DTCell c, Map<String, String> values, Object input) {
    boolean b = false;

    while (true) {
      switch (c.getOprType()) {
        case ALL_CONTAINED_IN: {
          b = true;
          String[] inputVals = (String[])getTypedValues(getTrimmedValues(value), String.class);
          String[] evals = (String[])getTypedValues(getTrimmedValues(c.getValue()), String.class, values, input);
          for (String val1 : inputVals) {
            if (BaseUtils.compareWithMany(val1, evals) == false) {
              b = false;
              break;
            }
          }
        }
        break;

        case ALL_EQUAL: {
          b = true;
          String[] inputVals = (String[])getTypedValues(getTrimmedValues(value), String.class);
          String[] evals = (String[])getTypedValues(getTrimmedValues(c.getValue()), String.class, values, input);

          inputVals = Arrays.stream(inputVals).distinct().toArray(String[]::new);
          Arrays.sort(inputVals);

          evals = Arrays.stream(evals).distinct().toArray(String[]::new);
          Arrays.sort(evals);

          if (inputVals.length != evals.length) {
            b = false;
            break;
          }

          for (int i = 0; i < inputVals.length; i++) {
            if (inputVals[i].equals(evals[i]) == false) {
              b = false;
              break;
            }
          }
        }
        break;

        case CONTAINS_ALL: {
          b = true;
          String[] inputVals = (String[])getTypedValues(getTrimmedValues(value), String.class);
          String[] evals = (String[])getTypedValues(getTrimmedValues(c.getValue()), String.class, values, input);
          for (String val1 : evals) {
            if (BaseUtils.compareWithMany(val1, inputVals) == false) {
              b = false;
              break;
            }
          }
        }
        break;

        case NOT_CONTAINS_ALL: {
          b = true;
          String[] inputVals = (String[])getTypedValues(getTrimmedValues(value), String.class);
          String[] evals = (String[])getTypedValues(getTrimmedValues(c.getValue()), String.class, values, input);
          for (String val1 : evals) {
            if (BaseUtils.compareWithMany(val1, inputVals) == true) {
              b = false;
              break;
            }
          }
        }
        break;

        case NOT_ALL_CONTAINED_IN: {
          b = true;
          String[] inputVals = (String[])getTypedValues(getTrimmedValues(value), String.class);
          String[] evals = (String[])getTypedValues(getTrimmedValues(c.getValue()), String.class, values, input);
          for (String val1 : inputVals) {
            if (BaseUtils.compareWithMany(val1, evals) == true) {
              b = false;
              break;
            }
          }
        }
        break;

        case ANY_CONTAINED_IN: {
          String[] inputVals = (String[])getTypedValues(getTrimmedValues(value), String.class);
          String[] evals = (String[])getTypedValues(getTrimmedValues(c.getValue()), String.class, values, input);
          for (String val1 : inputVals) {
            if (BaseUtils.compareWithMany(val1, evals)) {
              b = true;
              break;
            }
          }
        }
        break;

        case NOT_ANY_CONTAINED_IN: {
          String[] inputVals = (String[])getTypedValues(getTrimmedValues(value), String.class);
          String[] evals = (String[])getTypedValues(getTrimmedValues(c.getValue()), String.class, values, input);
          for (String val1 : inputVals) {
            if (BaseUtils.compareWithMany(val1, evals) == false) {
              b = true;
              break;
            }
          }
        }
        break;

        case IN: {
          String[] svals = (String[])getTypedValues(getTrimmedValues(c.getValue()), String.class, values, input);
          if (BaseUtils.compareWithMany(value, svals)) {
            b = true;
          }
        }
        break;

        case NOT_IN: {
          String[] svals = (String[])getTypedValues(getTrimmedValues(c.getValue()), String.class, values, input);

          if (BaseUtils.compareWithMany(value, svals)) {
            b = true;
          }
          b = !b;
        }
        break;

        case MATCHES_REGEX: {
          b = value.matches(c.getValue());
        }
        break;

        default: {
          String val2 = null;

          String s = c.getValue();
          if (s.charAt(0) == '#') {
            val2 = (String)processInvokable(s.substring(1).trim(), values, input);
          }
          else {
            val2 = c.getValue();
          }

          int result = value.compareTo(val2);

          switch (c.getOprType()) {
            case EQ:
              if (result == 0) {
                b = true;
              }
              break;

            case NOT_EQ:
              if (result != 0) {
                b = true;
              }
              break;

            case GT:
              if (result > 0) {
                b = true;
              }
              break;

            case LT:
              if (result < 0) {
                b = true;
              }
              break;

            case GT_EQ:
              if (result >= 0) {
                b = true;
              }
              break;

            case LT_EQ:
              if (result <= 0) {
                b = true;
              }
              break;

            default:
              throw new UnifyException(new ErrorTuple("error", "This scenario should never happen"));
          }
        }
        break;
      }
      break;
    }

    return b;
  }

  private boolean evaluateBoolean(String value, DTCell c, Map<String, String> values, Object input) {
    boolean b = false;
    boolean val1 = Boolean.parseBoolean(value);
    boolean val2 = false;

    String s = c.getValue();
    if (s.charAt(0) == '#') {
      val2 = (Boolean)processInvokable(s.substring(1).trim(), values, input);
    }
    else {
      val2 = Boolean.parseBoolean(c.getValue());
    }

    switch (c.getOprType()) {
      case EQ:
        if (val1 == val2) {
          b = true;
        }
        break;

      case NOT_EQ:
        if (val1 != val2) {
          b = true;
        }
        break;

      case GT:
      case LT:
      case GT_EQ:
      case LT_EQ:
      case MATCHES_REGEX:
        // nothing to do as it does not make sense in the context of a boolean
        break;

      default:
        throw new UnifyException(new ErrorTuple("error", "This scenario should never happen"));
    }

    return b;
  }

  public String getName() {
    return name;
  }

  public String getSystemName() {
    return systemName;
  }

  static String dedupeValue(String value, OperatorType oprType) {
    switch (oprType) {
      case EQ:
      case NOT_EQ:
      case GT:
      case LT:
      case GT_EQ:
      case LT_EQ:
      case MATCHES_REGEX:
        // nothing to do
        break;

      case IN:
      case NOT_IN:
      case ALL_CONTAINED_IN:
      case NOT_ALL_CONTAINED_IN:
      case ANY_CONTAINED_IN:
      case NOT_ANY_CONTAINED_IN:
      case CONTAINS_ALL:
      case NOT_CONTAINS_ALL:
      case ALL_EQUAL:
        value = dedupeValue(value);
        break;

      default:
        // this will not happen
        break;
    }

    return value;
  }

  static String dedupeValue(String value) {
    String[] values = getTrimmedValues(value);
    Set<String> valuesSet = new HashSet<>(Arrays.asList(values));
    String[] dedupedValues = valuesSet.toArray(new String[0]);
    return String.join(",", dedupedValues);
  }

  private static class JexlScriptClassPermissions implements JexlPermissions {

    private Set<Class<?>> allowedClasses = null;

    public JexlScriptClassPermissions(Set<Class<?>> allowedClasses) {
      this.allowedClasses = allowedClasses;
    }

    @Override
    public boolean allow(Class<?> clazz) {
      // Only allow the specified class
      return allowedClasses.contains(clazz);
    }

    @Override
    public boolean allow(Constructor<?> constructor) {
      return true;
    }

    @Override
    public boolean allow(Field field) {
      return true;
    }

    @Override
    public boolean allow(Method method) {
      return true;
    }

    @Override
    public boolean allow(Package aPackage) {
      return true;
    }

    @Override
    public JexlPermissions compose(String... strings) {
      return null;
    }

  }

}
