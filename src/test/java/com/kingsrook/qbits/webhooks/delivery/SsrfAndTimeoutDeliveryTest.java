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


import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport.Receiver;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.secrets.EndpointSecrets;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The SSRF policy and timeouts, through the real delivery path.
 *******************************************************************************/
class SsrfAndTimeoutDeliveryTest
{
   private Receiver receiver;



   /*******************************************************************************
    ** Setup.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      WebhooksTestSupport.setUp();
      receiver = new Receiver();
   }



   /*******************************************************************************
    ** Teardown.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      receiver.close();
      WebhooksRuntime.setHostResolver(null);
   }



   /*******************************************************************************
    ** Saving an endpoint at a private, loopback or metadata address without an
    ** allowlist is refused, through the same action the UI and API use.
    *******************************************************************************/
   @Test
   void internalUrlsAreRefusedOnSave() throws Exception
   {
      for(String url : List.of("http://127.0.0.1:8080/x", "http://10.0.0.5/x", "http://169.254.169.254/latest/meta-data", "http://[::1]/x",
         "http://192.168.1.1/x", "ftp://example.com/x", "http://user:pw@example.com/x"))
      {
         QRecord result = new InsertAction().execute(new InsertInput(WebhooksTables.ENDPOINT)
            .withRecords(List.of(new QRecord().withValue("name", "bad " + url).withValue("url", url)))).getRecords().get(0);
         assertThat(result.getErrors()).as(url).isNotEmpty();
      }

      //////////////////////////////////////////////////////////////////////
      // metadata stays blocked even with a matching allowlist entry       //
      //////////////////////////////////////////////////////////////////////
      QRecord metadata = new InsertAction().execute(new InsertInput(WebhooksTables.ENDPOINT).withRecords(List.of(new QRecord().withValue("name", "meta")
         .withValue("url", "http://169.254.169.254/").withValue("allowInternalTargets", true).withValue("internalAllowlist", "169.254.0.0/16")))).getRecords().get(0);
      assertThat(metadata.getErrors()).isNotEmpty();
   }



   /*******************************************************************************
    ** DNS rebinding: the name resolved to a public address when saved and to
    ** 127.0.0.1 at send time. The send is refused (blocked) and nothing
    ** reaches the receiver.
    *******************************************************************************/
   @Test
   void dnsRebindingIsBlockedAtSendTime() throws Exception
   {
      AtomicInteger calls = new AtomicInteger();
      WebhooksRuntime.setHostResolver(host -> calls.incrementAndGet() == 1
         ? new InetAddress[] {InetAddress.getByName("93.184.216.34")}
         : new InetAddress[] {InetAddress.getByName("127.0.0.1")});
      int port = Integer.parseInt(receiver.url("").replaceAll(".*:(\\d+).*", "$1"));
      Integer endpointId = WebhooksTestSupport.insert(WebhooksTables.ENDPOINT, new QRecord().withValue("name", "rebind")
         .withValue("url", "http://rebind.example.com:" + port + "/hook")).getValueInteger("id");
      EndpointSecrets.rotate(endpointId, "test");
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "Mallory"));

      new DeliveryEngine().runOnce();
      assertThat(receiver.requests()).isEmpty();
      QRecord attempt = WebhooksTestSupport.all(WebhooksTables.ATTEMPT).get(0);
      assertThat(attempt.getValueString("outcome")).isEqualTo("BLOCKED");
      assertThat(attempt.getValueString("errorMessage")).contains("127.0.0.1");
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(0).getValueString("status")).isEqualTo("RETRY_WAIT");
      ////////////////////////////////////////////////////////////
      // a policy block is not the receiver's fault: health is unchanged //
      ////////////////////////////////////////////////////////////
      assertThat(WebhooksTestSupport.all(WebhooksTables.ENDPOINT).get(0).getValueInteger("consecutiveFailures")).isEqualTo(0);
   }



   /*******************************************************************************
    ** A redirect (to the metadata address) is not followed.
    *******************************************************************************/
   @Test
   void redirectsAreNotFollowed() throws Exception
   {
      receiver.respond(r -> new Receiver.Response(307, Map.of("Location", "http://169.254.169.254/latest/meta-data"), "", 0));
      Integer endpointId = WebhooksTestSupport.endpoint("redirector", receiver.url("/hook"));
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "Ada"));

      new DeliveryEngine().runOnce();
      assertThat(receiver.requests()).hasSize(1);
      QRecord attempt = WebhooksTestSupport.all(WebhooksTables.ATTEMPT).get(0);
      assertThat(attempt.getValueString("outcome")).isEqualTo("FAILED");
      assertThat(attempt.getValueInteger("httpStatus")).isEqualTo(307);
      assertThat(attempt.getValueString("errorMessage")).contains("redirects are not followed");
   }



   /*******************************************************************************
    ** The endpoint timeout (and httpTimeoutMillis) is honoured.
    *******************************************************************************/
   @Test
   void slowReceiverTimesOut() throws Exception
   {
      receiver.respond(r -> new Receiver.Response(200, Map.of(), "late", 3000));
      Integer endpointId = WebhooksTestSupport.endpoint("slow", receiver.url("/hook"));
      SystemWrites.updateOne(WebhooksTables.ENDPOINT, new QRecord().withValue("id", endpointId).withValue("timeoutSeconds", 1));
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "Ada"));

      long start = System.currentTimeMillis();
      new DeliveryEngine().runOnce();
      assertThat(System.currentTimeMillis() - start).isLessThan(2900);
      QRecord attempt = WebhooksTestSupport.all(WebhooksTables.ATTEMPT).get(0);
      assertThat(attempt.getValueString("outcome")).isEqualTo("FAILED");
      assertThat(attempt.getValueString("errorMessage")).containsIgnoringCase("no response data");
      assertThat(WebhooksTestSupport.all(WebhooksTables.DELIVERY).get(0).getValueString("status")).isEqualTo("RETRY_WAIT");
   }



   /*******************************************************************************
    ** A large response is cut to maxResponseBytes, and the excerpt is stored.
    *******************************************************************************/
   @Test
   void largeResponsesAreCapped() throws Exception
   {
      receiver.respond(r -> new Receiver.Response(500, Map.of(), "x".repeat(200_000), 0));
      Integer endpointId = WebhooksTestSupport.endpoint("big", receiver.url("/hook"));
      WebhooksTestSupport.subscribe(endpointId, "person.inserted", s -> { });
      WebhooksTestSupport.insert(WebhooksTestSupport.PERSON, new QRecord().withValue("firstName", "Ada"));
      new DeliveryEngine().runOnce();
      QRecord attempt = WebhooksTestSupport.all(WebhooksTables.ATTEMPT).get(0);
      assertThat(attempt.getValueString("responseExcerpt")).endsWith("[truncated]");
      assertThat(attempt.getValueString("responseExcerpt").length()).isLessThan(2100);
   }
}
