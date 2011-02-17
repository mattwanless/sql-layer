/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.server.service.memcache;

import com.akiban.ais.model.TableName;
import com.akiban.server.api.HapiGetRequest;
import com.akiban.server.api.HapiPredicate;
import com.akiban.server.api.HapiRequestException;
import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ParsedHapiGetRequest implements HapiGetRequest {

    public interface ParseErrorReporter {
        public void reportError(String error);
    }

    private final static ParseErrorReporter ERROR_REPORTER = new ParsedHapiGetRequest.ParseErrorReporter() {
        @Override
        public void reportError(String error) {
            throw new HapiParseException(error);
        }
    };

    ParsedHapiGetRequest()
    {}

    public static class HapiParseException extends RuntimeException {
        public HapiParseException(String message) {
            super(message);
        }

        HapiParseException(Throwable cause) {
            super(cause);
        }
    }

    public static HapiGetRequest parse(String query) throws HapiRequestException {
        return parse(query, ERROR_REPORTER);
    }
    
    public static ParsedHapiGetRequest parse(String query, ParseErrorReporter errorReporter)
            throws HapiRequestException
    {
        hapiLexer lexer = new hapiLexer( new ANTLRStringStream(query) );
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        hapiParser parser = new hapiParser(tokens);
        parser.setErrorReporter(errorReporter);
        try {
            return parser.get_request();
        } catch (hapiLexer.HapiLexerException e) {
            throw new HapiParseException(e);
        } catch (RecognitionException e) {
            throw new HapiParseException(e);
        } catch (HapiParseException e) {
            throw new HapiRequestException("while parsing request", e, HapiRequestException.ReasonCode.UNPARSABLE);
        }
    }

    private String schema;
    private String table;
    private String usingTable;
    private TableName usingTableName;
    private final List<HapiPredicate> predicates = new ArrayList<HapiPredicate>();

    @Override
    public String getSchema() {
        return schema;
    }

    @Override
    public String getTable() {
        return table;
    }

    @Override
    public TableName getUsingTable() {
        if (usingTableName == null) {
            usingTableName = new TableName(getSchema(), usingTable);
        }
        return usingTableName;
    }

    @Override
    public List<HapiPredicate> getPredicates() {
        return Collections.unmodifiableList(predicates);
    }

    void setTable(String table) {
        this.table = table;
        this.usingTable = table;
    }

    void setUsingTable(String usingTable) {
        this.usingTable = usingTable;
    }

    void setSchema(String schema) {
        this.schema = schema;
    }

    void addPredicate(String columnName, SimpleHapiPredicate.Operator operator, String value) {
        predicates.add( new SimpleHapiPredicate(getUsingTable(), columnName, operator, value) );
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("HapiGetRequest[ ");
        builder.append(schema).append(':').append(table).append(':');
        final TableName using = getUsingTable();
        if (!using.getTableName().equals(getTable())) {
            builder.append('(').append(using.getTableName()).append(')');
        }
        for (HapiPredicate predicate : predicates) {
            predicate.appendToSB(builder, using).append(',');
        }
        builder.setLength(builder.length()-1);
        builder.append(" ]");
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParsedHapiGetRequest that = (ParsedHapiGetRequest) o;

        if (!predicates.equals(that.predicates)) return false;
        if (!schema.equals(that.schema)) return false;
        if (!table.equals(that.table)) return false;
        if (!usingTable.equals(that.usingTable)) return false;
        if (!usingTableName.equals(that.usingTableName)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = schema.hashCode();
        result = 31 * result + table.hashCode();
        result = 31 * result + usingTable.hashCode();
        result = 31 * result + usingTableName.hashCode();
        result = 31 * result + predicates.hashCode();
        return result;
    }

    void validate() {
        List<String> errors = null;
        if (schema == null) {
            errors = error("schema is null", errors);
        }
        if (table == null) {
            errors = error("table is null", errors);
        }
        if (usingTable == null) {
            errors = error("usingTable is null", errors);
        }
        if(usingTableName == null) {
            errors = error("usingTableName is null", errors);
        }
        else if( usingTable != null && schema != null ) {
            if (!(usingTableName.getSchemaName().equals(schema) && usingTableName.getTableName().equals(usingTable))) {
                errors = error(usingTableName + "!=" + new TableName(schema, usingTable), errors);
            }
        }
        if(predicates.isEmpty()) {
            errors = error("predicates are empty", errors);
        }
        if (errors != null) {
            StringBuilder err = new StringBuilder("internal error");
            if (errors.size() != 1) {
                err.append('s');
            }
            err.append(": ").append(errors);
            throw new IllegalStateException(err.toString());
        }
    }

    private static List<String> error(String error, List<String> errors) {
        if (errors == null) {
            errors = new ArrayList<String>();
        }
        errors.add(error);
        return errors;
    }
}
