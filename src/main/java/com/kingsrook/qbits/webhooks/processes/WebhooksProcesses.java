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

package com.kingsrook.qbits.webhooks.processes;


import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.delivery.DeliveryEngine;
import com.kingsrook.qbits.webhooks.delivery.EndpointHealth;
import com.kingsrook.qbits.webhooks.events.DeliveryOutbox;
import com.kingsrook.qbits.webhooks.events.WebhookEvent;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.ActiveStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.DeliveryStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.EventSource;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.permissions.WebhooksPermissions;
import com.kingsrook.qbits.webhooks.secrets.EndpointSecrets;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.QScheduleMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ExtractViaQueryStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;


/*******************************************************************************
 ** The qbit's processes. People run them as table or record actions (never
 ** from the nav), each with the standard review and result screens; three
 ** run on the scheduler.
 **
 ** <table>
 **   <caption>Processes</caption>
 **   <tr><th>Name</th><th>Where</th><th>Permission</th></tr>
 **   <tr><td>webhooks_send_now</td><td>Deliveries (selected)</td><td>manage</td></tr>
 **   <tr><td>webhooks_replay</td><td>Deliveries (selected)</td><td>manage</td></tr>
 **   <tr><td>webhooks_cancel</td><td>Deliveries (selected)</td><td>manage</td></tr>
 **   <tr><td>webhooks_send_test</td><td>Endpoint (record)</td><td>manage</td></tr>
 **   <tr><td>webhooks_rotate_secret</td><td>Endpoint (record)</td><td>admin</td></tr>
 **   <tr><td>webhooks_delete_deliveries</td><td>Deliveries (selected, finished)</td><td>admin</td></tr>
 **   <tr><td>webhooks_deliver</td><td>scheduler</td><td>manage</td></tr>
 **   <tr><td>webhooks_maintain</td><td>scheduler (health, secrets)</td><td>admin</td></tr>
 **   <tr><td>webhooks_retention</td><td>scheduler (daily purge)</td><td>admin</td></tr>
 ** </table>
 *******************************************************************************/
public final class WebhooksProcesses
{
   public static final String SEND_NOW          = "webhooks_send_now";
   public static final String REPLAY            = "webhooks_replay";
   public static final String CANCEL            = "webhooks_cancel";
   public static final String SEND_TEST         = "webhooks_send_test";
   public static final String ROTATE_SECRET     = "webhooks_rotate_secret";
   public static final String DELETE_DELIVERIES = "webhooks_delete_deliveries";
   public static final String DELIVER           = "webhooks_deliver";
   public static final String MAINTAIN          = "webhooks_maintain";
   public static final String RETENTION         = "webhooks_retention";

   /** The custom event type a test send uses. */
   public static final String TEST_EVENT_TYPE = "webhooks.test";

   private static final Map<String, String> PERMISSIONS = Map.of(
      SEND_NOW, WebhooksPermissions.MANAGE,
      REPLAY, WebhooksPermissions.MANAGE,
      CANCEL, WebhooksPermissions.MANAGE,
      SEND_TEST, WebhooksPermissions.MANAGE,
      ROTATE_SECRET, WebhooksPermissions.ADMIN,
      DELETE_DELIVERIES, WebhooksPermissions.ADMIN,
      DELIVER, WebhooksPermissions.MANAGE,
      MAINTAIN, WebhooksPermissions.ADMIN,
      RETENTION, WebhooksPermissions.ADMIN);

   private static final List<String> FINAL_STATUSES = List.of(DeliveryStatus.DELIVERED.name(), DeliveryStatus.DEAD_LETTER.name(), DeliveryStatus.CANCELLED.name());



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksProcesses()
   {
   }



   /*******************************************************************************
    ** The permission a process needs.
    *******************************************************************************/
   public static String permissionFor(String processName)
   {
      return (PERMISSIONS.getOrDefault(processName, WebhooksPermissions.ADMIN));
   }



