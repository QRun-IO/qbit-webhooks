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


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.EventSource;
import com.kingsrook.qbits.webhooks.registry.WebhookEventKind;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qqq.backend.core.actions.processes.listeners.ProcessLifecycleListenerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The in-process process source: queues {@code process.<name>.completed} and
 ** {@code process.<name>.failed} events for the processes in the config's
 ** {@code processEventSources}.
 **
 ** <p>The payload carries the process name, label, run id, table and (for a
 ** failure) the error message. It never carries the process's input values,
 ** which can hold anything.</p>
 *******************************************************************************/
public class WebhooksProcessListener implements ProcessLifecycleListenerInterface
{
   private static final QLogger LOG = QLogger.getLogger(WebhooksProcessListener.class);



   /*******************************************************************************
    ** True for processes with registered types.
    *******************************************************************************/
   @Override
   public boolean appliesTo(String processName)
   {
      if(processName == null || !WebhooksRuntime.isInstalled())
      {
         return (false);
      }
      WebhooksRegistry registry = WebhooksRegistry.of(QContext.getQInstance());
      return (!registry.forProcess(processName, WebhookEventKind.PROCESS_COMPLETED).isEmpty() || !registry.forProcess(processName, WebhookEventKind.PROCESS_FAILED).isEmpty());
   }



   /*******************************************************************************
    ** Nothing on start.
    *******************************************************************************/
   @Override
   public void onProcessStarted(RunProcessInput input)
   {
      // only completion and failure are webhook events
   }



   /*******************************************************************************
    ** Completed. A run that pauses for a screen is not complete yet.
    *******************************************************************************/
   @Override
   public void onProcessCompleted(RunProcessInput input, RunProcessOutput output)
   {
      if(output != null && output.getProcessState() != null && output.getProcessState().getNextStepName().isPresent())
      {
         return;
      }
      queue(input, WebhookEventKind.PROCESS_COMPLETED, null);
   }



   /*******************************************************************************
    ** Failed.
    *******************************************************************************/
   @Override
   public void onProcessFailed(RunProcessInput input, Exception exception)
   {
      queue(input, WebhookEventKind.PROCESS_FAILED, exception == null ? "Unknown error" : exception.getMessage());
   }



   /*******************************************************************************
    ** Build and queue the events.
    *******************************************************************************/
   private static void queue(RunProcessInput input, WebhookEventKind kind, String error)
   {
      try
      {
         WebhooksQBitConfig config = WebhooksRuntime.config();
         if(config == null || input == null)
         {
            return;
         }
         String                 processName = input.getProcessName();
         List<WebhookEventType> types       = WebhooksRegistry.of(QContext.getQInstance()).forProcess(processName, kind);
         if(types.isEmpty())
         {
            return;
         }
         QProcessMetaData    process = QContext.getQInstance().getProcess(processName);
         Map<String, Object> data    = new LinkedHashMap<>();
         data.put("processName", processName);
         data.put("processLabel", process == null ? processName : process.getLabel());
         data.put("processUUID", input.getProcessUUID());
         data.put("tableName", process == null ? null : process.getTableName());
         if(error != null)
         {
            data.put("error", error.length() > 1000 ? error.substring(0, 1000) : error);
         }
         List<WebhookEvent> events = new ArrayList<>();
         for(WebhookEventType type : types)
         {
            events.add(new WebhookEvent()
               .withEventTypeName(type.getName())
               .withSource(EventSource.PROCESS)
               .withSourceUri("qqq://" + config.getSourceName() + "/process/" + processName)
               .withSourceEventId(input.getProcessUUID() == null ? java.util.UUID.randomUUID().toString() : input.getProcessUUID() + "-" + kind.getSuffix())
               .withOccurredAt(WebhooksRuntime.clock().instant())
               .withData(data));
         }
         DeliveryOutbox.enqueue(events, null);
      }
      catch(Exception e)
      {
         LOG.warn("Could not queue webhook deliveries for a process event", e, logPair("process", input == null ? null : input.getProcessName()));
      }
   }
}
