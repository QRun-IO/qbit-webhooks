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

package com.kingsrook.qbits.webhooks.registry;


/*******************************************************************************
 ** What makes an event of a type happen.
 **
 ** <ul>
 **   <li>TABLE_INSERT, TABLE_UPDATE, TABLE_DELETE: a record change in a table
 **       (in-process, or from the ESB for remote tables).</li>
 **   <li>TABLE_STORE: an insert or an update.</li>
 **   <li>PROCESS_COMPLETED, PROCESS_FAILED: a process run ended.</li>
 **   <li>CUSTOM: fired from code (a domain event such as "post published",
 **       or a channel delivery from another qbit).</li>
 ** </ul>
 *******************************************************************************/
public enum WebhookEventKind
{
   TABLE_INSERT(true, "inserted"),
   TABLE_UPDATE(true, "updated"),
   TABLE_DELETE(true, "deleted"),
   TABLE_STORE(true, "stored"),
   PROCESS_COMPLETED(false, "completed"),
   PROCESS_FAILED(false, "failed"),
   CUSTOM(false, null);

   private final boolean table;
   private final String  suffix;



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   WebhookEventKind(boolean table, String suffix)
   {
      this.table = table;
      this.suffix = suffix;
   }



   /*******************************************************************************
    ** True for the table kinds.
    *******************************************************************************/
   public boolean isTable()
   {
      return (table);
   }



   /*******************************************************************************
    ** True for the process kinds.
    *******************************************************************************/
   public boolean isProcess()
   {
      return (this == PROCESS_COMPLETED || this == PROCESS_FAILED);
   }



   /*******************************************************************************
    ** The name suffix of auto-registered types ("inserted", ...).
    *******************************************************************************/
   public String getSuffix()
   {
      return (suffix);
   }
}
