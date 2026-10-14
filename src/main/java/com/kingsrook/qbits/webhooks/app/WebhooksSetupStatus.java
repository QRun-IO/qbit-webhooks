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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.ActiveStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.processes.WebhooksProcesses;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qbits.webhooks.secrets.EndpointSecrets;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** The Setup card on the Webhooks home: each thing an administrator configures
 ** (event types, encryption key, endpoints, secrets, subscriptions), its state
 ** and the next step. Nothing here is seeded: every step is the owner's choice.
 *******************************************************************************/
public final class WebhooksSetupStatus
{
   public static final String SET_UP      = "Set up";
   public static final String NEEDS_SETUP = "Needs setup";
   public static final String OPTIONAL    = "Optional";



   /*******************************************************************************
    ** One line of the card.
    *******************************************************************************/
   public record Step(String label, String state, String next, String href)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksSetupStatus()
   {
   }



   /*******************************************************************************
    ** Read the current state, as the viewer.
    *******************************************************************************/
   public static List<Step> load() throws QException
   {
      WebhooksQBitConfig config     = WebhooksRuntime.config();
      int                eventTypes = 0;
      for(WebhookEventType type : WebhooksRegistry.of(QContext.getQInstance()).getAll())
      {
         if(!WebhooksProcesses.TEST_EVENT_TYPE.equals(type.getName()))
         {
            eventTypes++;
         }
      }

      int          endpoints     = count(WebhooksTables.ENDPOINT);
      int          subscriptions = count(WebhooksTables.SUBSCRIPTION);
      int blocked = 0;
      for(QRecord endpoint : new QueryAction().execute(new QueryInput(WebhooksTables.ENDPOINT)).getRecords())
      {
         ///////////////////////////////////////////////////////////////////////////////////
         // exactly DeliveryEngine's test: active, signing required, and no usable secret //
         ///////////////////////////////////////////////////////////////////////////////////
         if(ActiveStatus.ACTIVE.name().equals(endpoint.getValueString("activeStatus")) && !Boolean.TRUE.equals(endpoint.getValueBoolean("allowUnsigned"))
            && EndpointSecrets.signingKeys(endpoint.getValueInteger("id")).isEmpty())
         {
            blocked++;
         }
      }
      return (steps(config.getTablePathPrefix(), config.getEncryptionKeyEnvName(), eventTypes, EndpointSecrets.canGenerate(), endpoints, blocked, subscriptions));
   }



   /*******************************************************************************
    ** The steps, from the numbers (pure, for tests).
    *******************************************************************************/
   static List<Step> steps(String prefix, String keyEnvName, int eventTypes, boolean canGenerate, int endpoints, int blockedEndpoints, int subscriptions)
   {
      String     endpointHref     = prefix + "/" + WebhooksTables.ENDPOINT;
      String     subscriptionHref = prefix + "/" + WebhooksTables.SUBSCRIPTION;
      List<Step> steps            = new ArrayList<>();
      steps.add(eventTypes > 0
         ? new Step("Event types", SET_UP, eventTypes + " available to subscribe to (from the code-side registry)", subscriptionHref)
         : new Step("Event types", NEEDS_SETUP, "The host lists tables or processes that raise events in the qbit config (tableEventSources, processEventSources), or fires custom events", null));
      steps.add(canGenerate
         ? new Step("Encryption key", SET_UP, "Generated signing secrets can be stored encrypted", null)
         : new Step("Encryption key", OPTIONAL, "Set " + keyEnvName + " to generate secrets here, or use environment-variable secrets", null));
      steps.add(endpoints > 0
         ? new Step("Endpoints", SET_UP, endpoints + (endpoints == 1 ? " endpoint" : " endpoints"), endpointHref)
         : new Step("Endpoints", NEEDS_SETUP, "Add an endpoint: the URL that receives events", endpointHref));
      steps.add(endpoints > 0 && blockedEndpoints == 0
         ? new Step("Signing secrets", SET_UP, "Every endpoint signs its deliveries", endpointHref)
         : new Step("Signing secrets", NEEDS_SETUP, endpoints == 0 ? "Add an endpoint first, then give it a secret"
            : blockedEndpoints + " active endpoint(s) have no usable signing secret, so their deliveries wait: open the endpoint and add one", endpointHref));
      steps.add(subscriptions > 0
         ? new Step("Subscriptions", SET_UP, subscriptions + (subscriptions == 1 ? " subscription" : " subscriptions"), subscriptionHref)
         : new Step("Subscriptions", NEEDS_SETUP, "Subscribe an endpoint to an event type, or nothing is ever sent", subscriptionHref));
      return (steps);
   }



   /*******************************************************************************
    ** Count as the viewer.
    *******************************************************************************/
   private static int count(String table) throws QException
   {
      Integer count = new CountAction().execute(new CountInput(table)).getCount();
      return (count == null ? 0 : count);
   }

}