   /*******************************************************************************
    ** All processes.
    *******************************************************************************/
   public static List<QProcessMetaData> processes(WebhooksQBitConfig config)
   {
      List<QProcessMetaData> list = new ArrayList<>();
      list.add(etl(SEND_NOW, "Send Now", "send", WebhooksTables.DELIVERY, SendNowTransform.class, SendNowLoad.class,
         "These deliveries will be sent now (an attempt is made even if the endpoint is unhealthy)."));
      list.add(etl(REPLAY, "Replay", "replay", WebhooksTables.DELIVERY, ReplayTransform.class, ReplayLoad.class,
         "These deliveries will be queued again with the same event id and a fresh set of attempts."));
      list.add(etl(CANCEL, "Cancel Delivery", "cancel", WebhooksTables.DELIVERY, CancelTransform.class, CancelLoad.class,
         "These deliveries will not be sent."));
      list.add(etl(DELETE_DELIVERIES, "Delete Deliveries", "delete", WebhooksTables.DELIVERY, DeleteTransform.class, DeleteLoad.class,
         "These finished deliveries and their attempts will be deleted."));
      list.add(etl(SEND_TEST, "Send Test Event", "science", WebhooksTables.ENDPOINT, SendTestTransform.class, SendTestLoad.class,
         "A signed test event (type " + TEST_EVENT_TYPE + ") will be sent to this endpoint now."));
      list.add(etl(ROTATE_SECRET, "Rotate Signing Secret", "key", WebhooksTables.ENDPOINT, RotateTransform.class, RotateLoad.class,
         "A new signing secret will be created. The current one keeps signing for " + config.getRotationOverlapHours() + " hours."));

      list.add(scheduled(config, DELIVER, "Deliver Webhooks", DeliverStep.class, config.getDeliveryIntervalSeconds()));
      list.add(scheduled(config, MAINTAIN, "Maintain Webhook Endpoints", MaintainStep.class, 300));
      list.add(scheduled(config, RETENTION, "Purge Old Webhook Deliveries", RetentionStep.class, 86400));
      return (list);
   }



   /*******************************************************************************
    ** A streamed ETL table or record action.
    *******************************************************************************/
   static QProcessMetaData etl(String name, String label, String icon, String table, Class<? extends AbstractTransformStep> transform,
      Class<? extends AbstractLoadStep> load, String preview)
   {
      Map<String, Serializable> values = new HashMap<>();
      values.put(StreamedETLWithFrontendProcess.FIELD_SOURCE_TABLE, table);
      values.put(StreamedETLWithFrontendProcess.FIELD_DESTINATION_TABLE, table);
      values.put(StreamedETLWithFrontendProcess.FIELD_SUPPORTS_FULL_VALIDATION, true);
      values.put(StreamedETLWithFrontendProcess.FIELD_DO_FULL_VALIDATION, true);
      values.put(StreamedETLWithFrontendProcess.FIELD_PREVIEW_MESSAGE, preview);
      QProcessMetaData process = StreamedETLWithFrontendProcess.defineProcessMetaData(ExtractViaQueryStep.class, transform, load, values);
      process.setName(name);
      process.setLabel(label);
      process.setIcon(new QIcon(icon));
      process.setTableName(table);
      process.setPermissionRules(WebhooksTables.permissionRules());
      return (process);
   }



   /*******************************************************************************
    ** A hidden backend process on the scheduler (when one is configured).
    *******************************************************************************/
   static QProcessMetaData scheduled(WebhooksQBitConfig config, String name, String label, Class<? extends BackendStep> step, int seconds)
   {
      QProcessMetaData process = new QProcessMetaData()
         .withName(name)
         .withLabel(label)
         .withIsHidden(true)
         .withPermissionRules(WebhooksTables.permissionRules())
         .withStep(new QBackendStepMetaData().withName("run").withCode(new QCodeReference(step)));
      if(config.getSchedulerName() != null)
      {
         process.withSchedule(new QScheduleMetaData().withSchedulerName(config.getSchedulerName()).withRepeatSeconds(seconds).withInitialDelaySeconds(Math.min(seconds, 30)));
      }
      return (process);
   }



   /*******************************************************************************
    ** A summary line with the four tenses.
    *******************************************************************************/
   static ProcessSummaryLine line(Status status, String singularFuture, String pluralFuture, String singularPast, String pluralPast)
   {
      ProcessSummaryLine line = new ProcessSummaryLine(status, 0, null);
      line.setSingularFutureMessage(singularFuture);
      line.setPluralFutureMessage(pluralFuture);
      line.setSingularPastMessage(singularPast);
      line.setPluralPastMessage(pluralPast);
      return (line);
   }



