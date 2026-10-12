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


import java.util.List;
import java.util.function.Function;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.producers.MetaDataCustomizerInterface;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;


/*******************************************************************************
 ** Configuration data for this qbit.
 **
 *******************************************************************************/
public class WebhooksQBitConfig implements QBitConfig
{
   private MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer;

   private String defaultBackendNameForTables;
   private String schedulerName;

   private String defaultApiNameForNewSubscriptions;
   private String defaultApiVersionForNewSubscriptions;

   private List<QFieldMetaData>     securityFields;
   private List<RecordSecurityLock> recordSecurityLocks;

   private Integer sendWebhookEventProcessRepeatSeconds = 60;
   private Integer repeatedFailsToGoUnhealthy           = 10;
   private Integer unhealthyToProbationTimeoutMinutes   = 60;
   private Integer memoizationTimeoutMinutes            = 5;

   private Integer       maxSendAttemptsBeforeFailure       = 5;
   private List<Integer> minutesBetweenRetryAttempts        = List.of(1, 5, 15, 60, 240);
   private Integer       minutesToConsiderLeakedSendAttempt = 10;
   private Integer       maxRateLimitRetries                = 3;
   private Integer       initialRateLimitBackoffMillis      = 1000;
   private Integer       httpTimeoutMillis                  = 5000;

   //////////////////////////////////////////////////////////////////////////////////////////
   // to get default values, construct one instance of this config to keep in this class - //
   // see the applyConfig and getConfigValue methods below where it is used                //
   //////////////////////////////////////////////////////////////////////////////////////////
   public static final WebhooksQBitConfig DEFAULT_CONFIG = new WebhooksQBitConfig();



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   public void validate(QInstance qInstance, List<String> errors)
   {
      ///////////////////////////////////////////////////////////////////////////////////////////////////////////
      // we don't feel the need to validate fields or locks, as they'll be added to tables and validated there //
      ///////////////////////////////////////////////////////////////////////////////////////////////////////////

      ////////////////////////////////////////////////////////////////////////////
      // todo this would all be good to validate, but, the qbit validation is   //
      // happening before the full instance is built so, this isn't working     //
      // out...  the validation of qbit configs probably needs to happen later. //
      ////////////////////////////////////////////////////////////////////////////
      /*
      if(this.defaultBackendNameForTables != null)
      {
         assertCondition(qInstance.getBackends().containsKey(this.defaultBackendNameForTables), "Unrecognized defaultBackendNameForTables: " + defaultBackendNameForTables, errors);
      }

      if(this.schedulerName != null)
      {
         assertCondition(qInstance.getSchedulers().containsKey(this.schedulerName), "Unrecognized schedulerName: " + schedulerName, errors);
      }

      if(StringUtils.hasContent(this.defaultApiNameForNewSubscriptions))
      {
         ApiInstanceMetaDataContainer apiInstanceMetaDataContainer = ApiInstanceMetaDataContainer.of(qInstance);
         if(assertCondition(apiInstanceMetaDataContainer != null && CollectionUtils.nonNullMap(apiInstanceMetaDataContainer.getApis()).containsKey(this.defaultApiNameForNewSubscriptions),
            "Unrecognized defaultApiNameForNewSubscriptions: " + defaultApiNameForNewSubscriptions, errors))
         {
            if(StringUtils.hasContent(this.defaultApiVersionForNewSubscriptions))
            {
               ApiInstanceMetaData apiInstanceMetaData = apiInstanceMetaDataContainer.getApis().get(this.defaultApiVersionForNewSubscriptions);
               assertCondition(apiInstanceMetaData.getSupportedVersions().contains(new APIVersion(this.defaultApiVersionForNewSubscriptions)), "Unrecognized defaultApiVersionForNewSubscriptions (in API '" + defaultApiNameForNewSubscriptions + "'): " + defaultApiVersionForNewSubscriptions, errors);
            }
         }
      }
      */

      assertCondition(sendWebhookEventProcessRepeatSeconds != null && sendWebhookEventProcessRepeatSeconds > 0, "sendWebhookEventProcessRepeatSeconds must be non-null and > 0", errors);
      assertCondition(repeatedFailsToGoUnhealthy == null || repeatedFailsToGoUnhealthy > 0, "If repeatedFailsToGoUnhealthy is given, it must be > 0", errors);
      assertCondition(unhealthyToProbationTimeoutMinutes == null || unhealthyToProbationTimeoutMinutes > 0, "If unhealthyToProbationTimeoutMinutes is given, it must be > 0", errors);
      assertCondition(memoizationTimeoutMinutes != null && memoizationTimeoutMinutes >= 0, "memoizationTimeoutMinutes must be non-null and >= 0", errors);
      assertCondition(maxSendAttemptsBeforeFailure != null && maxSendAttemptsBeforeFailure >= 1, "maxSendAttemptsBeforeFailure must be non-null and >= 1", errors);
      assertCondition(minutesToConsiderLeakedSendAttempt != null && minutesToConsiderLeakedSendAttempt >= 1, "minutesToConsiderLeakedSendAttempt must be non-null and >= 1", errors);
      assertCondition(maxRateLimitRetries != null && maxRateLimitRetries >= 0, "maxRateLimitRetries must be non-null and >= 0", errors);
      assertCondition(initialRateLimitBackoffMillis != null && initialRateLimitBackoffMillis > 0, "initialRateLimitBackoffMillis must be non-null and > 0", errors);
      assertCondition(httpTimeoutMillis != null && httpTimeoutMillis > 0, "httpTimeoutMillis must be non-null and > 0", errors);

      if(assertCondition(CollectionUtils.nullSafeHasContents(minutesBetweenRetryAttempts), "minutesBetweenRetryAttempts must be non-null and non-empty", errors))
      {
         boolean allOk = true;
         for(Integer minutesBetweenRetryAttempt : minutesBetweenRetryAttempts)
         {
            if(minutesBetweenRetryAttempt < 0)
            {
               allOk = false;
               break;
            }
         }
         assertCondition(allOk, "minutesBetweenRetryAttempts must have all values >= 0", errors);
      }
   }



