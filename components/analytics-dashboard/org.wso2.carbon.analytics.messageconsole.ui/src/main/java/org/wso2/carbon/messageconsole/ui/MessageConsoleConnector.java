package org.wso2.carbon.messageconsole.ui;

/*
* Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* WSO2 Inc. licenses this file to you under the Apache License,
* Version 2.0 (the "License"); you may not use this file except
* in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.axis2.AxisFault;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.analytics.messageconsole.stub.MessageConsoleStub;
import org.wso2.carbon.analytics.messageconsole.stub.beans.ColumnBean;
import org.wso2.carbon.analytics.messageconsole.stub.beans.EntityBean;
import org.wso2.carbon.analytics.messageconsole.stub.beans.RecordBean;
import org.wso2.carbon.analytics.messageconsole.stub.beans.RecordResultBean;
import org.wso2.carbon.analytics.messageconsole.stub.beans.TableBean;
import org.wso2.carbon.messageconsole.ui.beans.Column;
import org.wso2.carbon.messageconsole.ui.beans.Record;
import org.wso2.carbon.messageconsole.ui.beans.ResponseArbitraryField;
import org.wso2.carbon.messageconsole.ui.beans.ResponseArbitraryFieldColumn;
import org.wso2.carbon.messageconsole.ui.beans.ResponseRecord;
import org.wso2.carbon.messageconsole.ui.beans.ResponseResult;
import org.wso2.carbon.messageconsole.ui.beans.ResponseTable;
import org.wso2.carbon.messageconsole.ui.beans.TableSchemaColumn;
import org.wso2.carbon.messageconsole.ui.serializers.ResponseArbitraryFieldSerializer;
import org.wso2.carbon.messageconsole.ui.serializers.ResponseArbitraryFieldsSerializer;
import org.wso2.carbon.messageconsole.ui.serializers.ResponseRecordSerializer;
import org.wso2.carbon.messageconsole.ui.serializers.ResponseResultSerializer;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MessageConsoleConnector {

    private static final Log log = LogFactory.getLog(MessageConsoleConnector.class);
    private static final String MESSAGE_CONSOLE = "MessageConsole";
    private static final String OK = "OK";
    private static final String ERROR = "ERROR";

    public static final String RECORD_ID = "bam_unique_rec_id";
    public static final String TIMESTAMP = "bam_rec_timestamp";

    public static final int TYPE_LIST_RECORD = 1;
    public static final int TYPE_CREATE_RECORD = 2;
    public static final int TYPE_UPDATE_RECORD = 3;
    public static final int TYPE_DELETE_RECORD = 4;
    public static final int TYPE_TABLE_INFO = 5;

    public static final int TYPE_LIST_ARBITRARY_RECORD = 6;
    public static final int TYPE_CRATE_ARBITRARY_RECORD = 7;
    public static final int TYPE_UPDATE_ARBITRARY_RECORD = 8;
    public static final int TYPE_DELETE_ARBITRARY_RECORD = 9;
    public static final int TYPE_CREATE_TABLE = 10;

    private MessageConsoleStub stub;
    private static final GsonBuilder RESPONSE_RESULT_BUILDER = new GsonBuilder().registerTypeAdapter(ResponseResult.class,
                                                                                                     new ResponseResultSerializer());
    private static final GsonBuilder RESPONSE_RECORD_BUILDER = new GsonBuilder().registerTypeAdapter(ResponseRecord.class,
                                                                                                     new ResponseRecordSerializer());
    private static final GsonBuilder RESPONSE_ARBITRARY_FIELD_BUILDER = new GsonBuilder().
            registerTypeAdapter(ResponseArbitraryField.class, new ResponseArbitraryFieldsSerializer());
    private static final GsonBuilder RESPONSE_ARBITRARY_FIELD_COLUMN_BUILDER = new GsonBuilder().
            registerTypeAdapter(ResponseArbitraryFieldColumn.class, new ResponseArbitraryFieldSerializer());
    private static final Type TABLE_SCHEMA_TYPE = new TypeToken<List<TableSchemaColumn>>() {
    }.getType();


    public MessageConsoleConnector(ConfigurationContext configCtx, String backendServerURL, String cookie) {
        String serviceURL = backendServerURL + MESSAGE_CONSOLE;
        try {
            stub = new MessageConsoleStub(configCtx, serviceURL);
            ServiceClient client = stub._getServiceClient();
            Options options = client.getOptions();
            options.setManageSession(true);
            options.setProperty(org.apache.axis2.transport.http.HTTPConstants.COOKIE_STRING, cookie);
        } catch (AxisFault axisFault) {
            log.error("Unable to create MessageConsoleStub.", axisFault);
        }
    }

    public String[] getTableList() {
        String[] tableList = null;
        try {
            tableList = stub.listTables();
        } catch (Exception e) {
            log.error("Unable to get table list:" + e.getMessage(), e);
        }

        if (tableList == null) {
            if (log.isDebugEnabled()) {
                log.debug("Received an empty table name list!");
            }
            tableList = new String[0];
        }

        return tableList;
    }

    public String getRecords(String tableName, long timeFrom, long timeTo, int startIndex, int pageSize, String
            searchQuery) {

        if (log.isDebugEnabled()) {
            log.debug("Search Query: " + searchQuery);
            log.debug("timeFrom: " + timeFrom);
            log.debug("timeTo: " + timeTo);
            log.debug("Start Index: " + startIndex);
            log.debug("Page Size: " + pageSize);
        }
        ResponseResult responseResult = new ResponseResult();
        Gson gson = RESPONSE_RESULT_BUILDER.serializeNulls().create();
        try {
            RecordResultBean recordBeans = stub.getRecords(tableName, timeFrom, timeTo, startIndex, pageSize, searchQuery);
            responseResult.setResult(OK);
            List<Record> records = new ArrayList<>();
            if (recordBeans != null) {
                if (recordBeans.getRecords() != null) {
                    if (log.isDebugEnabled()) {
                        log.debug("Result size: " + recordBeans.getRecords().length);
                    }
                    for (RecordBean recordBean : recordBeans.getRecords()) {
                        Record record = getRecord(recordBean);
                        records.add(record);
                    }
                    responseResult.setRecords(records);
                }
                responseResult.setTotalRecordCount(recordBeans.getTotalResultCount());
            }
        } catch (Exception e) {
            log.error("Unable to get records for table:" + tableName, e);
            responseResult.setResult(ERROR);
            responseResult.setMessage(e.getMessage());
        }

        return gson.toJson(responseResult);
    }

    private Record getRecord(RecordBean recordBean) {
        Record record = new Record();
        if (recordBean != null) {
            List<Column> columns = new ArrayList<>(recordBean.getEntityBeans().length + 2);
            columns.add(new Column(RECORD_ID, recordBean.getRecordId()));
            columns.add(new Column(TIMESTAMP, String.valueOf(recordBean.getTimestamp())));
            for (EntityBean entityBean : recordBean.getEntityBeans()) {
                columns.add(new Column(entityBean.getColumnName(), entityBean.getValue()));
            }
            record.setColumns(columns);
        }
        return record;
    }

    public String getTableInfo(String tableName) {

        Gson gson = new GsonBuilder().serializeNulls().create();
        TableBean tableInfo;
        ResponseTable table = new ResponseTable();
        try {
            tableInfo = stub.getTableInfo(tableName);

            table.setName(tableInfo.getName());
            List<ResponseTable.Column> columns = new ArrayList<>();
            for (ColumnBean resultColumn : tableInfo.getColumns()) {
                ResponseTable.Column column = new ResponseTable().new Column();
                column.setName(resultColumn.getName());
                column.setPrimary(resultColumn.getPrimary());
                column.setType(resultColumn.getType());
                column.setDisplay(resultColumn.getDisplay());
                columns.add(column);
            }

            // Adding recordId column as a hidden field
            ResponseTable.Column recordId = new ResponseTable().new Column();
            recordId.setName(RECORD_ID);
            recordId.setPrimary(false);
            recordId.setType("STRING");
            recordId.setDisplay(false);
            recordId.setKey(true);
            columns.add(recordId);

            ResponseTable.Column timestamp = new ResponseTable().new Column();
            timestamp.setName(TIMESTAMP);
            timestamp.setPrimary(false);
            timestamp.setType("LONG");
            timestamp.setDisplay(true);
            columns.add(timestamp);

            table.setColumns(columns);

        } catch (Exception e) {
            log.error("Unable to get table information for table:" + tableName, e);
        }

        return gson.toJson(table);
    }

    public String deleteRecords(String table, String[] recordIds) {

        if (log.isDebugEnabled()) {
            log.debug("Records[" + Arrays.toString(recordIds) + "] going to delete from" + table);
        }
        ResponseResult responseResult = new ResponseResult();
        Gson gson = RESPONSE_RESULT_BUILDER.serializeNulls().create();
        responseResult.setResult(OK);
        try {
            stub.deleteRecords(table, recordIds);
        } catch (Exception e) {
            log.error("Unable to delete records" + Arrays.toString(recordIds) + " from table :" + table, e);
            responseResult.setResult(ERROR);
            responseResult.setMessage(e.getMessage());
        }

        return gson.toJson(responseResult);
    }

    public String addRecord(String table, String[] columns, String[] values) {

        if (log.isDebugEnabled()) {
            log.debug("New record {column: " + Arrays.toString(columns) + ", values: " + Arrays.toString(values) +
                      "} going to add to" + table);
        }
        ResponseRecord responseRecord = new ResponseRecord();
        Gson gson = RESPONSE_RECORD_BUILDER.serializeNulls().create();
        responseRecord.setResult(OK);
        try {
            RecordBean recordBean = stub.addRecord(table, columns, values);
            responseRecord.setRecord(getRecord(recordBean));
        } catch (Exception e) {
            log.error("Unable to add record {column: " + Arrays.toString(columns) + ", values: " + Arrays.toString(values) + " } to table :" + table, e);
            responseRecord.setResult(ERROR);
            responseRecord.setMessage(e.getMessage());
        }
        return gson.toJson(responseRecord);
    }


    public String updateRecord(String table, String[] columns, String[] values, String recordId, String timestamp) {
        if (log.isDebugEnabled()) {
            log.debug("Record {id: " + recordId + ", column: " + Arrays.toString(columns) + ", values: " + Arrays.toString(values) + "} going to update to" + table);
        }

        ResponseRecord responseRecord = new ResponseRecord();
        Gson gson = RESPONSE_RECORD_BUILDER.serializeNulls().create();
        responseRecord.setResult(OK);
        try {
            RecordBean recordBean = stub.updateRecord(table, recordId, columns, values, Long.parseLong(timestamp));
            responseRecord.setRecord(getRecord(recordBean));
        } catch (Exception e) {
            log.error("Unable to update record {id: " + recordId + ", column: " + Arrays.toString(columns) + ", " +
                      "values: " + Arrays.toString(values) + " } to table :" + table, e);

            responseRecord.setResult(ERROR);
            responseRecord.setMessage(e.getMessage());
        }
        return gson.toJson(responseRecord);
    }

    public String getArbitraryFields(String table, String recordId) {
        ResponseArbitraryField responseArbitraryField = new ResponseArbitraryField();
        Gson gson = RESPONSE_ARBITRARY_FIELD_BUILDER.serializeNulls().create();
        responseArbitraryField.setResult(OK);
        try {
            List<Column> resultColumns = new ArrayList<>();
            EntityBean[] entityBeans = stub.getArbitraryList(table, recordId);

            if (entityBeans != null) {
                for (EntityBean entityBean : entityBeans) {
                    Column column = new Column(entityBean.getColumnName(), entityBean.getValue(), entityBean.getType());
                    resultColumns.add(column);
                }
            }

            responseArbitraryField.setColumns(resultColumns);
        } catch (Exception e) {
            responseArbitraryField.setResult(ERROR);
            responseArbitraryField.setMessage(e.getMessage());
        }

        return gson.toJson(responseArbitraryField);
    }

    public String deleteArbitraryField(String table, String recordId, String fieldName) {
        ResponseArbitraryField responseArbitraryField = new ResponseArbitraryField();
        Gson gson = RESPONSE_ARBITRARY_FIELD_BUILDER.serializeNulls().create();
        responseArbitraryField.setResult(OK);

        try {
            stub.deleteArbitraryField(table, recordId, fieldName);
        } catch (Exception e) {
            responseArbitraryField.setResult(ERROR);
            responseArbitraryField.setMessage(e.getMessage());
        }

        return gson.toJson(responseArbitraryField);
    }

    public String putArbitraryField(String table, String recordId, String fieldName, String value, String type) {

        ResponseArbitraryFieldColumn responseArbitraryFieldColumn = new ResponseArbitraryFieldColumn();
        Gson gson = RESPONSE_ARBITRARY_FIELD_COLUMN_BUILDER.serializeNulls().create();
        responseArbitraryFieldColumn.setResult(OK);
        try {
            stub.putArbitraryField(table, recordId, fieldName, value, type);
            responseArbitraryFieldColumn.setColumn(new Column(fieldName, value, type));
        } catch (Exception e) {
            responseArbitraryFieldColumn.setResult(ERROR);
            responseArbitraryFieldColumn.setMessage(e.getMessage());
        }
        return gson.toJson(responseArbitraryFieldColumn);
    }

    public String createTable(String table, String detailsJsonString) {

        String msg;
        TableBean tableBean = new TableBean();
        tableBean.setName(table);

        Gson gson = new Gson();
        List<TableSchemaColumn> columnList = gson.fromJson(detailsJsonString, TABLE_SCHEMA_TYPE);
        ColumnBean[] columns = new ColumnBean[columnList.size()];
        int i = 0;
        for (TableSchemaColumn schemaColumn : columnList) {
            ColumnBean columnBean = new ColumnBean();
            columnBean.setName(schemaColumn.getColumn());
            columnBean.setType(schemaColumn.getType());
            columnBean.setPrimary(schemaColumn.isPrimary());
            columnBean.setIndex(schemaColumn.isIndex());
            columns[i++] = columnBean;
        }
        tableBean.setColumns(columns);
        msg = "Successfully saved table information";
        try {
            stub.createTable(tableBean);
        } catch (Exception e) {
            log.error("Unable to save table information: " + e.getMessage(), e);
            msg = "Unable to save table information: " + e.getMessage();
        }
        return msg;
    }
}