   /*******************************************************************************
    ** A one-off info line.
    *******************************************************************************/
   static ProcessSummaryLine info(Status status, String message)
   {
      return (new ProcessSummaryLine(status, null, message));
   }



   /*******************************************************************************
    ** Base transform: lines for "will happen" and "skipped, with reason".
    *******************************************************************************/
   abstract static class FilteringTransform extends AbstractTransformStep implements ProcessSummaryProviderInterface
   {
      protected final ProcessSummaryLine ok;
      protected final Map<String, ProcessSummaryLine> skipped = new LinkedHashMap<>();



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      FilteringTransform(String singularFuture, String pluralFuture, String singularPast, String pluralPast)
      {
         ok = line(Status.OK, singularFuture, pluralFuture, singularPast, pluralPast);
      }



      /*******************************************************************************
       ** Why a record is skipped, or null to keep it.
       *******************************************************************************/
      abstract String skipReason(QRecord record) throws QException;



      /*******************************************************************************
       ** Sort the page.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         for(QRecord record : input.getRecords())
         {
            String reason = skipReason(record);
            if(reason == null)
            {
               ok.incrementCountAndAddPrimaryKey(record.getValue("id"));
               output.getRecords().add(record);
            }
            else
            {
               skipped.computeIfAbsent(reason, r -> line(Status.INFO, "record " + r + " (no change)", "records " + r + " (no change)",
                  "record " + r + " (no change)", "records " + r + " (no change)")).incrementCountAndAddPrimaryKey(record.getValue("id"));
            }
         }
      }



      /*******************************************************************************
       ** Summary.
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();
         ok.addSelfToListIfAnyCount(lines);
         skipped.values().forEach(l -> l.addSelfToListIfAnyCount(lines));
         return (lines);
      }
   }



   /*******************************************************************************
    ** Send Now: unfinished deliveries.
    *******************************************************************************/
   public static class SendNowTransform extends FilteringTransform
   {
      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      public SendNowTransform()
      {
         super("delivery will be sent now", "deliveries will be sent now", "delivery was attempted", "deliveries were attempted");
      }



      /*******************************************************************************
       ** Skip finished ones.
       *******************************************************************************/
      @Override
      String skipReason(QRecord record)
      {
         String status = record.getValueString("status");
         if(DeliveryStatus.DELIVERED.name().equals(status))
         {
            return ("was already delivered (use Replay to send it again)");
         }
         if(DeliveryStatus.DEAD_LETTER.name().equals(status))
         {
            return ("is a dead letter (use Replay)");
         }
         if(DeliveryStatus.CANCELLED.name().equals(status))
         {
            return ("is cancelled");
         }
         return (null);
      }
   }



   /*******************************************************************************
    ** Send Now: claim and send each, recording results.
    *******************************************************************************/
   public static class SendNowLoad extends AbstractLoadStep implements ProcessSummaryProviderInterface
   {
      private final ProcessSummaryLine delivered = line(Status.OK, "", "", "delivery was delivered", "deliveries were delivered");
      private final ProcessSummaryLine failed    = line(Status.ERROR, "", "", "delivery failed (see its attempts; it will be retried)",
         "deliveries failed (see their attempts; they will be retried)");
      private final ProcessSummaryLine busy      = line(Status.INFO, "", "", "delivery was being sent by another runner",
         "deliveries were being sent by another runner");



