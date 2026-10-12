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

package com.kingsrook.qbits.webhooks.permissions;


import java.io.Serializable;
import java.util.List;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.app.WebhooksApp;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.processes.WebhooksProcesses;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;


/*******************************************************************************
 ** The webhooks.view / manage / admin rules, field-level admin rules on
 ** endpoints, subscription read checks and record security.
 *******************************************************************************/
class WebhooksPermissionsTest
{
   private QInstance instance;



   /*******************************************************************************
    ** Setup.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      instance = WebhooksTestSupport.setUp();
   }



   /*******************************************************************************
    ** Run as a session with some permissions.
    *******************************************************************************/
   private void as(String... permissions)
   {
      QContext.init(instance, new QSession().withPermissions(List.of(permissions)));
   }



   /*******************************************************************************
    ** The mapping of actions to permissions.
    *******************************************************************************/
   @Test
   void checkerMapping() throws Exception
   {
      WebhooksPermissionChecker checker = new WebhooksPermissionChecker();
      QTableMetaData endpoint = instance.getTable(WebhooksTables.ENDPOINT);
      QTableMetaData delivery = instance.getTable(WebhooksTables.DELIVERY);
      QTableMetaData secret   = instance.getTable(WebhooksTables.SECRET);

      as(WebhooksPermissions.VIEW);
      assertThatNoException().isThrownBy(() -> checker.checkPermissionsThrowing(new QueryInput(WebhooksTables.ENDPOINT), endpoint));
      assertThatThrownBy(() -> checker.checkPermissionsThrowing(new InsertInput(WebhooksTables.ENDPOINT), endpoint)).isInstanceOf(QPermissionDeniedException.class);
      assertThatThrownBy(() -> checker.checkPermissionsThrowing(new QueryInput(), instance.getProcess(WebhooksProcesses.REPLAY))).isInstanceOf(QPermissionDeniedException.class);

      as(WebhooksPermissions.MANAGE);
      assertThatNoException().isThrownBy(() -> checker.checkPermissionsThrowing(new UpdateInput(WebhooksTables.ENDPOINT), endpoint));
      assertThatNoException().isThrownBy(() -> checker.checkPermissionsThrowing(new QueryInput(), instance.getProcess(WebhooksProcesses.REPLAY)));
      assertThatThrownBy(() -> checker.checkPermissionsThrowing(new DeleteInput(WebhooksTables.ENDPOINT), endpoint)).isInstanceOf(QPermissionDeniedException.class);
      assertThatThrownBy(() -> checker.checkPermissionsThrowing(new InsertInput(WebhooksTables.SECRET), secret)).isInstanceOf(QPermissionDeniedException.class);
      assertThatThrownBy(() -> checker.checkPermissionsThrowing(new QueryInput(), instance.getProcess(WebhooksProcesses.ROTATE_SECRET))).isInstanceOf(QPermissionDeniedException.class);
      assertThatThrownBy(() -> checker.checkPermissionsThrowing(new QueryInput(), instance.getWidget(WebhooksApp.SECRET_VALUE_WIDGET))).isInstanceOf(QPermissionDeniedException.class);

      as(WebhooksPermissions.ADMIN);
      assertThatNoException().isThrownBy(() -> checker.checkPermissionsThrowing(new DeleteInput(WebhooksTables.ENDPOINT), endpoint));
      assertThatNoException().isThrownBy(() -> checker.checkPermissionsThrowing(new QueryInput(), instance.getWidget(WebhooksApp.SECRET_VALUE_WIDGET)));
      assertThatThrownBy(() -> checker.checkPermissionsThrowing(new InsertInput(WebhooksTables.DELIVERY), delivery)).hasMessageContaining("written only by the webhooks qbit");

      as();
      assertThatThrownBy(() -> checker.checkPermissionsThrowing(new QueryInput(WebhooksTables.ENDPOINT), endpoint)).isInstanceOf(QPermissionDeniedException.class);
   }



   /*******************************************************************************
    ** Internal-network fields need webhooks.admin, through the table itself.
    *******************************************************************************/
   @Test
   void internalTargetsNeedAdmin() throws Exception
   {
      as(WebhooksPermissions.MANAGE);
      QRecord refused = new InsertAction().execute(new InsertInput(WebhooksTables.ENDPOINT).withRecords(List.of(new QRecord().withValue("name", "pods")
         .withValue("url", "http://10.100.22.64/revalidate").withValue("allowInternalTargets", true).withValue("internalAllowlist", "10.100.22.0/24")))).getRecords().get(0);
      assertThat(refused.getErrors()).isNotEmpty();
      assertThat(refused.getErrors().get(0).getMessage()).contains("webhooks.admin");

      QRecord publicEndpoint = WebhooksTestSupport.insert(WebhooksTables.ENDPOINT, new QRecord().withValue("name", "public").withValue("url", "https://93.184.216.34/hook"));
      QRecord update = new UpdateAction().execute(new UpdateInput(WebhooksTables.ENDPOINT).withRecord(new QRecord().withValue("id", publicEndpoint.getValue("id"))
         .withValue("internalAllowlist", "10.0.0.0/8"))).getRecords().get(0);
      assertThat(update.getErrors()).isNotEmpty();

      as(WebhooksPermissions.ADMIN);
      QRecord allowed = WebhooksTestSupport.insert(WebhooksTables.ENDPOINT, new QRecord().withValue("name", "pods")
         .withValue("url", "http://10.100.22.64/revalidate").withValue("allowInternalTargets", true).withValue("internalAllowlist", "10.100.22.0/24"));
      assertThat(allowed.getValue("id")).isNotNull();

      QRecord badList = new InsertAction().execute(new InsertInput(WebhooksTables.ENDPOINT).withRecords(List.of(new QRecord().withValue("name", "bad")
         .withValue("url", "https://93.184.216.34/").withValue("allowInternalTargets", true).withValue("internalAllowlist", "not a cidr!")))).getRecords().get(0);
      assertThat(badList.getErrors()).isNotEmpty();
   }



