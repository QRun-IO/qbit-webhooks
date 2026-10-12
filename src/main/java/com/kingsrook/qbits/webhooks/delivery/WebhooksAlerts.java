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


import java.lang.reflect.Method;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Raises and resolves alerts through the QQQ Alerts producer SPI
 ** ({@code com.kingsrook.qqq.backend.core.alerts.QAlerts}, design QRun-IO/qqq
 ** #1090) when it is on the classpath. Without it every call only logs, so the
 ** qbit needs no compile dependency on the Alerts SPI or qbit.
 **
 ** <p>Alert types (dedup key = the endpoint id):</p>
 ** <ul>
 **   <li>{@code webhooks.endpoint.unhealthy} (ERROR): failures in a row
 **       paused deliveries. Resolved by the next success.</li>
 **   <li>{@code webhooks.endpoint.disabled} (ERROR): failed on probation too
 **       often and was disabled.</li>
 **   <li>{@code webhooks.delivery.dead_letter} (WARNING): a delivery ran out of
 **       attempts.</li>
 ** </ul>
 ** <p>Titles and summaries are external-safe: endpoint names and counts only,
 ** never URLs, payloads or response text.</p>
 *******************************************************************************/
public final class WebhooksAlerts
{
   public static final String TYPE_UNHEALTHY   = "webhooks.endpoint.unhealthy";
   public static final String TYPE_DISABLED    = "webhooks.endpoint.disabled";
   public static final String TYPE_DEAD_LETTER = "webhooks.delivery.dead_letter";

   private static final QLogger LOG = QLogger.getLogger(WebhooksAlerts.class);

   private static final String QALERTS      = "com.kingsrook.qqq.backend.core.alerts.QAlerts";
   private static final String ALERT_SIGNAL = "com.kingsrook.qqq.backend.core.alerts.AlertSignal";
   private static final String SEVERITY     = "com.kingsrook.qqq.backend.core.alerts.AlertSeverity";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksAlerts()
   {
   }



   /*******************************************************************************
    ** True when the Alerts SPI is on the classpath.
    *******************************************************************************/
   public static boolean available()
   {
      try
      {
         Class.forName(QALERTS, false, WebhooksAlerts.class.getClassLoader());
         return (true);
      }
      catch(ClassNotFoundException | LinkageError e)
      {
         return (false);
      }
   }



   /*******************************************************************************
    ** An endpoint became unhealthy.
    *******************************************************************************/
   public static void unhealthy(QRecord endpoint, int failures, String lastError)
   {
      raise(TYPE_UNHEALTHY, key(endpoint), "ERROR", "Webhook endpoint " + endpoint.getValueString("name") + " is unhealthy",
         failures + " deliveries failed in a row, so deliveries to it are paused until a probation attempt succeeds.", endpoint);
   }



   /*******************************************************************************
    ** An endpoint was disabled automatically.
    *******************************************************************************/
   public static void disabled(QRecord endpoint, int probationFailures)
   {
      raise(TYPE_DISABLED, key(endpoint), "ERROR", "Webhook endpoint " + endpoint.getValueString("name") + " was disabled",
         "It failed " + probationFailures + " probation attempts. Fix the receiver, then set the endpoint back to Active.", endpoint);
   }



   /*******************************************************************************
    ** A delivery was dead-lettered.
    *******************************************************************************/
   public static void deadLetter(QRecord endpoint, QRecord delivery)
   {
      raise(TYPE_DEAD_LETTER, key(endpoint), "WARNING", "Webhook deliveries to " + endpoint.getValueString("name") + " ran out of retries",
         "Dead-lettered deliveries can be replayed from the Deliveries table.", endpoint);
   }



   /*******************************************************************************
    ** An endpoint recovered.
    *******************************************************************************/
   public static void resolveUnhealthy(QRecord endpoint)
   {
      resolve(TYPE_UNHEALTHY, key(endpoint), "A delivery succeeded");
   }



   /*******************************************************************************
    ** Dedup key.
    *******************************************************************************/
   private static String key(QRecord endpoint)
   {
      return ("endpoint:" + endpoint.getValue("id"));
   }



   /*******************************************************************************
    ** QAlerts.raise(new AlertSignal(type).withDedupKey(..).withSeverity(..)
    ** .withTitle(..).withSummary(..)), or a log line.
    *******************************************************************************/
   static void raise(String type, String dedupKey, String severity, String title, String summary, QRecord endpoint)
   {
      LOG.info("Webhooks alert", logPair("type", type), logPair("dedupKey", dedupKey), logPair("title", title));
      if(!available())
      {
         return;
      }
      try
      {
         ClassLoader loader     = WebhooksAlerts.class.getClassLoader();
         Class<?>    signalType = Class.forName(ALERT_SIGNAL, true, loader);
         Object      signal     = signalType.getConstructor(String.class).newInstance(type);
         signal = signalType.getMethod("withDedupKey", String.class).invoke(signal, dedupKey);
         Class<?> severityType = Class.forName(SEVERITY, true, loader);
         @SuppressWarnings({"unchecked", "rawtypes"})
         Object level = Enum.valueOf((Class<? extends Enum>) severityType.asSubclass(Enum.class), severity);
         signal = signalType.getMethod("withSeverity", severityType).invoke(signal, level);
         signal = signalType.getMethod("withTitle", String.class).invoke(signal, title);
         signal = signalType.getMethod("withSummary", String.class).invoke(signal, summary);
         Method raise = Class.forName(QALERTS, true, loader).getMethod("raise", signalType);
         raise.invoke(null, signal);
      }
      catch(Exception | LinkageError e)
      {
         LOG.info("Could not raise a webhooks alert through the Alerts SPI", logPair("type", type), logPair("reason", e.getClass().getSimpleName()));
      }
   }



   /*******************************************************************************
    ** QAlerts.resolve(type, dedupKey, note), or nothing.
    *******************************************************************************/
   static void resolve(String type, String dedupKey, String note)
   {
      if(!available())
      {
         return;
      }
      try
      {
         Method resolve = Class.forName(QALERTS, true, WebhooksAlerts.class.getClassLoader()).getMethod("resolve", String.class, String.class, String.class);
         resolve.invoke(null, type, dedupKey, note);
      }
      catch(Exception | LinkageError e)
      {
         LOG.info("Could not resolve a webhooks alert through the Alerts SPI", logPair("type", type), logPair("reason", e.getClass().getSimpleName()));
      }
   }
}
