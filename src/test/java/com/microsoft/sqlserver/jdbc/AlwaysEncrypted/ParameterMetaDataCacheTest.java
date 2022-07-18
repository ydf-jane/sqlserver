/*
 * Microsoft JDBC Driver for SQL Server Copyright(c) Microsoft Corporation All rights reserved. This program is made
 * available under the terms of the MIT License. See the LICENSE file in the project root for more information.
 */
package com.microsoft.sqlserver.jdbc.AlwaysEncrypted;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import com.microsoft.sqlserver.jdbc.SQLServerConnection;
import com.microsoft.sqlserver.jdbc.SQLServerStatement;
import com.microsoft.sqlserver.jdbc.TestUtils;
import com.microsoft.sqlserver.testframework.Constants;
import com.microsoft.sqlserver.testframework.PrepUtil;


/**
 * Tests for caching parameter metadata in sp_describe_parameter_encryption calls
 */
@RunWith(JUnitPlatform.class)
@Tag(Constants.xSQLv11)
@Tag(Constants.xSQLv12)
@Tag(Constants.xSQLv14)
public class ParameterMetaDataCacheTest extends AESetup {

    @BeforeAll
    public static void setupTests() throws Exception {
        connectionString = TestUtils.addOrOverrideProperty(connectionString, "columnEncryptionSetting", "Enabled");
        setConnection();
    }

    /**
     * 
     * Tests caching of parameter metadata by running a query to be cached, another to replace parameter information,
     * then the first again to measure the difference in time between the two runs.
     * 
     * @throws Exception
     */
    @Test
    @Tag(Constants.xSQLv11)
    @Tag(Constants.xSQLv12)
    @Tag(Constants.xSQLv14)
    @Tag(Constants.reqExternalSetup)
    public void testParameterMetaDataCache() throws Exception {
        try (SQLServerConnection con = PrepUtil.getConnection(AETestConnectionString);
                SQLServerStatement stmt = (SQLServerStatement) con.createStatement()) {
            String[] charValues = createCharValues(false);
            String[] numericValues = createNumericValues(false);
            TestUtils.dropTableIfExists(CHAR_TABLE_AE, stmt);
            TestUtils.dropTableIfExists(NUMERIC_TABLE_AE, stmt);
            createTable(CHAR_TABLE_AE, cekAkv, charTable);
            createTable(NUMERIC_TABLE_AE, cekAkv, numericTable);

            final Field cacheHits = Class.forName("com.microsoft.sqlserver.jdbc.ParameterMetaDataCache")
                    .getDeclaredField("CACHE_HITS");
            cacheHits.setAccessible(true);

            // Success is measured by looking at the cache hits. For the second call to update CHAR_TABLE, if we
            // successfully cached the first time, we will be able to read from the cache and cache hits should
            // increase.
            populateCharNormalCase(charValues);
            populateNumeric(numericValues);
            int hitsBefore = cacheHits.getInt(Class.forName("com.microsoft.sqlserver.jdbc.ParameterMetaDataCache"));
            populateCharNormalCase(charValues);
            int hitsAfter = cacheHits.getInt(Class.forName("com.microsoft.sqlserver.jdbc.ParameterMetaDataCache"));

            assertTrue((hitsAfter - hitsBefore) == 1);
            con.close();
        }
    }

    /**
     * 
     * Tests trimming of the cache by setting the maximum size, and size above which to trim, to 0. When the second,
     * NUMERIC_TABLE, is updated, this should successfully trim the cache to empty, before adding the new entry.
     * 
     * @throws Exception
     */
    @Test
    @Tag(Constants.xSQLv11)
    @Tag(Constants.xSQLv12)
    @Tag(Constants.xSQLv14)
    @Tag(Constants.reqExternalSetup)
    public void testParameterMetaDataCacheTrim() throws Exception {
        Field cacheSize = Class.forName("com.microsoft.sqlserver.jdbc.ParameterMetaDataCache")
                .getDeclaredField("CACHE_SIZE");
        Field maximumWeightedCapacity = Class.forName("com.microsoft.sqlserver.jdbc.ParameterMetaDataCache")
                .getDeclaredField("MAX_WEIGHTED_CAPACITY");

        cacheSize.setAccessible(true);
        maximumWeightedCapacity.setAccessible(true);

        cacheSize.set(cacheSize.get(Class.forName("com.microsoft.sqlserver.jdbc.ParameterMetaDataCache")), 0);
        maximumWeightedCapacity.set(
                maximumWeightedCapacity.get(Class.forName("com.microsoft.sqlserver.jdbc.ParameterMetaDataCache")), 0);

        try (SQLServerConnection con = PrepUtil.getConnection(AETestConnectionString);
                SQLServerStatement stmt = (SQLServerStatement) con.createStatement()) {
            String[] charValues = createCharValues(false);
            String[] numericValues = createNumericValues(false);
            TestUtils.dropTableIfExists(CHAR_TABLE_AE, stmt);
            TestUtils.dropTableIfExists(NUMERIC_TABLE_AE, stmt);
            createTable(CHAR_TABLE_AE, cekAkv, charTable);
            createTable(NUMERIC_TABLE_AE, cekAkv, numericTable);

            populateCharNormalCase(charValues);
            populateNumeric(numericValues);
            populateCharNormalCase(charValues);
            con.close();
        }
    }

    /**
     * 
     * Tests that the enclave is retried when using secure enclaves (assuming the server supports this). This is done by
     * executing a query generating metadata in the cache, changing the column encryption type to make the metadata
     * stale, and running the query again. The query should fail, but retry and pass.
     * 
     * @throws Exception
     */
    @Test
    @Tag(Constants.xSQLv11)
    @Tag(Constants.xSQLv12)
    @Tag(Constants.xSQLv14)
    @Tag(Constants.reqExternalSetup)
    public void testRetryWithSecureCache() throws Exception {
        try (SQLServerConnection con = PrepUtil.getConnection(AETestConnectionString);
                SQLServerStatement stmt = (SQLServerStatement) con.createStatement()) {
            String[] values = createCharValues(false);
            TestUtils.dropTableIfExists(CHAR_TABLE_AE, stmt);
            createTable(CHAR_TABLE_AE, cekAkv, charTable);
            populateCharNormalCase(values);
            testAlterColumnEncryption(stmt, CHAR_TABLE_AE, charTable, cekAkv);
            populateCharNormalCase(values);
            con.close();
        }
    }

    /**
     * Dropping all CMKs and CEKs and any open resources. Technically, dropAll depends on the state of the class so it
     * shouldn't be static, but the AfterAll annotation requires it to be static.
     * 
     * @throws SQLException
     */
    @AfterAll
    public static void cleanUp() throws Exception {
        dropAll();
    }
}