      /*******************************************************************************
       ** Send.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         for(QRecord record : input.getRecords())
         {
            DeliveryEngine.SendResult result = new DeliveryEngine().sendNow(record.getValueInteger("id"));
            switch(result)
            {
               case DELIVERED -> delivered.incrementCountAndAddPrimaryKey(record.getValue("id"));
               case CLAIMED_ELSEWHERE, NOT_SENT -> busy.incrementCountAndAddPrimaryKey(record.getValue("id"));
               default -> failed.incrementCountAndAddPrimaryKey(record.getValue("id"));
            }
            output.getRecords().add(record);
         }
      }



      /*******************************************************************************
       ** Summary.
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();
         delivered.addSelfToListIfAnyCount(lines);
         failed.addSelfToListIfAnyCount(lines);
         busy.addSelfToListIfAnyCount(lines);
         if(getTransformStep() instanceof FilteringTransform transform)
         {
            transform.skipped.values().forEach(l -> l.addSelfToListIfAnyCount(lines));
         }
         return (lines);
      }
   }



   /*******************************************************************************
    ** Replay: finished deliveries.
    *******************************************************************************/
   public static class ReplayTransform extends FilteringTransform
   {
      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      public ReplayTransform()
      {
         super("delivery will be queued again", "deliveries will be queued again", "delivery was queued again", "deliveries were queued again");
      }



      /*******************************************************************************
       ** Skip unfinished ones.
       *******************************************************************************/
      @Override
      String skipReason(QRecord record)
      {
         return (FINAL_STATUSES.contains(record.getValueString("status")) ? null : "is still being delivered");
      }
   }



   /*******************************************************************************
    ** Replay: back to PENDING with a fresh attempt budget.
    *******************************************************************************/
   public static class ReplayLoad extends AbstractLoadStep
   {
      /*******************************************************************************
       ** Queue again.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         Instant       now     = WebhooksRuntime.clock().instant();
         List<QRecord> updates = new ArrayList<>();
         for(QRecord record : input.getRecords())
         {
            Integer replays = record.getValueInteger("replayCount");
            updates.add(new QRecord().withValue("id", record.getValue("id"))
               .withValue("status", DeliveryStatus.PENDING.name())
               .withValue("cycleAttemptCount", 0)
               .withValue("replayCount", (replays == null ? 0 : replays) + 1)
               .withValue("nextAttemptDate", now)
               .withValue("deadLetterDate", null)
               .withValue("deliveredDate", null));
         }
         output.getRecords().addAll(SystemWrites.update(WebhooksTables.DELIVERY, updates, null));
      }
   }



   /*******************************************************************************
    ** Cancel: unfinished, not in flight.
    *******************************************************************************/
   public static class CancelTransform extends FilteringTransform
   {
      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      public CancelTransform()
      {
         super("delivery will be cancelled", "deliveries will be cancelled", "delivery was cancelled", "deliveries were cancelled");
      }



      /*******************************************************************************
       ** Only pending or waiting.
       *******************************************************************************/
      @Override
      String skipReason(QRecord record)
      {
         String status = record.getValueString("status");
         if(DeliveryStatus.SENDING.name().equals(status))
         {
            return ("is being sent right now");
         }
         return (FINAL_STATUSES.contains(status) ? "is already finished" : null);
      }
   }



   /*******************************************************************************
    ** Cancel.
    *******************************************************************************/
   public static class CancelLoad extends AbstractLoadStep
   {
      /*******************************************************************************
       ** Mark cancelled.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         List<QRecord> updates = new ArrayList<>();
         for(QRecord record : input.getRecords())
         {
            updates.add(new QRecord().withValue("id", record.getValue("id")).withValue("status", DeliveryStatus.CANCELLED.name()).withValue("nextAttemptDate", null));
         }
         output.getRecords().addAll(SystemWrites.update(WebhooksTables.DELIVERY, updates, null));
      }
   }



   /*******************************************************************************
    ** Delete: finished deliveries only.
    *******************************************************************************/
   public static class DeleteTransform extends FilteringTransform
   {
      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      public DeleteTransform()
      {
         super("delivery will be deleted", "deliveries will be deleted", "delivery was deleted", "deliveries were deleted");
      }



      /*******************************************************************************
       ** Only finished.
       *******************************************************************************/
      @Override
      String skipReason(QRecord record)
      {
         return (FINAL_STATUSES.contains(record.getValueString("status")) ? null : "is not finished (cancel it first)");
      }
   }



