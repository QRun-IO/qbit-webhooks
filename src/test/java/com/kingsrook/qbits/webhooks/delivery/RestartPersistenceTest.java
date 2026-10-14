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

package com.kingsrook.qbits.webhooks.delivery;


import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import com.kingsrook.qbits.webhooks.H2Support;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport.Receiver;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.secrets.EndpointSecrets;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.webhooks.WebhookSecret;
import com.kingsrook.qqq.backend.core.utils.webhooks.WebhookVerifier;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Everything survives a restart: deliveries queued before it are sent after
 ** it, signed with the secret stored (encrypted) before it, and the new
 ** instance's config is the one used (no static leftovers).
 *******************************************************************************/
class RestartPersistenceTest
{
   /*******************************************************************************
    ** Queue, "restart", deliver.
    *******************************************************************************/
   @Test
   void pendingDeliveriesSurviveARestart() throws Exception
   {
      WebhooksRuntime.setClock(java.time.Clock.systemUTC());
      System.setProperty(WebhooksTestSupport.KEY_PROPERTY, java.util.Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
      Path dir = Files.createTempDirectory("webhooks-restart");
      RDBMSBackendMetaData backend = H2Support.backend("file", dir.resolve("db").toString().replace('\\', '/'));
      H2Support.prime(backend);

      try(Receiver receiver = new Receiver())
      {
         //////////////////////////////////////////////////////
         // first life: configure and queue, but never send //
         //////////////////////////////////////////////////////
         QInstance first = H2Support.instance(backend, config -> config.withSourceName("first-life"));
         QContext.init(first, WebhooksTestSupport.adminSession());
         Integer endpointId = WebhooksTestSupport.endpoint("crm", receiver.url("/hook"));
         WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
         WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "Ada").withValue("lastName", "L"));
         String fingerprint = EndpointSecrets.signingKeys(endpointId).get(0).fingerprint();
         QContext.clear();
         WebhooksRuntime.closeHttpClients();

         //////////////////////////////////////////////////////////////////
         // second life: a new instance over the same database file       //
         //////////////////////////////////////////////////////////////////
         QInstance second = H2Support.instance(backend, config -> config.withSourceName("second-life").withMaxAttempts(9));
         QContext.init(second, WebhooksTestSupport.adminSession());
         assertThat(WebhooksRuntime.config().getMaxAttempts()).isEqualTo(9);
         List<QRecord> pending = WebhooksTestSupport.all(WebhooksTables.DELIVERY);
         assertThat(pending).hasSize(1);
         assertThat(pending.get(0).getValueString("status")).isEqualTo("PENDING");
         assertThat(pending.get(0).getValueString("payload")).contains("qqq://first-life/table/person");

         assertThat(new DeliveryEngine().runOnce().getDelivered()).isEqualTo(1);
         Receiver.Request request = receiver.requests().get(0);
         List<WebhookSecret> secrets = EndpointSecrets.signingKeys(endpointId).stream().map(EndpointSecrets.SigningKey::secret).toList();
         assertThat(EndpointSecrets.signingKeys(endpointId).get(0).fingerprint()).isEqualTo(fingerprint);
         new WebhookVerifier(secrets, Duration.ofMinutes(5), java.time.Clock.systemUTC()).verify(request.headers(), request.body());
         QContext.clear();
      }
      finally
      {
         WebhooksRuntime.setClock(WebhooksTestSupport.CLOCK);
      }
   }
}
