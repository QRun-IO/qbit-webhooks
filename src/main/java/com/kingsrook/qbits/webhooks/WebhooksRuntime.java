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

package com.kingsrook.qbits.webhooks;


import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qbits.webhooks.shared.http.SafeHttpClient;
import com.kingsrook.qbits.webhooks.shared.http.SafeHttpPolicy;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;


/*******************************************************************************
 ** Runtime lookups for the qbit.
 **
 ** <p>The config is read from the current QInstance on every call (never held
 ** in a static), so reconfiguring, or two instances in one JVM, never see
 ** stale settings. The only JVM-wide state is the HTTP connection pools (one
 ** per distinct policy) and the clock, which tests replace.</p>
 *******************************************************************************/
public final class WebhooksRuntime
{
   private static final Map<String, SafeHttpClient> HTTP_CLIENTS = new ConcurrentHashMap<>();

   private static final String NODE_ID = ManagementFactory.getRuntimeMXBean().getName() + "/" + UUID.randomUUID().toString().substring(0, 8);

   private static volatile Clock                         clock    = Clock.systemUTC();
   private static volatile SafeHttpClient.HostResolver   resolver = null;



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksRuntime()
   {
   }



   /*******************************************************************************
    ** The config in the current instance, or null when the qbit is not installed.
    *******************************************************************************/
   public static WebhooksQBitConfig config()
   {
      return (config(QContext.getQInstance()));
   }



   /*******************************************************************************
    ** The config in an instance, or null when the qbit is not installed.
    *******************************************************************************/
   public static WebhooksQBitConfig config(QInstance qInstance)
   {
      if(qInstance == null || qInstance.getQBits() == null)
      {
         return (null);
      }
      for(QBitMetaData qBit : qInstance.getQBits().values())
      {
         if(WebhooksQBitProducer.ARTIFACT_ID.equals(qBit.getArtifactId()) && qBit.getConfig() instanceof WebhooksQBitConfig config)
         {
            return (config);
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** True when the qbit is installed in the current instance.
    *******************************************************************************/
   public static boolean isInstalled()
   {
      return (config() != null);
   }



   /*******************************************************************************
    ** The clock (tests replace it).
    *******************************************************************************/
   public static Clock clock()
   {
      return (clock);
   }



   /*******************************************************************************
    ** Replace the clock (tests).
    *******************************************************************************/
   public static void setClock(Clock newClock)
   {
      clock = newClock == null ? Clock.systemUTC() : newClock;
   }



   /*******************************************************************************
    ** Replace DNS resolution for outbound requests (tests; null restores the
    ** system resolver). Clears the connection pools.
    *******************************************************************************/
   public static void setHostResolver(SafeHttpClient.HostResolver newResolver)
   {
      resolver = newResolver;
      closeHttpClients();
   }



   /*******************************************************************************
    ** An id for this JVM, written on the attempts it claims.
    *******************************************************************************/
   public static String nodeId()
   {
      return (NODE_ID);
   }



   /*******************************************************************************
    ** The HTTP policy for an endpoint: the config's limits plus the endpoint's
    ** own timeout and internal allowlist (when it may use one).
    *******************************************************************************/
   public static SafeHttpPolicy policyFor(WebhooksQBitConfig config, Integer endpointTimeoutSeconds, java.util.List<String> internalAllowlist)
   {
      Duration read = endpointTimeoutSeconds != null && endpointTimeoutSeconds > 0
         ? Duration.ofSeconds(endpointTimeoutSeconds) : Duration.ofMillis(config.getHttpTimeoutMillis());
      Duration total = Duration.ofMillis(config.getTotalTimeoutMillis());
      if(read.compareTo(total) > 0)
      {
         read = total;
      }
      return (new SafeHttpPolicy()
         .withAllowPlainHttp(Boolean.TRUE.equals(config.getAllowPlainHttp()))
         .withInternalAllowlist(internalAllowlist)
         .withConnectTimeout(Duration.ofMillis(config.getConnectTimeoutMillis()))
         .withReadTimeout(read)
         .withTotalTimeout(total)
         .withMaxResponseBytes(config.getMaxResponseBytes())
         .withUserAgent(config.getUserAgent()));
   }



   /*******************************************************************************
    ** A shared client for a policy.
    *******************************************************************************/
   public static SafeHttpClient httpClient(SafeHttpPolicy policy)
   {
      SafeHttpClient.HostResolver current = resolver;
      return (HTTP_CLIENTS.computeIfAbsent(policy.cacheKey(), key -> current == null ? new SafeHttpClient(policy) : new SafeHttpClient(policy, current)));
   }



   /*******************************************************************************
    ** Close every pooled client (tests, shutdown).
    *******************************************************************************/
   public static void closeHttpClients()
   {
      for(SafeHttpClient client : HTTP_CLIENTS.values())
      {
         client.close();
      }
      HTTP_CLIENTS.clear();
   }
}