   /*******************************************************************************
    ** Delete deliveries and their attempts.
    *******************************************************************************/
   public static class DeleteLoad extends AbstractLoadStep
   {
      /*******************************************************************************
       ** Delete.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         List<Serializable> ids = input.getRecords().stream().map(r -> r.getValue("id")).toList();
         if(!ids.isEmpty())
         {
            Retention.deleteDeliveries(new ArrayList<>(ids));
         }
         output.getRecords().addAll(input.getRecords());
      }
   }



   /*******************************************************************************
    ** Send Test: an endpoint that is not disabled.
    *******************************************************************************/
   public static class SendTestTransform extends FilteringTransform
   {
      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      public SendTestTransform()
      {
         super("endpoint will get a test event", "endpoints will get a test event", "endpoint was sent a test event", "endpoints were sent a test event");
      }



      /*******************************************************************************
       ** Skip disabled.
       *******************************************************************************/
      @Override
      String skipReason(QRecord record)
      {
         return (ActiveStatus.DISABLED.name().equals(record.getValueString("activeStatus")) ? "is disabled" : null);
      }
   }



   /*******************************************************************************
    ** Send Test: queue a test delivery and send it now.
    *******************************************************************************/
   public static class SendTestLoad extends AbstractLoadStep implements ProcessSummaryProviderInterface
   {
      private final List<ProcessSummaryLineInterface> results = new ArrayList<>();



      /*******************************************************************************
       ** Send.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         for(QRecord endpoint : input.getRecords())
         {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("message", "This is a test event from QQQ webhooks.");
            data.put("endpoint", endpoint.getValueString("name"));
            WebhookEvent event = new WebhookEvent()
               .withEventTypeName(TEST_EVENT_TYPE)
               .withSource(EventSource.TEST)
               .withSourceUri("qqq://" + WebhooksRuntime.config().getSourceName() + "/test")
               .withOccurredAt(WebhooksRuntime.clock().instant())
               .withData(data);
            QRecord delivery = DeliveryOutbox.enqueueDirect(event, endpoint.getValueInteger("id"), null);
            DeliveryEngine.SendResult result = new DeliveryEngine().sendNow(delivery.getValueInteger("id"));
            QRecord after = SystemWrites.query(WebhooksTables.DELIVERY, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, delivery.getValue("id")))).get(0);
            String name = endpoint.getValueString("name");
            if(result == DeliveryEngine.SendResult.DELIVERED)
            {
               results.add(info(Status.OK, name + ": delivered (HTTP " + after.getValue("lastHttpStatus") + ")."));
            }
            else
            {
               results.add(info(Status.ERROR, name + ": not delivered. " + (after.getValueString("lastError") == null ? "" : after.getValueString("lastError"))
                  + " (delivery " + after.getValue("id") + " has the details)"));
            }
            output.getRecords().add(endpoint);
         }
      }



      /*******************************************************************************
       ** Summary.
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         return (new ArrayList<>(results));
      }
   }



   /*******************************************************************************
    ** Rotate: every selected endpoint (admin only).
    *******************************************************************************/
   public static class RotateTransform extends FilteringTransform
   {
      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      public RotateTransform()
      {
         super("endpoint will get a new signing secret", "endpoints will get a new signing secret", "endpoint got a new signing secret",
            "endpoints got a new signing secret");
      }



      /*******************************************************************************
       ** Refuse when secrets cannot be generated.
       *******************************************************************************/
      @Override
      String skipReason(QRecord record) throws QException
      {
         return (EndpointSecrets.canGenerate() ? null : "cannot get a generated secret: the encryption key is not set (add an environment reference instead)");
      }
   }



   /*******************************************************************************
    ** Rotate. The secret itself is never put in the result (result screens can
    ** be stored by process tracing): it is shown on the new secret's record to
    ** webhook admins.
    *******************************************************************************/
   public static class RotateLoad extends AbstractLoadStep implements ProcessSummaryProviderInterface
   {
      private final ProcessSummaryLine rotated = line(Status.OK, "", "", "endpoint has a new signing secret: open its Signing Secrets to copy the value",
         "endpoints have new signing secrets: open their Signing Secrets to copy the values");