   /***************************************************************************
    **
    ***************************************************************************/
   public static <V> V getConfigValue(Function<WebhooksQBitConfig, V> valueFunction)
   {
      QTableMetaData table = QContext.getQInstance().getTable(Webhook.TABLE_NAME);
      if(table != null && table.getSourceQBitConfig() instanceof WebhooksQBitConfig webhooksQBitConfig)
      {
         return (valueFunction.apply(webhooksQBitConfig));
      }
      else
      {
         return (valueFunction.apply(DEFAULT_CONFIG));
      }
   }



   /*******************************************************************************
    ** Getter for sendWebhookEventProcessRepeatSeconds
    ** @see #withSendWebhookEventProcessRepeatSeconds(Integer)
    *******************************************************************************/
   public Integer getSendWebhookEventProcessRepeatSeconds()
   {
      return (this.sendWebhookEventProcessRepeatSeconds);
   }



   /*******************************************************************************
    ** Setter for sendWebhookEventProcessRepeatSeconds
    ** @see #withSendWebhookEventProcessRepeatSeconds(Integer)
    *******************************************************************************/
   public void setSendWebhookEventProcessRepeatSeconds(Integer sendWebhookEventProcessRepeatSeconds)
   {
      this.sendWebhookEventProcessRepeatSeconds = sendWebhookEventProcessRepeatSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for sendWebhookEventProcessRepeatSeconds
    **
    ** This property controls the repeatSeconds value that is applied to the
    ** scheduled job that is created by the SyncWebhookScheduledJobProcess (which
    ** runs the SendWebhookEventProcess.
    **
    ** Value must be greater than 0.
    *******************************************************************************/
   public WebhooksQBitConfig withSendWebhookEventProcessRepeatSeconds(Integer sendWebhookEventProcessRepeatSeconds)
   {
      this.sendWebhookEventProcessRepeatSeconds = sendWebhookEventProcessRepeatSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Getter for repeatedFailsToGoUnhealthy
    ** @see #withRepeatedFailsToGoUnhealthy(Integer)
    *******************************************************************************/
   public Integer getRepeatedFailsToGoUnhealthy()
   {
      return (this.repeatedFailsToGoUnhealthy);
   }



   /*******************************************************************************
    ** Setter for repeatedFailsToGoUnhealthy
    ** @see #withRepeatedFailsToGoUnhealthy(Integer)
    *******************************************************************************/
   public void setRepeatedFailsToGoUnhealthy(Integer repeatedFailsToGoUnhealthy)
   {
      this.repeatedFailsToGoUnhealthy = repeatedFailsToGoUnhealthy;
   }



   /*******************************************************************************
    ** Fluent setter for repeatedFailsToGoUnhealthy
    **
    ** This property is used in the WebhookHealthManager determine if when a webhook
    ** should become unhealthy.  It must have this many of failures in a row to go
    ** from HEALTHY to UNHEALTHY.
    **
    ** Use a null value to disable webhooks ever becoming unhealthy this way.
    ** Otherwise, value must be > 0.
    *******************************************************************************/
   public WebhooksQBitConfig withRepeatedFailsToGoUnhealthy(Integer repeatedFailsToGoUnhealthy)
   {
      this.repeatedFailsToGoUnhealthy = repeatedFailsToGoUnhealthy;
      return (this);
   }



   /*******************************************************************************
    ** Getter for unhealthyToProbationTimeoutMinutes
    ** @see #withUnhealthyToProbationTimeoutMinutes(Integer)
    *******************************************************************************/
   public Integer getUnhealthyToProbationTimeoutMinutes()
   {
      return (this.unhealthyToProbationTimeoutMinutes);
   }



   /*******************************************************************************
    ** Setter for unhealthyToProbationTimeoutMinutes
    ** @see #withUnhealthyToProbationTimeoutMinutes(Integer)
    *******************************************************************************/
   public void setUnhealthyToProbationTimeoutMinutes(Integer unhealthyToProbationTimeoutMinutes)
   {
      this.unhealthyToProbationTimeoutMinutes = unhealthyToProbationTimeoutMinutes;
   }



   /*******************************************************************************
    ** Fluent setter for unhealthyToProbationTimeoutMinutes
    **
    ** This property controls the ManageWebhookHealth process - the number of minutes
    ** that must be passed before a webhook in health status of Unhealthy can be
    ** moved to status Probation.
    **
    ** Use a null value to disable webhooks ever going into probation this way.
    ** Otherwise, value must be > 0.
    *******************************************************************************/
   public WebhooksQBitConfig withUnhealthyToProbationTimeoutMinutes(Integer unhealthyToProbationTimeoutMinutes)
   {
      this.unhealthyToProbationTimeoutMinutes = unhealthyToProbationTimeoutMinutes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for memoizationTimeoutMinutes
    ** @see #withMemoizationTimeoutMinutes(Integer)
    *******************************************************************************/
   public Integer getMemoizationTimeoutMinutes()
   {
      return (this.memoizationTimeoutMinutes);
   }



   /*******************************************************************************
    ** Setter for memoizationTimeoutMinutes
    ** @see #withMemoizationTimeoutMinutes(Integer)
    *******************************************************************************/
   public void setMemoizationTimeoutMinutes(Integer memoizationTimeoutMinutes)
   {
      this.memoizationTimeoutMinutes = memoizationTimeoutMinutes;
   }



   /*******************************************************************************
    ** Fluent setter for memoizationTimeoutMinutes
    **
    ** @param memoizationTimeoutMinutes
    ** Control the timeout (in minutes) used by the memoizations in
    ** WebhookSubscriptionsHelper.
    **
    ** Can be set to 0 to disable memoization.  Else must not be negative (>= 0)
    *******************************************************************************/
   public WebhooksQBitConfig withMemoizationTimeoutMinutes(Integer memoizationTimeoutMinutes)
   {
      this.memoizationTimeoutMinutes = memoizationTimeoutMinutes;
      return (this);
   }



   /*******************************************************************************
    * Getter for maxSendAttemptsBeforeFailure
    * @see #withMaxSendAttemptsBeforeFailure(Integer)
    *******************************************************************************/
   public Integer getMaxSendAttemptsBeforeFailure()
   {
      return (this.maxSendAttemptsBeforeFailure);
   }



   /*******************************************************************************
    * Setter for maxSendAttemptsBeforeFailure
    * @see #withMaxSendAttemptsBeforeFailure(Integer)
    *******************************************************************************/
   public void setMaxSendAttemptsBeforeFailure(Integer maxSendAttemptsBeforeFailure)
   {
      this.maxSendAttemptsBeforeFailure = maxSendAttemptsBeforeFailure;
   }



   /*******************************************************************************
    * Fluent setter for maxSendAttemptsBeforeFailure
    *
    * @param maxSendAttemptsBeforeFailure the number of times the system will try to
    * send a single webhook event before its status moves to FAILURE.  Must be >= 1.
    * defaults to 5.
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withMaxSendAttemptsBeforeFailure(Integer maxSendAttemptsBeforeFailure)
   {
      this.maxSendAttemptsBeforeFailure = maxSendAttemptsBeforeFailure;
      return (this);
   }



   /*******************************************************************************
    * Getter for minutesBetweenRetryAttempts
    * @see #withMinutesBetweenRetryAttempts(List)
    *******************************************************************************/
   public List<Integer> getMinutesBetweenRetryAttempts()
   {
      return (this.minutesBetweenRetryAttempts);
   }



   /*******************************************************************************
    * Setter for minutesBetweenRetryAttempts
    * @see #withMinutesBetweenRetryAttempts(List)
    *******************************************************************************/
   public void setMinutesBetweenRetryAttempts(List<Integer> minutesBetweenRetryAttempts)
   {
      this.minutesBetweenRetryAttempts = minutesBetweenRetryAttempts;
   }



   /*******************************************************************************
    * Fluent setter for minutesBetweenRetryAttempts
    *
    * @param minutesBetweenRetryAttempts
    * List of integers, that control how long to wait after each failed send, before
    * the next attempt is made.  If this list is shorter than the number of attempts,
    * then the last element of the list will be used for attempt beyond the list
    * length.  Must not be null or empty.  Each entry in the list must be >= 0.
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withMinutesBetweenRetryAttempts(List<Integer> minutesBetweenRetryAttempts)
   {
      this.minutesBetweenRetryAttempts = minutesBetweenRetryAttempts;
      return (this);
   }



   /*******************************************************************************
    * Getter for minutesToConsiderLeakedSendAttempt
    * @see #withMinutesToConsiderLeakedSendAttempt(Integer)
    *******************************************************************************/
   public Integer getMinutesToConsiderLeakedSendAttempt()
   {
      return (this.minutesToConsiderLeakedSendAttempt);
   }



   /*******************************************************************************
    * Setter for minutesToConsiderLeakedSendAttempt
    * @see #withMinutesToConsiderLeakedSendAttempt(Integer)
    *******************************************************************************/
   public void setMinutesToConsiderLeakedSendAttempt(Integer minutesToConsiderLeakedSendAttempt)
   {
      this.minutesToConsiderLeakedSendAttempt = minutesToConsiderLeakedSendAttempt;
   }



   /*******************************************************************************
    * Fluent setter for minutesToConsiderLeakedSendAttempt
    *
    * @param minutesToConsiderLeakedSendAttempt
    * When an event is marked as sending, in the case that the process fails and the
    * send "leaks", this property controls how many minutes pass before that failure
    * is assumed to be a leak, and is re-tried.  Must be non-null and >= 1.
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withMinutesToConsiderLeakedSendAttempt(Integer minutesToConsiderLeakedSendAttempt)
   {
      this.minutesToConsiderLeakedSendAttempt = minutesToConsiderLeakedSendAttempt;
      return (this);
   }



   /*******************************************************************************
    * Getter for maxRateLimitRetries
    * @see #withMaxRateLimitRetries(Integer)
    *******************************************************************************/
   public Integer getMaxRateLimitRetries()
   {
      return (this.maxRateLimitRetries);
   }



   /*******************************************************************************
    * Setter for maxRateLimitRetries
    * @see #withMaxRateLimitRetries(Integer)
    *******************************************************************************/
   public void setMaxRateLimitRetries(Integer maxRateLimitRetries)
   {
      this.maxRateLimitRetries = maxRateLimitRetries;
   }



   /*******************************************************************************
    * Fluent setter for maxRateLimitRetries
    *
    * @param maxRateLimitRetries
    * Sets the number of retries performed within a single attempt to send an event
    * in response to http 429 (rate limit) errors.  If set to 0, no in-band retries
    * are done in response to rate limits (they are treated the same as any other
    * error response).  Must be non-null and >= 0.
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withMaxRateLimitRetries(Integer maxRateLimitRetries)
   {
      this.maxRateLimitRetries = maxRateLimitRetries;
      return (this);
   }



   /*******************************************************************************
    * Getter for initialRateLimitBackoffMillis
    * @see #withInitialRateLimitBackoffMillis(Integer)
    *******************************************************************************/
   public Integer getInitialRateLimitBackoffMillis()
   {
      return (this.initialRateLimitBackoffMillis);
   }



   /*******************************************************************************
    * Setter for initialRateLimitBackoffMillis
    * @see #withInitialRateLimitBackoffMillis(Integer)
    *******************************************************************************/
   public void setInitialRateLimitBackoffMillis(Integer initialRateLimitBackoffMillis)
   {
      this.initialRateLimitBackoffMillis = initialRateLimitBackoffMillis;
   }



   /*******************************************************************************
    * Fluent setter for initialRateLimitBackoffMillis
    *
    * @param initialRateLimitBackoffMillis
    * this property sets the initial number of milliseconds that the webhook sender
    * sleeps after a rate-limit error is caught.  Must be non-null and > 0.  Note
    * that for multiple rate-limit retries, this value is doubled between each try.
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withInitialRateLimitBackoffMillis(Integer initialRateLimitBackoffMillis)
   {
      this.initialRateLimitBackoffMillis = initialRateLimitBackoffMillis;
      return (this);
   }



   /*******************************************************************************
    * Getter for httpTimeoutMillis
    * @see #withHttpTimeoutMillis(Integer)
    *******************************************************************************/
   public Integer getHttpTimeoutMillis()
   {
      return (this.httpTimeoutMillis);
   }



   /*******************************************************************************
    * Setter for httpTimeoutMillis
    * @see #withHttpTimeoutMillis(Integer)
    *******************************************************************************/
   public void setHttpTimeoutMillis(Integer httpTimeoutMillis)
   {
      this.httpTimeoutMillis = httpTimeoutMillis;
   }



   /*******************************************************************************
    * Fluent setter for httpTimeoutMillis
    *
    * @param httpTimeoutMillis
    * this property sets the number of milliseconds used for all timeouts associated
    * with the HTTP posts used to send webhooks.  Must be non-null and > 0.
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withHttpTimeoutMillis(Integer httpTimeoutMillis)
   {
      this.httpTimeoutMillis = httpTimeoutMillis;
      return (this);
   }



   /*******************************************************************************
    * Getter for defaultBackendNameForTables
    * @see #withDefaultBackendNameForTables(String)
    *******************************************************************************/
   public String getDefaultBackendNameForTables()
   {
      return (this.defaultBackendNameForTables);
   }



   /*******************************************************************************
    * Setter for defaultBackendNameForTables
    * @see #withDefaultBackendNameForTables(String)
    *******************************************************************************/
   public void setDefaultBackendNameForTables(String defaultBackendNameForTables)
   {
      this.defaultBackendNameForTables = defaultBackendNameForTables;
   }



   /*******************************************************************************
    * Fluent setter for defaultBackendNameForTables
    *
    * @param defaultBackendNameForTables
    * Sets the backendName to be applied to all tables produced by this QBit.
    * If set, must be the name of a backend within the QInstance.
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withDefaultBackendNameForTables(String defaultBackendNameForTables)
   {
      this.defaultBackendNameForTables = defaultBackendNameForTables;
      return (this);
   }



   /*******************************************************************************
    * Getter for schedulerName
    * @see #withSchedulerName(String)
    *******************************************************************************/
   public String getSchedulerName()
   {
      return (this.schedulerName);
   }



   /*******************************************************************************
    * Setter for schedulerName
    * @see #withSchedulerName(String)
    *******************************************************************************/
   public void setSchedulerName(String schedulerName)
   {
      this.schedulerName = schedulerName;
   }



   /*******************************************************************************
    * Fluent setter for schedulerName
    *
    * @param schedulerName
    * Name of the scheduler used by scheduled jobs used by this QBIt.  If set, must
    * be the name of a scheduler within the QInstance.
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withSchedulerName(String schedulerName)
   {
      this.schedulerName = schedulerName;
      return (this);
   }



   /*******************************************************************************
    * Getter for defaultApiNameForNewSubscriptions
    * @see #withDefaultApiNameForNewSubscriptions(String)
    *******************************************************************************/
   public String getDefaultApiNameForNewSubscriptions()
   {
      return (this.defaultApiNameForNewSubscriptions);
   }



   /*******************************************************************************
    * Setter for defaultApiNameForNewSubscriptions
    * @see #withDefaultApiNameForNewSubscriptions(String)
    *******************************************************************************/
   public void setDefaultApiNameForNewSubscriptions(String defaultApiNameForNewSubscriptions)
   {
      this.defaultApiNameForNewSubscriptions = defaultApiNameForNewSubscriptions;
   }



   /*******************************************************************************
    * Fluent setter for defaultApiNameForNewSubscriptions
    *
    * @param defaultApiNameForNewSubscriptions
    * Sets the default value to use in the apiName field of the WebhookSubscription
    * table.  If given, must be a valid API Name within the QInstance.
    *
    * @see #withDefaultApiVersionForNewSubscriptions(String)
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withDefaultApiNameForNewSubscriptions(String defaultApiNameForNewSubscriptions)
   {
      this.defaultApiNameForNewSubscriptions = defaultApiNameForNewSubscriptions;
      return (this);
   }



   /*******************************************************************************
    * Getter for defaultApiVersionForNewSubscriptions
    * @see #withDefaultApiVersionForNewSubscriptions(String)
    *******************************************************************************/
   public String getDefaultApiVersionForNewSubscriptions()
   {
      return (this.defaultApiVersionForNewSubscriptions);
   }



   /*******************************************************************************
    * Setter for defaultApiVersionForNewSubscriptions
    * @see #withDefaultApiVersionForNewSubscriptions(String)
    *******************************************************************************/
   public void setDefaultApiVersionForNewSubscriptions(String defaultApiVersionForNewSubscriptions)
   {
      this.defaultApiVersionForNewSubscriptions = defaultApiVersionForNewSubscriptions;
   }



   /*******************************************************************************
    * Fluent setter for defaultApiVersionForNewSubscriptions
    *
    * @param defaultApiVersionForNewSubscriptions
    * Sets the default value to use in the apiVersion field of the WebhookSubscription
    * table.  If given, must be a valid API Version within the api named by
    * defaultApiNameForNewSubscriptions.
    *
    * @see #withDefaultApiNameForNewSubscriptions(String)
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withDefaultApiVersionForNewSubscriptions(String defaultApiVersionForNewSubscriptions)
   {
      this.defaultApiVersionForNewSubscriptions = defaultApiVersionForNewSubscriptions;
      return (this);
   }



   /*******************************************************************************
    * Getter for securityFields
    * @see #withSecurityFields(List)
    *******************************************************************************/
   public List<QFieldMetaData> getSecurityFields()
   {
      return (this.securityFields);
   }



   /*******************************************************************************
    * Setter for securityFields
    * @see #withSecurityFields(List)
    *******************************************************************************/
   public void setSecurityFields(List<QFieldMetaData> securityFields)
   {
      this.securityFields = securityFields;
   }



   /*******************************************************************************
    * Fluent setter for securityFields
    *
    * @param securityFields
    * Optional list of fields to add to the Webhook and WebhookEvent tables, meant
    * to work in concert with
    *
    * @see #withRecordSecurityLocks(List)
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withSecurityFields(List<QFieldMetaData> securityFields)
   {
      this.securityFields = securityFields;
      return (this);
   }



   /*******************************************************************************
    * Getter for recordSecurityLocks
    * @see #withRecordSecurityLocks(List)
    *******************************************************************************/
   public List<RecordSecurityLock> getRecordSecurityLocks()
   {
      return (this.recordSecurityLocks);
   }



   /*******************************************************************************
    * Setter for recordSecurityLocks
    * @see #withRecordSecurityLocks(List)
    *******************************************************************************/
   public void setRecordSecurityLocks(List<RecordSecurityLock> recordSecurityLocks)
   {
      this.recordSecurityLocks = recordSecurityLocks;
   }



   /*******************************************************************************
    * Fluent setter for recordSecurityLocks
    *
    * @param recordSecurityLocks
    * Optional list of RecordSecurityLock objects to apply to tables in this QBit.
    * These locks are applied directly to the Webhook and WebhookEvent tables, and
    * are added to WebhookSubscription and WebhookEventSendLog through joins.
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withRecordSecurityLocks(List<RecordSecurityLock> recordSecurityLocks)
   {
      this.recordSecurityLocks = recordSecurityLocks;
      return (this);
   }



   /*******************************************************************************
    * Getter for tableMetaDataCustomizer
    * @see #withTableMetaDataCustomizer(MetaDataCustomizerInterface<QTableMetaData>)
    *******************************************************************************/
   public MetaDataCustomizerInterface<QTableMetaData> getTableMetaDataCustomizer()
   {
      return (this.tableMetaDataCustomizer);
   }



   /*******************************************************************************
    * Setter for tableMetaDataCustomizer
    * @see #withTableMetaDataCustomizer(MetaDataCustomizerInterface<QTableMetaData>)
    *******************************************************************************/
   public void setTableMetaDataCustomizer(MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer)
   {
      this.tableMetaDataCustomizer = tableMetaDataCustomizer;
   }



   /*******************************************************************************
    * Fluent setter for tableMetaDataCustomizer
    *
    * @param tableMetaDataCustomizer
    * A metadata MetaDataCustomizerInterface object to apply to all tables produced
    * by this QBit.
    *
    * @return this
    *******************************************************************************/
   public WebhooksQBitConfig withTableMetaDataCustomizer(MetaDataCustomizerInterface<QTableMetaData> tableMetaDataCustomizer)
   {
      this.tableMetaDataCustomizer = tableMetaDataCustomizer;
      return (this);
   }

}
