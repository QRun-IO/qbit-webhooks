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


import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.model.Webhook;
import com.kingsrook.qbits.webhooks.model.WebhookActiveStatus;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.scheduledjobs.ScheduledJob;
import com.kingsrook.qqq.backend.core.scheduler.processes.AbstractRecordSyncToScheduledJobProcess;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 **
 *******************************************************************************/
public class SyncWebhookScheduledJobProcess extends AbstractRecordSyncToScheduledJobProcess
{

   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   protected ScheduledJob customizeScheduledJob(ScheduledJob scheduledJob, QRecord sourceRecord) throws QException
   {
      scheduledJob.setRepeatSeconds(WebhooksQBitConfig.getConfigValue(config -> config.getSendWebhookEventProcessRepeatSeconds()));

      boolean isActive = (WebhookActiveStatus.ACTIVE.getId().equals(sourceRecord.getValueInteger("activeStatusId")));
      scheduledJob.setIsActive(isActive);

      if(!StringUtils.hasContent(scheduledJob.getSchedulerName()))
      {
         scheduledJob.setSchedulerName(WebhooksQBitConfig.getConfigValue(config -> config.getSchedulerName()));
      }

      return (scheduledJob);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   protected String getScheduledJobForeignKeyType()
   {
      return (Webhook.TABLE_NAME);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   protected String getRecordForeignKeyFieldName()
   {
      return ("id");
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   protected String getRecordForeignKeyPossibleValueSourceName()
   {
      return (Webhook.TABLE_NAME);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   protected String getSourceTableName()
   {
      return (Webhook.TABLE_NAME);
   }



   /***************************************************************************
    **
    ***************************************************************************/
   @Override
   protected String getProcessNameScheduledJobParameter()
   {
      return (SendWebhookEventProcessMetaDataProducer.NAME);
   }
}
