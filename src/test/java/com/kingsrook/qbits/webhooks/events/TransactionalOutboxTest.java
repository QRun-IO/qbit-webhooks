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

package com.kingsrook.qbits.webhooks.events;


import com.kingsrook.qbits.webhooks.H2Support;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** With a transaction on the qbit's backend, deliveries commit or roll back
 ** with the write that caused them.
 *******************************************************************************/
class TransactionalOutboxTest
{
   private QInstance instance;



   /*******************************************************************************
    ** Setup.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      System.setProperty(WebhooksTestSupport.KEY_PROPERTY, java.util.Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
      RDBMSBackendMetaData backend = H2Support.backend("mem", "outbox");
      H2Support.prime(backend);
      instance = H2Support.instance(backend, config -> { });
      QContext.init(instance, WebhooksTestSupport.adminSession());
      Integer endpointId = WebhooksTestSupport.endpoint("crm", "http://127.0.0.1:9/hook");
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
   }



   /*******************************************************************************
    ** Teardown.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** Rollback: no delivery. Commit: one.
    *******************************************************************************/
   @Test
   void deliveriesFollowTheTransaction() throws Exception
   {
      InsertInput rolledBack = new InsertInput(WebhooksTestSupport.PERSON).withRecord(new QRecord().withValue("firstName", "Ghost").withValue("lastName", "L"));
      try(QBackendTransaction transaction = QBackendTransaction.openFor(rolledBack))
      {
         rolledBack.setTransaction(transaction);
         new InsertAction().execute(rolledBack);
         transaction.rollback();
      }
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).isEmpty();

      InsertInput committed = new InsertInput(WebhooksTestSupport.PERSON).withRecord(new QRecord().withValue("firstName", "Real").withValue("lastName", "L"));
      try(QBackendTransaction transaction = QBackendTransaction.openFor(committed))
      {
         committed.setTransaction(transaction);
         new InsertAction().execute(committed);
         transaction.commit();
      }
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY)).hasSize(1);
   }
}
