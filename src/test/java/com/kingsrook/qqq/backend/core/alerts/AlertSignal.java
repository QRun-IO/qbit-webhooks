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

package com.kingsrook.qqq.backend.core.alerts;


/*******************************************************************************
 ** TEST STAND-IN for the Alerts SPI signal (see {@link QAlerts}).
 *******************************************************************************/
public class AlertSignal
{
   private final String  alertTypeName;
   private String        dedupKey;
   private AlertSeverity severity;
   private String        title;
   private String        summary;



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   public AlertSignal(String alertTypeName)
   {
      this.alertTypeName = alertTypeName;
   }



   /*******************************************************************************
    ** Fluent setter.
    *******************************************************************************/
   public AlertSignal withDedupKey(String dedupKey)
   {
      this.dedupKey = dedupKey;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter.
    *******************************************************************************/
   public AlertSignal withSeverity(AlertSeverity severity)
   {
      this.severity = severity;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter.
    *******************************************************************************/
   public AlertSignal withTitle(String title)
   {
      this.title = title;
      return (this);
   }



   /*******************************************************************************
    ** Fluent setter.
    *******************************************************************************/
   public AlertSignal withSummary(String summary)
   {
      this.summary = summary;
      return (this);
   }



   /*******************************************************************************
    ** Getter.
    *******************************************************************************/
   public String getAlertTypeName()
   {
      return (alertTypeName);
   }



   /*******************************************************************************
    ** Getter.
    *******************************************************************************/
   public String getDedupKey()
   {
      return (dedupKey);
   }



   /*******************************************************************************
    ** Getter.
    *******************************************************************************/
   public AlertSeverity getSeverity()
   {
      return (severity);
   }



   /*******************************************************************************
    ** Getter.
    *******************************************************************************/
   public String getTitle()
   {
      return (title);
   }



   /*******************************************************************************
    ** Getter.
    *******************************************************************************/
   public String getSummary()
   {
      return (summary);
   }
}