   /*******************************************************************************
    ** Secrets: people add references only, never values.
    *******************************************************************************/
   @Test
   void secretsAreReferencesOrGeneratedOnly() throws Exception
   {
      Integer endpointId = WebhooksTestSupport.insert(WebhooksTables.ENDPOINT, new QRecord().withValue("name", "e").withValue("url", "https://93.184.216.34/")).getValueInteger("id");
      QRecord generated = new InsertAction().execute(new InsertInput(WebhooksTables.SECRET).withRecords(List.of(new QRecord().withValue("endpointId", endpointId)
         .withValue("source", "GENERATED").withValue("encryptedSecret", "v1:forged")))).getRecords().get(0);
      assertThat(generated.getErrors()).isNotEmpty();

      QRecord value = new InsertAction().execute(new InsertInput(WebhooksTables.SECRET).withRecords(List.of(new QRecord().withValue("endpointId", endpointId)
         .withValue("source", "REFERENCE").withValue("secretRef", "whsec_abc+def/=")))).getRecords().get(0);
      assertThat(value.getErrors()).isNotEmpty();

      System.setProperty("TEST_WEBHOOK_SECRET", "whsec_" + java.util.Base64.getEncoder().encodeToString("0123456789abcdefXYZ".getBytes()));
      QRecord reference = WebhooksTestSupport.insert(WebhooksTables.SECRET, new QRecord().withValue("endpointId", endpointId)
         .withValue("source", "REFERENCE").withValue("secretRef", "TEST_WEBHOOK_SECRET"));
      assertThat(reference.getValueString("fingerprint")).hasSize(12);
      assertThat(reference.getValue("encryptedSecret")).isNull();
      assertThat(com.kingsrook.qbits.webhooks.secrets.EndpointSecrets.signingKeys(endpointId)).hasSize(1);
   }



   /*******************************************************************************
    ** Subscribing needs read access to the event's table.
    *******************************************************************************/
   @Test
   void subscribingNeedsReadAccessToTheTable() throws Exception
   {
      instance.getTable(WebhooksTestSupport.PERSON).setPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_WRITE_PERMISSIONS));
      Integer endpointId = WebhooksTestSupport.insert(WebhooksTables.ENDPOINT, new QRecord().withValue("name", "e").withValue("url", "https://93.184.216.34/")).getValueInteger("id");

      QRecord refused = new InsertAction().execute(new InsertInput(WebhooksTables.SUBSCRIPTION).withRecords(List.of(new QRecord().withValue("endpointId", endpointId)
         .withValue("eventTypeName", "person.inserted")))).getRecords().get(0);
      assertThat(refused.getErrors()).isNotEmpty();
      assertThat(refused.getErrors().get(0).getMessage()).contains("cannot read the person table");

      QContext.init(instance, WebhooksTestSupport.adminSession().withPermission("person.read"));
      WebhooksTestSupport.insert(WebhooksTables.SUBSCRIPTION, new QRecord().withValue("endpointId", endpointId).withValue("eventTypeName", "person.inserted"));

      QRecord unknown = new InsertAction().execute(new InsertInput(WebhooksTables.SUBSCRIPTION).withRecords(List.of(new QRecord().withValue("endpointId", endpointId)
         .withValue("eventTypeName", "nope.inserted")))).getRecords().get(0);
      assertThat(unknown.getErrors()).isNotEmpty();
      QRecord badFilter = new InsertAction().execute(new InsertInput(WebhooksTables.SUBSCRIPTION).withRecords(List.of(new QRecord().withValue("endpointId", endpointId)
         .withValue("eventTypeName", "person.inserted").withValue("filterJson", "{not json")))).getRecords().get(0);
      assertThat(badFilter.getErrors()).isNotEmpty();
   }



   /*******************************************************************************
    ** Record security: a subscription only gets records its security values
    ** allow reading.
    *******************************************************************************/
   @Test
   void recordSecurityLimitsWhatASubscriptionGets() throws Exception
   {
      instance = WebhooksTestSupport.setUp(config -> config
         .withSecurityFields(List.of(new QFieldMetaData("storeId", QFieldType.INTEGER)))
         .withRecordSecurityLocks(List.of(new RecordSecurityLock().withFieldName("storeId").withSecurityKeyType("store"))), qInstance ->
      {
         qInstance.addSecurityKeyType(new QSecurityKeyType().withName("store").withAllAccessKeyName("storeAll"));
         QTableMetaData person = qInstance.getTable(WebhooksTestSupport.PERSON);
         person.addField(new QFieldMetaData("storeId", QFieldType.INTEGER).withBackendName("store_id"));
         person.withRecordSecurityLock(new RecordSecurityLock().withFieldName("storeId").withSecurityKeyType("store"));
      });
      QContext.init(instance, WebhooksTestSupport.adminSession().withSecurityKeyValue("storeAll", true));

      Integer endpointId = WebhooksTestSupport.endpoint("store1", "http://127.0.0.1:9/hook");
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> s.withValue("storeId", 1));

      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "InStore1").withValue("storeId", 1));
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "InStore2").withValue("storeId", 2));
      List<QRecord> deliveries = WebhooksTestSupport.all(WebhooksTables.DELIVERY);
      assertThat(deliveries).hasSize(1);
      assertThat(deliveries.get(0).getValueString("payload")).contains("InStore1");
      assertThat(deliveries.get(0).getValue("storeId")).isEqualTo((Serializable) 1);
   }
}
