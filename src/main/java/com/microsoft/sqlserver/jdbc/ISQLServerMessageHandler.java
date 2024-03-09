/*
 * Microsoft JDBC Driver for SQL Server Copyright(c) Microsoft Corporation All rights reserved. This program is made
 * available under the terms of the MIT License. See the LICENSE file in the project root for more information.
 */
package com.microsoft.sqlserver.jdbc;

/**
 * You can use the ISQLServerMessageHandler interface to customize the way JDBC handles error messages generated by the SQL Server. 
 * Implementing ISQLServerMessageHandler in your own class for handling error messages can provide the following benefits:
 * <ul>
 *   <li><b>"message feedback"</b><br>
 *       Display Server messages from a long running SQL Statement<br>
 *       Like <code>RAISERROR ('Progress message...', 0, 1) WITH NOWAIT</code><br>
 *       Or Status messages from a running backup...<br>
 *   </li>
 *   <li><b>"Universal" error logging</b><br>
 *       Your error-message handler can contain the logic for handling all error logging.
 *   </li>
 *   <li><b>"Universal" error handling</b><br>
 *       Error-handling logic can be placed in your error-message handler, instead of being repeated throughout your application.
 *   </li>
 *   <li><b>Remapping of error-message severity</b>, based on application requirements<br>
 *       Your error-message handler can contain logic for recognizing specific error messages, and downgrading or upgrading 
 *       their severity based on application considerations rather than the severity rating of the server. 
 *       For example, during a cleanup operation that deletes old rows, you might want to downgrade the severity of a 
 *       message that a row does not exist. However, you may want to upgrade the severity in other circumstances.
 *   </li>
 * </ul>
 * <p>
 * For example code, see {@link #messageHandler(ISQLServerMessage)}
 */
public interface ISQLServerMessageHandler
{
    /**
     * You can use the ISQLServerMessageHandler interface to customize the way JDBC handles error messages generated by the SQL Server. 
     * Implementing ISQLServerMessageHandler in your own class for handling error messages can provide the following benefits:
     * <ul>
     *   <li><b>"message feedback"</b><br>
     *       Display Server messages from a long running SQL Statement<br>
     *       Like <code>RAISERROR ('Progress message...', 0, 1) WITH NOWAIT</code><br>
     *       Or Status messages from a running backup...<br>
     *   </li>
     *   <li><b>"Universal" error logging</b><br>
     *       Your error-message handler can contain the logic for handling all error logging.
     *   </li>
     *   <li><b>"Universal" error handling</b><br>
     *       Error-handling logic can be placed in your error-message handler, instead of being repeated throughout your application.
     *   </li>
     *   <li><b>Remapping of error-message severity</b>, based on application requirements<br>
     *       Your error-message handler can contain logic for recognizing specific error messages, and downgrading or upgrading 
     *       their severity based on application considerations rather than the severity rating of the server. 
     *       For example, during a cleanup operation that deletes old rows, you might want to downgrade the severity of a 
     *       message that a row does not exist. However, you may want to upgrade the severity in other circumstances.
     *   </li>
     * </ul>
     * 
     * Example code:
     * <pre>
     *  public ISQLServerMessage messageHandler(ISQLServerMessage serverErrorOrWarning)
     *  {
     *      ISQLServerMessage retObj = serverErrorOrWarning;
     *
     *      if (serverErrorOrWarning.isErrorMessage()) {
     *
     *          // Downgrade: 2601 -- Cannot insert duplicate key row...
     *          if (2601 == serverErrorOrWarning.getErrorNumber()) {
     *              retObj = serverErrorOrWarning.getSQLServerMessage().toSQLServerInfoMessage();
     *          }
     *
     *          // Discard: 3701 -- Cannot drop the table ...
     *          if (3701 == serverErrorOrWarning.getErrorNumber()) {
     *              retObj = null;
     *          }
     *      }
     *
     *      return retObj;
     *  }
    
     * </pre>
     * 
     * @param serverErrorOrWarning
     * @return 
     * <ul>
     *   <li><b>unchanged</b> same object as passed in.<br>
     *       The JDBC driver will works as if no message hander was installed<br>
     *       Possibly used for logging functionality<br>
     *   </li>
     *   <li><b>null</b><br>
     *       The JDBC driver will <i>discard</i> this message. No SQLException will be thrown
     *   </li>
     *   <li><b>SQLServerInfoMessage</b> object<br>
     *       Create a "SQL warning" from a input database error, and return it. 
     *       This results in the warning being added to the warning-message chain.
     *   </li>
     *   <li><b>SQLServerError</b> object<br>
     *       If the originating message is a SQL warning (SQLServerInfoMessage object), messageHandler can evaluate 
     *       the SQL warning as urgent and create and return a SQL exception (SQLServerError object)
     *       to be thrown once control is returned to the JDBC Driver.
     *   </li>
     * </ul>
     */
    ISQLServerMessage messageHandler(ISQLServerMessage serverErrorOrWarning);
}