      /*******************************************************************************
       ** Rotate.
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         for(QRecord endpoint : input.getRecords())
         {
            EndpointSecrets.rotate(endpoint.getValueInteger("id"), "Rotated");
            rotated.incrementCountAndAddPrimaryKey(endpoint.getValue("id"));
            output.getRecords().add(endpoint);
         }
      }



      /*******************************************************************************
       ** Summary.
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();
         rotated.addSelfToListIfAnyCount(lines);
         return (lines);
      }
   }



   /*******************************************************************************
    ** Scheduled: deliver.
    *******************************************************************************/
   public static class DeliverStep implements BackendStep
   {
      /*******************************************************************************
       ** Run.
       *******************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         DeliveryEngine.RunSummary summary = new DeliveryEngine().runOnce();
         output.addValue("delivered", summary.getDelivered());
         output.addValue("failed", summary.getFailed());
      }
   }



   /*******************************************************************************
    ** Scheduled: probation and secret retirement.
    *******************************************************************************/
   public static class MaintainStep implements BackendStep
   {
      /*******************************************************************************
       ** Run.
       *******************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         output.addValue("probation", EndpointHealth.promoteToProbation(WebhooksRuntime.config()).size());
         output.addValue("retiredSecrets", EndpointSecrets.retireExpired());
      }
   }



   /*******************************************************************************
    ** Scheduled: retention.
    *******************************************************************************/
   public static class RetentionStep implements BackendStep
   {
      /*******************************************************************************
       ** Run.
       *******************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         output.addValue("deleted", Retention.purge(WebhooksRuntime.config()));
      }
   }



   /*******************************************************************************
    ** Retention purge.
    *******************************************************************************/
   public static final class Retention
   {
      /*******************************************************************************
       ** Utility class.
       *******************************************************************************/
      private Retention()
      {
      }



      /*******************************************************************************
       ** Delete finished deliveries (and their attempts) past their retention,
       ** and attempts past theirs. 0 days means keep forever. Returns the
       ** number of deliveries deleted.
       *******************************************************************************/
      public static int purge(WebhooksQBitConfig config) throws QException
      {
         Instant now     = WebhooksRuntime.clock().instant();
         int     deleted = 0;
         deleted += purgeStatus(List.of(DeliveryStatus.DELIVERED.name(), DeliveryStatus.CANCELLED.name()), config.getDeliveredRetentionDays(), now);
         deleted += purgeStatus(List.of(DeliveryStatus.DEAD_LETTER.name()), config.getDeadLetterRetentionDays(), now);
         Integer attemptDays = config.getAttemptRetentionDays();
         if(attemptDays != null && attemptDays > 0)
         {
            SystemWrites.delete(WebhooksTables.ATTEMPT, new QQueryFilter()
               .withCriteria(new QFilterCriteria("startDate", QCriteriaOperator.LESS_THAN, now.minus(Duration.ofDays(attemptDays))))
               .withCriteria(new QFilterCriteria("outcome", QCriteriaOperator.NOT_EQUALS, "IN_FLIGHT")));
         }
         return (deleted);
      }



      /*******************************************************************************
       ** Delete deliveries in some statuses older than a number of days.
       *******************************************************************************/
      private static int purgeStatus(List<String> statuses, Integer days, Instant now) throws QException
      {
         if(days == null || days <= 0)
         {
            return (0);
         }
         List<QRecord> old = SystemWrites.query(WebhooksTables.DELIVERY, new QQueryFilter()
            .withCriteria(new QFilterCriteria("status", QCriteriaOperator.IN, new ArrayList<>(statuses)))
            .withCriteria(new QFilterCriteria("modifyDate", QCriteriaOperator.LESS_THAN, now.minus(Duration.ofDays(days))))
            .withLimit(5000));
         List<Serializable> ids = old.stream().map(r -> r.getValue("id")).toList();
         if(ids.isEmpty())
         {
            return (0);
         }
         deleteDeliveries(new ArrayList<>(ids));
         return (ids.size());
      }



      /*******************************************************************************
       ** Delete deliveries by id, with their attempts.
       *******************************************************************************/
      public static void deleteDeliveries(List<Serializable> ids) throws QException
      {
         SystemWrites.delete(WebhooksTables.ATTEMPT, new QQueryFilter(new QFilterCriteria("deliveryId", QCriteriaOperator.IN, ids)));
         SystemWrites.delete(WebhooksTables.DELIVERY, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.IN, ids)));
      }
   }
}
