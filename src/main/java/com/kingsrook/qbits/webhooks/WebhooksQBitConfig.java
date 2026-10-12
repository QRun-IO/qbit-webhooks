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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.producers.MetaDataCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Configuration for the webhooks qbit. Every setting has a working default
 ** except {@code backendName}.
 **
 ** <p>Event sources: {@code tableEventSources} (in-process insert, update and
 ** delete events for local tables), {@code processEventSources} (in-process
 ** completed and failed events), {@code esbDestinations} (ESB topics carrying
 ** CloudEvents from other services, with {@code esbTableEventSources} naming
 ** remote tables that have no local table), plus custom events registered in
 ** code. See docs/DESIGN.md.</p>
 *******************************************************************************/
public class WebhooksQBitConfig implements QBitConfig
{
   private String                                      backendName;
   private String                                      schedulerName;
   private MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer;
   private Boolean                                     snakeCaseColumnNames = true;

   private String sourceName = "qqq-app";

   private String appName  = "webhooks";
   private String appLabel = "Webhooks";
   private String appPath;
   private String tablePathPrefix;

   private List<QFieldMetaData>     securityFields      = new ArrayList<>();
   private List<RecordSecurityLock> recordSecurityLocks = new ArrayList<>();

   private List<String> tableEventSources    = new ArrayList<>();
   private List<String> processEventSources  = new ArrayList<>();
   private List<String> esbDestinations      = new ArrayList<>();
   private List<String> esbTableEventSources = new ArrayList<>();
   private Boolean      esbSkipOwnEvents     = true;

   private String defaultApiName;
   private String defaultApiVersion;

   private Integer       deliveryIntervalSeconds = 15;
   private Integer       deliveryBatchSize       = 200;
   private Integer       maxAttempts             = 6;
   private List<Integer> retryMinutes            = List.of(1, 5, 15, 60, 240, 720);
   private Integer       leaseSeconds            = 120;
   private Integer       maxRetryAfterSeconds    = 3600;

   private Integer connectTimeoutMillis = 5000;
   private Integer httpTimeoutMillis    = 10000;
   private Integer totalTimeoutMillis   = 30000;
   private Integer maxResponseBytes     = 64 * 1024;
   private Boolean allowPlainHttp       = false;
   private String  userAgent            = "QQQ-Webhooks/1.0";

   private Integer unhealthyAfterConsecutiveFailures = 10;
   private Integer probationAfterMinutes             = 60;
   private Integer autoDisableAfterProbationFailures = 3;

   private String  encryptionKeyEnvName = "WEBHOOKS_SECRET_KEY";
   private Integer rotationOverlapHours = 24;

   private Integer deliveredRetentionDays  = 400;
   private Integer deadLetterRetentionDays = 0;
   private Integer attemptRetentionDays    = 400;

   private Integer subscriptionCacheSeconds = 0;



   /*******************************************************************************
    ** Validate (called by the producer).
    *******************************************************************************/
   public void validate(QInstance qInstance, List<String> errors)
   {
      if(!StringUtils.hasContent(backendName))
      {
         errors.add("backendName is required");
      }
      else if(qInstance.getBackend(backendName) == null)
      {
         errors.add("backendName " + backendName + " is not a backend in the instance");
      }
      if(schedulerName != null && qInstance.getScheduler(schedulerName) == null)
      {
         errors.add("schedulerName " + schedulerName + " is not a scheduler in the instance");
      }
      for(String table : tableEventSources)
      {
         if(qInstance.getTable(table) == null)
         {
            errors.add("tableEventSources names " + table + ", which is not a table in the instance (use esbTableEventSources for remote tables)");
         }
      }
      if(maxAttempts == null || maxAttempts < 1)
      {
         errors.add("maxAttempts must be at least 1");
      }
      if(retryMinutes == null || retryMinutes.isEmpty())
      {
         errors.add("retryMinutes needs at least one entry");
      }
      if(leaseSeconds == null || totalTimeoutMillis == null || leaseSeconds * 1000L <= totalTimeoutMillis)
      {
         errors.add("leaseSeconds must be longer than totalTimeoutMillis, so a lease never expires during a send");
      }
   }



   /*******************************************************************************
    ** Retry delay (minutes) after attempt n failed (1-based; the last entry
    ** repeats).
    *******************************************************************************/
   public int retryMinutesAfterAttempt(int attemptNumber)
   {
      int index = Math.max(0, Math.min(attemptNumber - 1, retryMinutes.size() - 1));
      return (retryMinutes.get(index));
   }



