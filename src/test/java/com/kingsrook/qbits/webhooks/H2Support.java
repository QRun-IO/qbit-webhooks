/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2025.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.webhooks;


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.function.Consumer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.QueryManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;


/*******************************************************************************
 ** H2 (a real SQL database with real unique constraints) for the replica and
 ** restart tests. Each "replica" is its own QInstance over the same database.
 *******************************************************************************/
public final class H2Support
{
   public static final String BACKEND = "rdbms";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private H2Support()
   {
   }



   /*******************************************************************************
    ** A backend: hostName "mem" (shared in-memory) or "file" (databaseName is
    ** a path).
    *******************************************************************************/
   public static RDBMSBackendMetaData backend(String hostName, String databaseName)
   {
      RDBMSBackendMetaData backend = new RDBMSBackendMetaData()
         .withName(BACKEND)
         .withVendor("h2")
         .withHostName(hostName)
         .withDatabaseName(databaseName)
         .withUsername("sa");
      backend.setJdbcUrl(ConnectionManager.getJdbcUrl(backend) + ";DATABASE_TO_UPPER=FALSE");
      return (backend);
   }



   /*******************************************************************************
    ** Create the tables (drops them first).
    *******************************************************************************/
   public static void prime(RDBMSBackendMetaData backend) throws Exception
   {
      try(Connection connection = new ConnectionManager().getConnection(backend); InputStream in = H2Support.class.getResourceAsStream("/webhooks-h2.sql"))
      {
         String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         StringBuilder kept = new StringBuilder();
         for(String line : sql.split("\n"))
         {
            if(!line.startsWith("--"))
            {
               kept.append(line).append("\n");
            }
         }
         for(String statement : kept.toString().split(";"))
         {
            if(!statement.isBlank())
            {
               QueryManager.executeUpdate(connection, statement);
            }
         }
      }
   }



   /*******************************************************************************
    ** An instance (a "replica") over the database.
    *******************************************************************************/
   public static QInstance instance(RDBMSBackendMetaData backend, Consumer<WebhooksQBitConfig> tweak) throws QException
   {
      QInstance instance = WebhooksTestSupport.baseInstance();
      instance.addBackend(backend);
      WebhooksTestSupport.addAppTables(instance, BACKEND);
      WebhooksQBitConfig config = WebhooksTestSupport.defaultConfig(BACKEND);
      tweak.accept(config);
      new WebhooksQBitProducer().withQBitConfig(config).produce(instance);
      new QInstanceValidator().validate(instance);
      return (instance);
   }
}
