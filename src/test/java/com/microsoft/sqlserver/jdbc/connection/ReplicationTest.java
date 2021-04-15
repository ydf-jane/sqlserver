/*
 * Microsoft JDBC Driver for SQL Server Copyright(c) Microsoft Corporation All rights reserved. This program is made
 * available under the terms of the MIT License. See the LICENSE file in the project root for more information.
 */
package com.microsoft.sqlserver.jdbc.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import com.microsoft.sqlserver.jdbc.RandomUtil;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import com.microsoft.sqlserver.jdbc.TestUtils;
import com.microsoft.sqlserver.testframework.AbstractTest;
import com.microsoft.sqlserver.testframework.Constants;
import com.microsoft.sqlserver.testframework.DBTable;


/*
 * This test is for testing the replication connection property
 */
@RunWith(JUnitPlatform.class)
@Tag(Constants.xAzureSQLDW)
public class ReplicationTest extends AbstractTest {

    @Test
    public void testReplication() throws SQLException {
        String tableName = RandomUtil.getIdentifier("repl");
        String triggerName = RandomUtil.getIdentifier("trig");
        String escapedTableName = DBTable.escapeIdentifier(tableName);
        String escapedTriggerName = DBTable.escapeIdentifier(triggerName);
        SQLServerDataSource ds = new SQLServerDataSource();
        ds.setURL(connectionString);
        ds.setReplication(false);

        String sqlDropTable = "IF OBJECT_ID('" + TestUtils.escapeSingleQuotes(escapedTableName) + "', 'U') IS NOT NULL "
                + " DROP TABLE " + escapedTableName + ";";
        String sqlCreateTable = "CREATE TABLE " + escapedTableName + " ([TestReplication] [varchar](50) NULL)";
        String sqlCreateTrigger = "CREATE TRIGGER " + escapedTriggerName + " ON " + escapedTableName + " "
                + "INSTEAD OF INSERT NOT FOR REPLICATION AS "
                + "BEGIN "
                + "	INSERT INTO " + escapedTableName + " (TestReplication) "
                + "	SELECT TestReplication + ' - REPLICATION IS OFF' "
                + "   FROM INSERTED "
                + "END";
        String sqlInsert = "INSERT INTO " + escapedTableName + " (TestReplication) values ('Replication test')";
        String sqlDelete = "DELETE FROM " + escapedTableName;
        String sqlSelect = "SELECT TestReplication FROM " + escapedTableName;

        try (Connection con = ds.getConnection(); Statement stmt = con.createStatement()) {
            // drop
            stmt.execute(sqlDropTable);
            // create
            stmt.execute(sqlCreateTable);
            stmt.execute(sqlCreateTrigger);
            stmt.executeUpdate(sqlInsert);
            try (ResultSet rs = stmt.executeQuery(sqlSelect)) {
                if (rs.next()) {
                    assertEquals(rs.getString(1), "Replication test - REPLICATION IS OFF");
                } else {
                    assertTrue(false, "Expected row of data was not found.");
                }
            }
            stmt.execute(sqlDelete);
        }

        ds.setReplication(true);
        try (Connection con = ds.getConnection(); Statement stmt = con.createStatement()) {
            stmt.executeUpdate(sqlInsert);
            try (ResultSet rs = stmt.executeQuery(sqlSelect)) {
                if (rs.next()) {
                    assertEquals(rs.getString(1), "Replication test");
                } else {
                    assertTrue(false, "Expected row of data was not found.");
                }
            }
            stmt.execute(sqlDropTable);
        }
	}
}
