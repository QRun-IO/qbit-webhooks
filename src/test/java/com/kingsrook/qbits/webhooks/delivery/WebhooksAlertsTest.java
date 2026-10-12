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


import java.time.Duration;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport.Receiver;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.QAlerts;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Health changes raise and resolve alerts through the Alerts SPI when it is
 ** present (here, the test stand-in).
 *******************************************************************************/
class WebhooksAlertsTest
{
   private Receiver receiver;



   /*******************************************************************************
    ** Setup.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      WebhooksTestSupport.setUp();
      QAlerts.RAISED.clear();
      QAlerts.RESOLVED.clear();
      receiver = new Receiver();
   }



   /*******************************************************************************
    ** Teardown.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      receiver.close();
   }



   /*******************************************************************************
    ** Unhealthy raises, recovery resolves, auto-disable and dead letters raise.
    *******************************************************************************/
   @Test
   void healthAlerts() throws Exception
   {
      assertThat(WebhooksAlerts.available()).isTrue();
      receiver.respond(r -> Receiver.Response.of(500));
      Integer endpointId = WebhooksTestSupport.endpoint("crm", receiver.url("/hook"));
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
      for(int i = 0; i < 3; i++)
      {
         WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "P" + i));
      }
      new DeliveryEngine().runOnce();

      AlertSignal unhealthy = QAlerts.RAISED.get(0);
      assertThat(unhealthy.getAlertTypeName()).isEqualTo(WebhooksAlerts.TYPE_UNHEALTHY);
      assertThat(unhealthy.getDedupKey()).isEqualTo("endpoint:" + endpointId);
      assertThat(unhealthy.getSeverity()).isEqualTo(AlertSeverity.ERROR);
      assertThat(unhealthy.getTitle()).contains("crm").doesNotContain("127.0.0.1");

      WebhooksTestSupport.CLOCK.advance(Duration.ofMinutes(11));
      EndpointHealth.promoteToProbation(WebhooksRuntime.config());
      receiver.respond(r -> Receiver.Response.of(200));
      new DeliveryEngine().runOnce();
      assertThat(QAlerts.RESOLVED).containsExactly(WebhooksAlerts.TYPE_UNHEALTHY + "|endpoint:" + endpointId);

      WebhooksAlerts.deadLetter(new QRecord().withValue("id", endpointId).withValue("name", "crm"), new QRecord());
      WebhooksAlerts.disabled(new QRecord().withValue("id", endpointId).withValue("name", "crm"), 3);
      assertThat(QAlerts.RAISED).extracting(AlertSignal::getAlertTypeName).contains(WebhooksAlerts.TYPE_DEAD_LETTER, WebhooksAlerts.TYPE_DISABLED);
   }
}
