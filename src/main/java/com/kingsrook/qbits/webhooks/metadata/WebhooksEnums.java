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

package com.kingsrook.qbits.webhooks.metadata;


import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.PossibleValueEnum;


/*******************************************************************************
 ** The qbit's enumerated values. Each is stored as its name (a string) and
 ** shown with its label.
 *******************************************************************************/
public final class WebhooksEnums
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksEnums()
   {
   }



   /*******************************************************************************
    ** Whether an endpoint (or subscription) is used.
    *******************************************************************************/
   public enum ActiveStatus implements PossibleValueEnum<String>
   {
      ACTIVE("Active"),
      PAUSED("Paused"),
      DISABLED("Disabled");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      ActiveStatus(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (name());
      }



      /*******************************************************************************
       ** Label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (label);
      }
   }



   /*******************************************************************************
    ** Endpoint health (set by the qbit). UNHEALTHY endpoints get no deliveries
    ** until a probation attempt succeeds.
    *******************************************************************************/
   public enum HealthStatus implements PossibleValueEnum<String>
   {
      HEALTHY("Healthy"),
      PROBATION("Probation"),
      UNHEALTHY("Unhealthy");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      HealthStatus(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (name());
      }



      /*******************************************************************************
       ** Label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (label);
      }
   }



   /*******************************************************************************
    ** Where a delivery is in its life.
    *******************************************************************************/
   public enum DeliveryStatus implements PossibleValueEnum<String>
   {
      PENDING("Pending"),
      SENDING("Sending"),
      RETRY_WAIT("Waiting to retry"),
      DELIVERED("Delivered"),
      DEAD_LETTER("Dead letter"),
      CANCELLED("Cancelled");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      DeliveryStatus(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (name());
      }



      /*******************************************************************************
       ** Label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (label);
      }
   }



   /*******************************************************************************
    ** How one delivery attempt ended.
    *******************************************************************************/
   public enum AttemptOutcome implements PossibleValueEnum<String>
   {
      IN_FLIGHT("In flight"),
      SUCCEEDED("Succeeded"),
      FAILED("Failed"),
      RATE_LIMITED("Rate limited (429)"),
      BLOCKED("Blocked by policy"),
      LEASE_EXPIRED("Lease expired");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      AttemptOutcome(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (name());
      }



      /*******************************************************************************
       ** Label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (label);
      }
   }



   /*******************************************************************************
    ** What produced an event.
    *******************************************************************************/
   public enum EventSource implements PossibleValueEnum<String>
   {
      TABLE("Table change"),
      ESB("ESB event"),
      PROCESS("Process"),
      CUSTOM("Custom event"),
      CHANNEL("Channel (another qbit)"),
      TEST("Test send");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      EventSource(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (name());
      }



      /*******************************************************************************
       ** Label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (label);
      }
   }



   /*******************************************************************************
    ** A signing secret's place in rotation.
    *******************************************************************************/
   public enum SecretStatus implements PossibleValueEnum<String>
   {
      ACTIVE("Active"),
      PREVIOUS("Previous (still signing until it expires)"),
      RETIRED("Retired");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      SecretStatus(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (name());
      }



      /*******************************************************************************
       ** Label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (label);
      }
   }



   /*******************************************************************************
    ** Where a signing secret's value lives.
    *******************************************************************************/
   public enum SecretSource implements PossibleValueEnum<String>
   {
      GENERATED("Generated (stored encrypted)"),
      REFERENCE("Environment reference");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      SecretSource(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (name());
      }



      /*******************************************************************************
       ** Label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (label);
      }
   }



   /*******************************************************************************
    ** How much of the record a subscription sends.
    *******************************************************************************/
   public enum DetailLevel implements PossibleValueEnum<String>
   {
      FULL("Full record"),
      CHANGED_FIELDS("Changed fields only"),
      KEYS_ONLY("Keys only");

      private final String label;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      DetailLevel(String label)
      {
         this.label = label;
      }



      /*******************************************************************************
       ** Id.
       *******************************************************************************/
      @Override
      public String getPossibleValueId()
      {
         return (name());
      }



      /*******************************************************************************
       ** Label.
       *******************************************************************************/
      @Override
      public String getPossibleValueLabel()
      {
         return (label);
      }
   }
}