   /*******************************************************************************
    ** Fluent setter for backendName (required): where the qbit's tables live.
    *******************************************************************************/
   public WebhooksQBitConfig withBackendName(String backendName)
   {
      this.backendName = backendName;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for schedulerName: without one, nothing is delivered on a
    ** schedule (deliveries still go out from the "Send Now" action).
    *******************************************************************************/
   public WebhooksQBitConfig withSchedulerName(String schedulerName)
   {
      this.schedulerName = schedulerName;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for tableMetaDataCustomizer (runs on every qbit table).
    *******************************************************************************/
   public WebhooksQBitConfig withTableMetaDataCustomizer(MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer)
   {
      this.tableMetaDataCustomizer = tableMetaDataCustomizer;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for snakeCaseColumnNames (default true: column names are
    ** the snake_case of the field names, matching docs/ddl).
    *******************************************************************************/
   public WebhooksQBitConfig withSnakeCaseColumnNames(Boolean snakeCaseColumnNames)
   {
      this.snakeCaseColumnNames = snakeCaseColumnNames;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for sourceName: this application's name in event sources
    ** ({@code qqq://<sourceName>/table/...}); use the ESB instance name.
    *******************************************************************************/
   public WebhooksQBitConfig withSourceName(String sourceName)
   {
      this.sourceName = sourceName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sourceName.
    *******************************************************************************/
   public String getSourceName()
   {
      return (sourceName);
   }



   /*******************************************************************************
    ** Fluent setter for appName.
    *******************************************************************************/
   public WebhooksQBitConfig withAppName(String appName)
   {
      this.appName = appName;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for appLabel.
    *******************************************************************************/
   public WebhooksQBitConfig withAppLabel(String appLabel)
   {
      this.appLabel = appLabel;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for securityFields: added to endpoints, subscriptions and
    ** deliveries (copied from the source record onto deliveries).
    *******************************************************************************/
   public WebhooksQBitConfig withSecurityFields(List<QFieldMetaData> securityFields)
   {
      this.securityFields = securityFields == null ? new ArrayList<>() : new ArrayList<>(securityFields);
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for recordSecurityLocks (on the same tables).
    *******************************************************************************/
   public WebhooksQBitConfig withRecordSecurityLocks(List<RecordSecurityLock> recordSecurityLocks)
   {
      this.recordSecurityLocks = recordSecurityLocks == null ? new ArrayList<>() : new ArrayList<>(recordSecurityLocks);
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for tableEventSources: local tables whose inserts, updates
    ** and deletes become event types {@code <table>.inserted|updated|deleted}.
    *******************************************************************************/
   public WebhooksQBitConfig withTableEventSources(List<String> tableEventSources)
   {
      this.tableEventSources = tableEventSources == null ? new ArrayList<>() : new ArrayList<>(tableEventSources);
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for processEventSources: processes whose completion or
    ** failure become event types {@code process.<name>.completed|failed}.
    *******************************************************************************/
   public WebhooksQBitConfig withProcessEventSources(List<String> processEventSources)
   {
      this.processEventSources = processEventSources == null ? new ArrayList<>() : new ArrayList<>(processEventSources);
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for esbDestinations: ESB topics to consume (needs qqq-esb
    ** and an ESB instance in the QInstance).
    *******************************************************************************/
   public WebhooksQBitConfig withEsbDestinations(List<String> esbDestinations)
   {
      this.esbDestinations = esbDestinations == null ? new ArrayList<>() : new ArrayList<>(esbDestinations);
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for esbTableEventSources: tables of other services (not in
    ** this instance) whose ESB events become event types.
    *******************************************************************************/
   public WebhooksQBitConfig withEsbTableEventSources(List<String> esbTableEventSources)
   {
      this.esbTableEventSources = esbTableEventSources == null ? new ArrayList<>() : new ArrayList<>(esbTableEventSources);
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for esbSkipOwnEvents (default true): ignore ESB events this
    ** instance published itself for tables and processes the in-process path
    ** already covers, so nothing is queued twice.
    *******************************************************************************/
   public WebhooksQBitConfig withEsbSkipOwnEvents(Boolean esbSkipOwnEvents)
   {
      this.esbSkipOwnEvents = esbSkipOwnEvents;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for defaultApiName (new subscriptions render records through
    ** this QQQ API when set).
    *******************************************************************************/
   public WebhooksQBitConfig withDefaultApiName(String defaultApiName)
   {
      this.defaultApiName = defaultApiName;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for defaultApiVersion.
    *******************************************************************************/
   public WebhooksQBitConfig withDefaultApiVersion(String defaultApiVersion)
   {
      this.defaultApiVersion = defaultApiVersion;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for deliveryIntervalSeconds.
    *******************************************************************************/
   public WebhooksQBitConfig withDeliveryIntervalSeconds(Integer deliveryIntervalSeconds)
   {
      this.deliveryIntervalSeconds = deliveryIntervalSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for deliveryBatchSize (most deliveries per run).
    *******************************************************************************/
   public WebhooksQBitConfig withDeliveryBatchSize(Integer deliveryBatchSize)
   {
      this.deliveryBatchSize = deliveryBatchSize;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for maxAttempts (then the delivery is dead-lettered).
    *******************************************************************************/
   public WebhooksQBitConfig withMaxAttempts(Integer maxAttempts)
   {
      this.maxAttempts = maxAttempts;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for retryMinutes.
    *******************************************************************************/
   public WebhooksQBitConfig withRetryMinutes(List<Integer> retryMinutes)
   {
      this.retryMinutes = retryMinutes == null ? List.of() : List.copyOf(retryMinutes);
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for leaseSeconds (a claimed delivery whose runner died is
    ** retried by another runner after this).
    *******************************************************************************/
   public WebhooksQBitConfig withLeaseSeconds(Integer leaseSeconds)
   {
      this.leaseSeconds = leaseSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for maxRetryAfterSeconds (cap on a receiver's Retry-After).
    *******************************************************************************/
   public WebhooksQBitConfig withMaxRetryAfterSeconds(Integer maxRetryAfterSeconds)
   {
      this.maxRetryAfterSeconds = maxRetryAfterSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for connectTimeoutMillis.
    *******************************************************************************/
   public WebhooksQBitConfig withConnectTimeoutMillis(Integer connectTimeoutMillis)
   {
      this.connectTimeoutMillis = connectTimeoutMillis;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for httpTimeoutMillis: the read timeout (an endpoint's own
    ** timeoutSeconds overrides it).
    *******************************************************************************/
   public WebhooksQBitConfig withHttpTimeoutMillis(Integer httpTimeoutMillis)
   {
      this.httpTimeoutMillis = httpTimeoutMillis;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for totalTimeoutMillis.
    *******************************************************************************/
   public WebhooksQBitConfig withTotalTimeoutMillis(Integer totalTimeoutMillis)
   {
      this.totalTimeoutMillis = totalTimeoutMillis;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for maxResponseBytes.
    *******************************************************************************/
   public WebhooksQBitConfig withMaxResponseBytes(Integer maxResponseBytes)
   {
      this.maxResponseBytes = maxResponseBytes;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for allowPlainHttp (default false: https only).
    *******************************************************************************/
   public WebhooksQBitConfig withAllowPlainHttp(Boolean allowPlainHttp)
   {
      this.allowPlainHttp = allowPlainHttp;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for userAgent.
    *******************************************************************************/
   public WebhooksQBitConfig withUserAgent(String userAgent)
   {
      this.userAgent = userAgent;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for unhealthyAfterConsecutiveFailures.
    *******************************************************************************/
   public WebhooksQBitConfig withUnhealthyAfterConsecutiveFailures(Integer unhealthyAfterConsecutiveFailures)
   {
      this.unhealthyAfterConsecutiveFailures = unhealthyAfterConsecutiveFailures;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for probationAfterMinutes.
    *******************************************************************************/
   public WebhooksQBitConfig withProbationAfterMinutes(Integer probationAfterMinutes)
   {
      this.probationAfterMinutes = probationAfterMinutes;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for autoDisableAfterProbationFailures (0: never disable).
    *******************************************************************************/
   public WebhooksQBitConfig withAutoDisableAfterProbationFailures(Integer autoDisableAfterProbationFailures)
   {
      this.autoDisableAfterProbationFailures = autoDisableAfterProbationFailures;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for encryptionKeyEnvName: the environment variable (or
    ** system property) holding the AES-256 key, base64, that encrypts generated
    ** signing secrets at rest.
    *******************************************************************************/
   public WebhooksQBitConfig withEncryptionKeyEnvName(String encryptionKeyEnvName)
   {
      this.encryptionKeyEnvName = encryptionKeyEnvName;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for rotationOverlapHours (the previous secret keeps signing
    ** for this long after a rotation).
    *******************************************************************************/
   public WebhooksQBitConfig withRotationOverlapHours(Integer rotationOverlapHours)
   {
      this.rotationOverlapHours = rotationOverlapHours;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for deliveredRetentionDays (0: keep forever).
    *******************************************************************************/
   public WebhooksQBitConfig withDeliveredRetentionDays(Integer deliveredRetentionDays)
   {
      this.deliveredRetentionDays = deliveredRetentionDays;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for deadLetterRetentionDays (default 0: keep forever).
    *******************************************************************************/
   public WebhooksQBitConfig withDeadLetterRetentionDays(Integer deadLetterRetentionDays)
   {
      this.deadLetterRetentionDays = deadLetterRetentionDays;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for attemptRetentionDays (0: keep forever).
    *******************************************************************************/
   public WebhooksQBitConfig withAttemptRetentionDays(Integer attemptRetentionDays)
   {
      this.attemptRetentionDays = attemptRetentionDays;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter for subscriptionCacheSeconds (default 0: subscriptions are
    ** read on every change, so every replica sees edits at once).
    *******************************************************************************/
   public WebhooksQBitConfig withSubscriptionCacheSeconds(Integer subscriptionCacheSeconds)
   {
      this.subscriptionCacheSeconds = subscriptionCacheSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Getter for backendName.
    *******************************************************************************/
   public String getBackendName()
   {
      return (backendName);
   }



   /*******************************************************************************
    ** Getter for schedulerName.
    *******************************************************************************/
   public String getSchedulerName()
   {
      return (schedulerName);
   }



   /*******************************************************************************
    ** Getter for tableMetaDataCustomizer.
    *******************************************************************************/
   @Override
   public MetaDataCustomizerInterface<QTableMetaData> getTableMetaDataCustomizer()
   {
      return (tableMetaDataCustomizer);
   }



   /*******************************************************************************
    ** Getter for snakeCaseColumnNames.
    *******************************************************************************/
   public Boolean getSnakeCaseColumnNames()
   {
      return (snakeCaseColumnNames);
   }



   /*******************************************************************************
    ** Getter for appName.
    *******************************************************************************/
   public String getAppName()
   {
      return (appName);
   }



   /*******************************************************************************
    ** Fluent setter for appPath: the app's URL path in the UI, for links on
    ** the home (default {@code /<appName>}).
    *******************************************************************************/
   public WebhooksQBitConfig withAppPath(String appPath)
   {
      this.appPath = appPath;
      return (this);
   }



   /*******************************************************************************
    ** Getter for appPath.
    *******************************************************************************/
   public String getAppPath()
   {
      return (appPath == null ? "/" + appName : appPath);
   }



   /*******************************************************************************
    ** Fluent setter for tablePathPrefix: the URL prefix of table and record
    ** links (default the app path).
    *******************************************************************************/
   public WebhooksQBitConfig withTablePathPrefix(String tablePathPrefix)
   {
      this.tablePathPrefix = tablePathPrefix;
      return (this);
   }



   /*******************************************************************************
    ** Getter for tablePathPrefix.
    *******************************************************************************/
   public String getTablePathPrefix()
   {
      return (tablePathPrefix == null ? getAppPath() : tablePathPrefix);
   }



   /*******************************************************************************
    ** Getter for appLabel.
    *******************************************************************************/
   public String getAppLabel()
   {
      return (appLabel);
   }



   /*******************************************************************************
    ** Getter for securityFields.
    *******************************************************************************/
   public List<QFieldMetaData> getSecurityFields()
   {
      return (securityFields);
   }



   /*******************************************************************************
    ** Getter for recordSecurityLocks.
    *******************************************************************************/
   public List<RecordSecurityLock> getRecordSecurityLocks()
   {
      return (recordSecurityLocks);
   }



   /*******************************************************************************
    ** Getter for tableEventSources.
    *******************************************************************************/
   public List<String> getTableEventSources()
   {
      return (tableEventSources);
   }



   /*******************************************************************************
    ** Getter for processEventSources.
    *******************************************************************************/
   public List<String> getProcessEventSources()
   {
      return (processEventSources);
   }



   /*******************************************************************************
    ** Getter for esbDestinations.
    *******************************************************************************/
   public List<String> getEsbDestinations()
   {
      return (esbDestinations);
   }



   /*******************************************************************************
    ** Getter for esbTableEventSources.
    *******************************************************************************/
   public List<String> getEsbTableEventSources()
   {
      return (esbTableEventSources);
   }



   /*******************************************************************************
    ** Getter for esbSkipOwnEvents.
    *******************************************************************************/
   public Boolean getEsbSkipOwnEvents()
   {
      return (esbSkipOwnEvents);
   }



   /*******************************************************************************
    ** Getter for defaultApiName.
    *******************************************************************************/
   public String getDefaultApiName()
   {
      return (defaultApiName);
   }



   /*******************************************************************************
    ** Getter for defaultApiVersion.
    *******************************************************************************/
   public String getDefaultApiVersion()
   {
      return (defaultApiVersion);
   }



   /*******************************************************************************
    ** Getter for deliveryIntervalSeconds.
    *******************************************************************************/
   public Integer getDeliveryIntervalSeconds()
   {
      return (deliveryIntervalSeconds);
   }



   /*******************************************************************************
    ** Getter for deliveryBatchSize.
    *******************************************************************************/
   public Integer getDeliveryBatchSize()
   {
      return (deliveryBatchSize);
   }



   /*******************************************************************************
    ** Getter for maxAttempts.
    *******************************************************************************/
   public Integer getMaxAttempts()
   {
      return (maxAttempts);
   }



   /*******************************************************************************
    ** Getter for retryMinutes.
    *******************************************************************************/
   public List<Integer> getRetryMinutes()
   {
      return (retryMinutes);
   }



   /*******************************************************************************
    ** Getter for leaseSeconds.
    *******************************************************************************/
   public Integer getLeaseSeconds()
   {
      return (leaseSeconds);
   }



   /*******************************************************************************
    ** Getter for maxRetryAfterSeconds.
    *******************************************************************************/
   public Integer getMaxRetryAfterSeconds()
   {
      return (maxRetryAfterSeconds);
   }



   /*******************************************************************************
    ** Getter for connectTimeoutMillis.
    *******************************************************************************/
   public Integer getConnectTimeoutMillis()
   {
      return (connectTimeoutMillis);
   }



   /*******************************************************************************
    ** Getter for httpTimeoutMillis.
    *******************************************************************************/
   public Integer getHttpTimeoutMillis()
   {
      return (httpTimeoutMillis);
   }



   /*******************************************************************************
    ** Getter for totalTimeoutMillis.
    *******************************************************************************/
   public Integer getTotalTimeoutMillis()
   {
      return (totalTimeoutMillis);
   }



   /*******************************************************************************
    ** Getter for maxResponseBytes.
    *******************************************************************************/
   public Integer getMaxResponseBytes()
   {
      return (maxResponseBytes);
   }



   /*******************************************************************************
    ** Getter for allowPlainHttp.
    *******************************************************************************/
   public Boolean getAllowPlainHttp()
   {
      return (allowPlainHttp);
   }



   /*******************************************************************************
    ** Getter for userAgent.
    *******************************************************************************/
   public String getUserAgent()
   {
      return (userAgent);
   }



   /*******************************************************************************
    ** Getter for unhealthyAfterConsecutiveFailures.
    *******************************************************************************/
   public Integer getUnhealthyAfterConsecutiveFailures()
   {
      return (unhealthyAfterConsecutiveFailures);
   }



   /*******************************************************************************
    ** Getter for probationAfterMinutes.
    *******************************************************************************/
   public Integer getProbationAfterMinutes()
   {
      return (probationAfterMinutes);
   }



   /*******************************************************************************
    ** Getter for autoDisableAfterProbationFailures.
    *******************************************************************************/
   public Integer getAutoDisableAfterProbationFailures()
   {
      return (autoDisableAfterProbationFailures);
   }



   /*******************************************************************************
    ** Getter for encryptionKeyEnvName.
    *******************************************************************************/
   public String getEncryptionKeyEnvName()
   {
      return (encryptionKeyEnvName);
   }



   /*******************************************************************************
    ** Getter for rotationOverlapHours.
    *******************************************************************************/
   public Integer getRotationOverlapHours()
   {
      return (rotationOverlapHours);
   }



   /*******************************************************************************
    ** Getter for deliveredRetentionDays.
    *******************************************************************************/
   public Integer getDeliveredRetentionDays()
   {
      return (deliveredRetentionDays);
   }



   /*******************************************************************************
    ** Getter for deadLetterRetentionDays.
    *******************************************************************************/
   public Integer getDeadLetterRetentionDays()
   {
      return (deadLetterRetentionDays);
   }



   /*******************************************************************************
    ** Getter for attemptRetentionDays.
    *******************************************************************************/
   public Integer getAttemptRetentionDays()
   {
      return (attemptRetentionDays);
   }



   /*******************************************************************************
    ** Getter for subscriptionCacheSeconds.
    *******************************************************************************/
   public Integer getSubscriptionCacheSeconds()
   {
      return (subscriptionCacheSeconds);
   }
}
