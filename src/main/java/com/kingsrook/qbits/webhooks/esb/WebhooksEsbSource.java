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

package com.kingsrook.qbits.webhooks.esb;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.events.DeliveryOutbox;
import com.kingsrook.qbits.webhooks.events.WebhookEvent;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.EventSource;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.registry.WebhookEventKind;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.QSupplementalInstanceMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.esb.envelope.EsbEvent;
import com.kingsrook.qqq.esb.model.EsbInstanceMetaData;
import com.kingsrook.qqq.esb.model.EsbProcessMetaData;
import com.kingsrook.qqq.esb.model.EsbTrigger;
import com.kingsrook.qqq.esb.runtime.EsbTriggerHandler;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The ESB source: a hidden process with an ESB trigger on each destination
 ** in the config's {@code esbDestinations} (topics such as
 ** {@code kof22.cms.tables}), turning CloudEvents into webhook deliveries.
 **
 ** <ul>
 **   <li>{@code qqq.table.<table>.inserted|updated|deleted} becomes
 **       {@code <table>.inserted|updated|deleted};</li>
 **   <li>{@code qqq.process.<name>.completed|failed} becomes
 **       {@code process.<name>.completed|failed};</li>
 **   <li>any other type is used as-is when it is a registered custom type.</li>
 ** </ul>
 **
 ** <p>No double sends: events this instance published itself are skipped
 ** (the in-process listeners already queued them), and every delivery's
 ** dedup key starts with the CloudEvent id, so a redelivered message queues
 ** nothing new. Events of unregistered types are ignored.</p>
 **
 ** <p>Requires {@code qqq-esb} (an optional dependency of this qbit). Call
 ** {@link #configure(QInstance)} after the ESB destinations are defined.</p>
 *******************************************************************************/
public final class WebhooksEsbSource
{
   public static final String PROCESS_NAME      = "webhooks_esb_ingest";
   public static final String SUBSCRIPTION_NAME = "webhooks";

   private static final QLogger LOG = QLogger.getLogger(WebhooksEsbSource.class);



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksEsbSource()
   {
   }



   /*******************************************************************************
    ** Add the ingest process and its triggers (no-op without destinations).
    *******************************************************************************/
   public static void configure(QInstance qInstance)
   {
      WebhooksQBitConfig config = WebhooksRuntime.config(qInstance);
      if(config == null || config.getEsbDestinations().isEmpty())
      {
         return;
      }
      QProcessMetaData process = new QProcessMetaData()
         .withName(PROCESS_NAME)
         .withLabel("Queue Webhooks From ESB Events")
         .withIsHidden(true)
         .withPermissionRules(WebhooksTables.permissionRules())
         .withStep(new QBackendStepMetaData().withName("ingest").withCode(new QCodeReference(IngestStep.class)));
      EsbProcessMetaData esb = EsbProcessMetaData.ofOrWithNew(process);
      for(String destination : config.getEsbDestinations())
      {
         esb.withTrigger(new EsbTrigger()
            .withDestinationName(destination)
            .withSubscriptionName(SUBSCRIPTION_NAME)
            .withConcurrency(1)
            .withMaxAttempts(5));
      }
      qInstance.addProcess(process);
      LOG.info("Webhooks ESB source configured", logPair("destinations", config.getEsbDestinations()));
   }



   /*******************************************************************************
    ** The trigger's step.
    *******************************************************************************/
   public static class IngestStep implements BackendStep
   {
      /*******************************************************************************
       ** Queue deliveries for the batch of events.
       *******************************************************************************/
      @Override
      @SuppressWarnings("unchecked")
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         Object value = input.getValue(EsbTriggerHandler.VALUE_ESB_MESSAGES);
         if(!(value instanceof List<?> list))
         {
            return;
         }
         List<WebhookEvent> events = new ArrayList<>();
         for(Object item : list)
         {
            if(item instanceof EsbEvent esbEvent)
            {
               WebhookEvent event = toWebhookEvent(QContext.getQInstance(), esbEvent);
               if(event != null)
               {
                  events.add(event);
               }
            }
         }
         int queued = DeliveryOutbox.enqueue(events, null).size();
         output.addValue("queued", queued);
      }
   }



   /*******************************************************************************
    ** Map a CloudEvent to a webhook event, or null to ignore it.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public static WebhookEvent toWebhookEvent(QInstance qInstance, EsbEvent esbEvent)
   {
      WebhooksQBitConfig config = WebhooksRuntime.config(qInstance);
      if(config == null || esbEvent == null || !StringUtils.hasContent(esbEvent.getType()))
      {
         return (null);
      }
      if(Boolean.TRUE.equals(config.getEsbSkipOwnEvents()) && isOwnEvent(qInstance, esbEvent))
      {
         return (null);
      }

      WebhooksRegistry registry = WebhooksRegistry.of(qInstance);
      String           type     = esbEvent.getType();
      Map<String, Serializable> data = esbEvent.getData() == null ? Map.of() : esbEvent.getData();
      WebhookEvent event = new WebhookEvent()
         .withSource(EventSource.ESB)
         .withSourceEventId(esbEvent.getId())
         .withSourceUri(esbEvent.getSource())
         .withOccurredAt(esbEvent.getTime() == null ? WebhooksRuntime.clock().instant() : esbEvent.getTime());

      if(type.startsWith("qqq.table."))
      {
         String rest   = type.substring("qqq.table.".length());
         int    dot    = rest.lastIndexOf('.');
         if(dot <= 0)
         {
            return (null);
         }
         String table  = rest.substring(0, dot);
         String suffix = rest.substring(dot + 1);
         WebhookEventKind kind = switch(suffix)
         {
            case "inserted" -> WebhookEventKind.TABLE_INSERT;
            case "updated" -> WebhookEventKind.TABLE_UPDATE;
            case "deleted" -> WebhookEventKind.TABLE_DELETE;
            default -> null;
         };
         if(kind == null || registry.get(WebhooksRegistry.tableTypeName(table, kind)) == null)
         {
            return (null);
         }
         QRecord record    = toRecord(table, data.get("record"));
         QRecord oldRecord = toRecord(table, data.get("oldRecord"));
         return (event.withEventTypeName(WebhooksRegistry.tableTypeName(table, kind))
            .withSourceTable(table)
            .withSourceRecordId(esbEvent.getSubject())
            .withRecord(kind == WebhookEventKind.TABLE_DELETE ? null : record)
            .withOldRecord(oldRecord));
      }

      if(type.startsWith("qqq.process."))
      {
         String rest = type.substring("qqq.process.".length());
         int    dot  = rest.lastIndexOf('.');
         if(dot <= 0)
         {
            return (null);
         }
         String process = rest.substring(0, dot);
         String suffix  = rest.substring(dot + 1);
         WebhookEventKind kind = "completed".equals(suffix) ? WebhookEventKind.PROCESS_COMPLETED : "failed".equals(suffix) ? WebhookEventKind.PROCESS_FAILED : null;
         if(kind == null || registry.get(WebhooksRegistry.processTypeName(process, kind)) == null)
         {
            return (null);
         }
         return (event.withEventTypeName(WebhooksRegistry.processTypeName(process, kind)).withData(new LinkedHashMap<>(data)));
      }

      WebhookEventType custom = registry.get(type);
      if(custom == null || custom.getKind() != WebhookEventKind.CUSTOM)
      {
         return (null);
      }
      return (event.withEventTypeName(type).withData(new LinkedHashMap<>(data)));
   }



   /*******************************************************************************
    ** True when this instance published the event (source qqq://<me>/...).
    *******************************************************************************/
   static boolean isOwnEvent(QInstance qInstance, EsbEvent esbEvent)
   {
      EsbInstanceMetaData esb  = QSupplementalInstanceMetaData.of(qInstance, EsbInstanceMetaData.NAME);
      String              name = esb == null || esb.getInstanceName() == null ? "" : esb.getInstanceName();
      return (esbEvent.getSource() != null && esbEvent.getSource().startsWith("qqq://" + name + "/"));
   }



   /*******************************************************************************
    ** A map of values as a record.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static QRecord toRecord(String table, Object values)
   {
      if(!(values instanceof Map<?, ?> map))
      {
         return (null);
      }
      QRecord record = new QRecord().withTableName(table);
      for(Map.Entry<?, ?> entry : map.entrySet())
      {
         if(entry.getKey() != null)
         {
            record.setValue(String.valueOf(entry.getKey()), (Serializable) entry.getValue());
         }
      }
      return (record);
   }
}
