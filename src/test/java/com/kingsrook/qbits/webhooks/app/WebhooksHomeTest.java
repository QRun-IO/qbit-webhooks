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

package com.kingsrook.qbits.webhooks.app;


import java.util.Map;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport.Receiver;
import com.kingsrook.qbits.webhooks.delivery.DeliveryEngine;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.permissions.WebhooksPermissions;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The home dashboard (real data only) and the secret-value widget.
 *******************************************************************************/
class WebhooksHomeTest
{
   private QInstance instance;
   private Receiver  receiver;



   /*******************************************************************************
    ** Setup.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      instance = WebhooksTestSupport.setUp(config -> config.withTablePathPrefix("/admin/webhooks"));
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
    ** Render the home.
    *******************************************************************************/
   private static String home() throws Exception
   {
      return (((RawHTML) new WebhooksApp.HomeRenderer().render(new RenderWidgetInput()).getWidgetData()).getHtml());
   }



   /*******************************************************************************
    ** Empty: says so, with a way forward.
    *******************************************************************************/
   @Test
   void emptyHome() throws Exception
   {
      String html = home();
      assertThat(html).doesNotContain("Endpoints needing attention").contains("No delivery attempts in the last 24 hours")
         .contains("No dead letters").contains("None yet");
   }



   /*******************************************************************************
    ** With traffic: counts, latency, the chart, attention and dead letters.
    *******************************************************************************/
   @Test
   void homeWithData() throws Exception
   {
      Integer good = WebhooksTestSupport.endpoint("good", receiver.url("/ok"));
      Integer bad  = WebhooksTestSupport.endpoint("bad <b>", receiver.url("/fail"));
      receiver.respond(r -> r.path().equals("/fail") ? Receiver.Response.of(500) : Receiver.Response.of(200));
      WebhooksTestSupport.subscribe(good, "person.inserted", s -> { });
      WebhooksTestSupport.subscribe(bad, "person.inserted", s -> { });
      for(int i = 0; i < 3; i++)
      {
         WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "P" + i));
      }
      new DeliveryEngine().runOnce();
      QRecord deadOne = WebhooksTestSupport.all(WebhooksTables.DELIVERY).stream().filter(d -> d.getValueInteger("endpointId").equals(bad)).findFirst().orElseThrow();
      SystemWrites.updateOne(WebhooksTables.DELIVERY, new QRecord().withValue("id", deadOne.getValue("id")).withValue("status", "DEAD_LETTER")
         .withValue("lastError", "HTTP 500").withValue("deadLetterDate", WebhooksTestSupport.CLOCK.instant()));

      WebhooksHomeData data = WebhooksHomeData.load();
      assertThat(data.getDelivered24h()).isEqualTo(3);
      assertThat(data.getFailedAttempts24h()).isEqualTo(3);
      assertThat(data.getDeadLetters()).isEqualTo(1);
      assertThat(data.getP50Millis()).isNotNull();

      String html = home();
      assertThat(html).contains("Delivered (24 h)").contains("attempts in the last 24 hours, by hour").doesNotContain("Endpoints needing attention").doesNotContain("bad <b>")
         .contains("/admin/webhooks/webhooks_delivery/" + deadOne.getValue("id")).contains("Replay");
      ////////////////////////////////////////////////////////////////
      // readability rules: no fixed widths that force a scroll bar   //
      ////////////////////////////////////////////////////////////////
      assertThat(html).doesNotContain("overflow-x").doesNotContain("overflow:auto").doesNotContain("<style").contains("minmax(min(100%");
   }



   /*******************************************************************************
    ** The secret value: admins only, behind a toggle; references not shown.
    *******************************************************************************/
   @Test
   void secretValueWidget() throws Exception
   {
      Integer endpointId = WebhooksTestSupport.endpoint("e", "http://127.0.0.1:9/");
      QRecord secret = WebhooksTestSupport.all(WebhooksTables.SECRET).get(0);
      Map<String, String> params = Map.of("id", String.valueOf(secret.getValue("id")));

      String html = WebhooksApp.SecretValueRenderer.html(params);
      assertThat(html).contains("<details>").contains("whsec_");

      QContext.init(instance, new QSession().withPermissions(WebhooksPermissions.MANAGE));
      assertThat(WebhooksApp.SecretValueRenderer.html(params)).doesNotContain("whsec_").contains("Only webhook administrators");

      QContext.init(instance, WebhooksTestSupport.adminSession());
      QRecord reference = WebhooksTestSupport.insert(WebhooksTables.SECRET, new QRecord().withValue("endpointId", endpointId).withValue("source", "REFERENCE")
         .withValue("secretRef", "SOME_ENV_VAR"));
      assertThat(WebhooksApp.SecretValueRenderer.html(Map.of("id", String.valueOf(reference.getValue("id"))))).contains("SOME_ENV_VAR").doesNotContain("whsec_");
   }
}
