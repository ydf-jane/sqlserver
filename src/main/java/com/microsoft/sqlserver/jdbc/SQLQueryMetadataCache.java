/*
 * Microsoft JDBC Driver for SQL Server Copyright(c) Microsoft Corporation All rights reserved. This program is made
 * available under the terms of the MIT License. See the LICENSE file in the project root for more information.
 */
package com.microsoft.sqlserver.jdbc;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.Map;
import java.text.MessageFormat;


/**
 * Implements a cache for query metadata returned from sp_describe_parameter_encryption calls. Adding, removing, and
 * reading from the cache is handled here, with the location of the cache being in the EnclaveSession.
 * 
 */
class SQLQueryMetadataCache {

    final static int cacheSize = 2000; // Size of the cache in number of entries
    final static int cacheTrimThreshold = 300; // Threshold above which to trim the cache

    /**
     * Retrieves the metadata from the cache, should it exist.
     * 
     * @param params
     *        Array of parameters used
     * @param parameterNames
     *        Names of parameters used
     * @param session
     *        The current enclave session containing the cache
     * @param connection
     *        The SQLServer connection
     * @param stmt
     *        The SQLServer statement, whose returned metadata we're checking
     * @return true, if the metadata for the query can be retrieved
     * 
     */
    static boolean getQueryMetadata(Parameter[] params, ArrayList<String> parameterNames,
            EnclaveSession session, SQLServerConnection connection, SQLServerStatement stmt)throws SQLServerException {

        AbstractMap.SimpleEntry<String, String> encryptionValues = getCacheLookupKeys(stmt, connection);
        ConcurrentHashMap<String, CryptoMetadata> metadataMap = session.getCryptoCache()
            .getCacheEntry(encryptionValues.getKey());

        if (metadataMap == null) {
            return false;
        }
        
        for (int i = 0; i < params.length; i++) {
            boolean found = metadataMap.containsKey(parameterNames.get(i));
            CryptoMetadata foundData = metadataMap.get(parameterNames.get(i));

            /*
             * If ever the map doesn't contain a parameter, the cache entry cannot be used. If there is data found, it
             * should never have the initialized algorithm as that would contain the key. Clear all metadata that has
             * already been assigned in either case.
             */
            if (!found || (foundData != null && foundData.isAlgorithmInitialized())) {
                for (Parameter param : params) {
                    param.cryptoMeta = null;
                }
                return false;
            }
            params[i].cryptoMeta = foundData;
        }

        // Assign the key using a metadata copy. We shouldn't load from the cached version for security reasons.
        for (int i = 0; i < params.length; ++i) {
            try {
                CryptoMetadata cryptoCopy = null;
                CryptoMetadata metaData = params[i].getCryptoMetadata();
                if (metaData != null) {
                    cryptoCopy = new CryptoMetadata(metaData.getCekTableEntry(), metaData.getOrdinal(),
                            metaData.getEncryptionAlgorithmId(), metaData.getEncryptionAlgorithmName(),
                            metaData.getEncryptionType().getValue(), metaData.getNormalizationRuleVersion());
                }

                params[i].cryptoMeta = cryptoCopy;

                if (cryptoCopy != null) {
                    try {
                        SQLServerSecurityUtility.decryptSymmetricKey(cryptoCopy, connection, stmt);
                    } catch (SQLServerException e) {

                        removeCacheEntry(stmt, session, connection);

                        for (Parameter paramToCleanup : params) {
                            paramToCleanup.cryptoMeta = null;
                        }
                        
                        return false;
                    }
                }
            } catch (Exception e) {
                throw new SQLServerException(null, SQLServerException.getErrString
                    ("R_CEKDecryptionFailed"), null, 0, false);
            }
        }
        
        Map<Integer, CekTableEntry> enclaveKeys = session.getCryptoCache().getEnclaveEntry(encryptionValues.getValue());
        return (enclaveKeys == null);
    }

    
    /**
    * 
    * Adds the parameter metadata to the cache, also handles cache trimming.
    * 
    * @param params
    *        List of parameters used
    * @param parameterNames
    *        Names of parameters used
    * @param session
    *        Enclave session containing the cryptocache
    * @param connection
    *        SQLServerConnection
    * @param stmt
    *        SQLServer statement used to retrieve keys to find correct cache
    * @param cekList
    *        The list of CEKs (from the first RS) that is also added to the cache as well as parameter metadata
    * @return true, if the query metadata has been added correctly
    */
    static boolean addQueryMetadata(Parameter[] params, ArrayList<String> parameterNames, EnclaveSession session,
            SQLServerConnection connection, SQLServerStatement stmt, Map<Integer, 
            CekTableEntry> cekList) throws SQLServerException {

        AbstractMap.SimpleEntry<String, String> encryptionValues = getCacheLookupKeys(stmt, connection);
        if (encryptionValues.getKey() == null) {
            return false;
        }

        ConcurrentHashMap<String, CryptoMetadata> metadataMap = new ConcurrentHashMap<>(params.length);
        
        for (int i = 0; i < params.length; i++) {
            try {
                CryptoMetadata cryptoCopy = null;
                CryptoMetadata metaData = params[i].getCryptoMetadata();
                if (metaData != null) {

                    cryptoCopy = new CryptoMetadata(metaData.getCekTableEntry(), metaData.getOrdinal(),
                            metaData.getEncryptionAlgorithmId(), metaData.getEncryptionAlgorithmName(),
                            metaData.getEncryptionType().getValue(), metaData.getNormalizationRuleVersion());
                }
                if (cryptoCopy != null && !cryptoCopy.isAlgorithmInitialized()) {
                    String paramName = parameterNames.get(i);
                    metadataMap.put(paramName, cryptoCopy);
                } else {
                    return false;
                }
            } catch (SQLServerException e) {
                throw new SQLServerException(null, SQLServerException.getErrString
                                                    ("R_cryptoCacheInaccessible"), null, 0, false);
            }
        }
        
        // If the size of the cache exceeds the threshold, set that we are in trimming and trim the cache accordingly.
        int cacheSizeCurrent = session.getCryptoCache().getParamMap().size();
        if (cacheSizeCurrent > cacheSize + cacheTrimThreshold) {
            try {
                int entriesToRemove = cacheSizeCurrent - cacheSize;
                ConcurrentHashMap<String, ConcurrentHashMap<String, CryptoMetadata>> newMap = new ConcurrentHashMap<>();
                ConcurrentHashMap<String, ConcurrentHashMap<String, CryptoMetadata>> oldMap = 
                    session.getCryptoCache().getParamMap();
                int count = 0;

                for (Map.Entry<String, ConcurrentHashMap<String, CryptoMetadata>> entry : oldMap.entrySet()) {
                    if (count >= entriesToRemove) {
                        newMap.put(entry.getKey(), entry.getValue());
                    }
                    count++;
                }
                session.getCryptoCache().replaceParamMap(newMap);

            } catch (Exception e) {
                throw new SQLServerException(null, SQLServerException.getErrString
                                    ("R_cryptoCacheInaccessible"), null, 0, false);
            }
        }
        
        session.getCryptoCache().addParamEntry(encryptionValues.getKey(), metadataMap);
        return true;
    }

    
    /**
    * 
    * Remove the cache entry.
    * 
    * @param stmt
    *        SQLServer statement used to retrieve keys
    * @param session
    *        The enclave session where the cryptocache is stored
    * @param connection
    *        The SQLServerConnection, also used to retrieve keys
    */
    static void removeCacheEntry(SQLServerStatement stmt, EnclaveSession session,
            SQLServerConnection connection) {
        AbstractMap.SimpleEntry<String, String> encryptionValues = getCacheLookupKeys(stmt, connection);
        if (encryptionValues.getKey() == null) {
            return;
        }

        session.getCryptoCache().removeParamEntry(encryptionValues.getKey());
    }

    
    /**
    * 
    * Returns the cache and enclave lookup keys for a given connection and statement
    * 
    * @param statement
    *        The SQLServer statement used to construct part of the keys
    * @param connection
    *        The connection from which database name is retrieved
    * @return
    *        A key value pair containing cache lookup key and enclave lookup key
    */
    private static AbstractMap.SimpleEntry<String, String> getCacheLookupKeys(
            SQLServerStatement statement, SQLServerConnection connection) {
        final int sqlIdentifierLength = 128;
        
        if (connection == null) {
            return new AbstractMap.SimpleEntry<>(null, null);
        }

        StringBuilder cacheLookupKeyBuilder = new StringBuilder();
        cacheLookupKeyBuilder.append(":::");
        // Pad database name to 128 characters to avoid any false cache matches because of weird DB names.
        String databaseName = connection.activeConnectionProperties
                .getProperty(SQLServerDriverStringProperty.DATABASE_NAME.toString());
        cacheLookupKeyBuilder.append(databaseName);
        for (int i = databaseName.length() - 1; i < sqlIdentifierLength; ++i) {
            cacheLookupKeyBuilder.append(" ");
        }
        cacheLookupKeyBuilder.append(":::");
        cacheLookupKeyBuilder.append(statement.toString());

        String cacheLookupKey = cacheLookupKeyBuilder.toString();
        String enclaveLookupKey = cacheLookupKeyBuilder.append(":::enclaveKeys").toString();

        return new AbstractMap.SimpleEntry<>(cacheLookupKey, enclaveLookupKey);
    }

    
    /**
     * 
     * Copy the enclave CEKs so they can be later used to retry secure enlave queries.
     * 
     * @param keysToBeSentToEnclave
     *        The CEKs sent to the enclave cryptocache
     * @return
     *        A copy of the CEKs, this is what is actually added to the cryptocache
     */
    private static Map<Integer, CekTableEntry> copyEnclaveKeys(Map<Integer, CekTableEntry> keysToBeSentToEnclave) {
        Map<Integer, CekTableEntry> cekList = new ConcurrentHashMap<>();

        for (Map.Entry<Integer, CekTableEntry> entry : keysToBeSentToEnclave.entrySet()) {
            int ordinal = entry.getKey();
            CekTableEntry original = entry.getValue();
            CekTableEntry copy = new CekTableEntry(ordinal);
            for (EncryptionKeyInfo cekInfo : original.getColumnEncryptionKeyValues()) {
                copy.add(cekInfo.encryptedKey, cekInfo.databaseId, cekInfo.cekId, cekInfo.cekVersion,
                        cekInfo.cekMdVersion, cekInfo.keyPath, cekInfo.keyStoreName, cekInfo.algorithmName);
            }
            cekList.put(ordinal, copy);
        }
        return cekList;
    }
}
