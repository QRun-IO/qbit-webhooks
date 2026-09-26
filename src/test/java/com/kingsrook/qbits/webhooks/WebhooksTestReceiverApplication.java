/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2025.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.kingsrook.qbits.webhooks;


import java.time.Instant;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppSection;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.middleware.javalin.QJavalinMetaData;
import com.kingsrook.qqq.middleware.javalin.metadata.JavalinRouteProviderMetaData;
import com.kingsrook.qqq.middleware.javalin.routeproviders.ProcessBasedRouterPayload;


/*******************************************************************************
 **
 *******************************************************************************/
public class WebhooksTestReceiverApplication extends AbstractQQQApplication
{
   public static final String MEMORY_BACKEND_NAME  = "memory";
   public static final String RECEIPT_TABLE_NAME   = "receipts";
   public static final String RECEIVE_PROCESS_NAME = "receiveWebhook";



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public QInstance defineQInstance() throws QException
   {
      QInstance qInstance = new QInstance();

      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withType(QAuthenticationType.FULLY_ANONYMOUS));

      qInstance.addBackend(new QBackendMetaData()
         .withName(MEMORY_BACKEND_NAME)
         .withBackendType(MemoryBackendModule.class));

      qInstance.addTable(new QTableMetaData()
         .withName(RECEIPT_TABLE_NAME)
         .withBackendName(MEMORY_BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("timestamp", QFieldType.DATE_TIME).withIsEditable(true))
         .withField(new QFieldMetaData("content", QFieldType.STRING).withIsEditable(true)));

      qInstance.addProcess(new QProcessMetaData()
         .withName(RECEIVE_PROCESS_NAME)
         .withStep(new QBackendStepMetaData()
            .withName("execute")
            .withCode(new QCodeReference(ReceiveWebhookProcessStep.class))));

      ///////////////////////////////////////////
      // turn off audits (why on by default??) //
      ///////////////////////////////////////////
      //? qInstance.getTables().values().forEach(t -> t.setAuditRules(new QAuditRules().withAuditLevel(AuditLevel.NONE)));

      /////////////////
      // create apps //
      /////////////////
      qInstance.addApp(new QAppMetaData()
         .withName("receiptsApp")
         .withLabel("Receipts")
         .withIcon(new QIcon("receipt_long"))
         .withSection(new QAppSection()
            .withName("data")
            .withTables(List.of(RECEIPT_TABLE_NAME))));

      QJavalinMetaData.ofOrWithNew(qInstance)
         .withRouteProvider(new JavalinRouteProviderMetaData()
            .withName("defaultReceiver")
            .withProcessName(RECEIVE_PROCESS_NAME)
            .withHostedPath("/" + RECEIVE_PROCESS_NAME)
            .withMethods(List.of("POST", "GET")));

      return qInstance;
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static class ReceiveWebhookProcessStep implements BackendStep
   {

      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public void run(RunBackendStepInput runBackendStepInput, RunBackendStepOutput runBackendStepOutput) throws QException
      {
         ProcessBasedRouterPayload processPayload = runBackendStepInput.getProcessPayload(ProcessBasedRouterPayload.class);
         String                    content        = processPayload.getBodyString();

         QRecord insertedRecord = new InsertAction().execute(new InsertInput(RECEIPT_TABLE_NAME).withRecord(new QRecord().withValue("timestamp", Instant.now()).withValue("content", content))).getRecords().get(0);
         System.out.println("I received and inserted: " + insertedRecord.getValues());

         processPayload.setResponseString("OK");
         runBackendStepOutput.setProcessPayload(processPayload);
      }
   }

}
