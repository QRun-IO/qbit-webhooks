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

package com.kingsrook.qbits.webhooks.permissions;


import java.util.List;


/*******************************************************************************
 ** The qbit's permissions (full qbit name, {@code webhooks.*}).
 **
 ** <ul>
 **   <li>{@code webhooks.view}: the app, its home and every table, read-only.</li>
 **   <li>{@code webhooks.manage}: create and edit endpoints and subscriptions;
 **       Send Now, Replay, Cancel and Send Test Event.</li>
 **   <li>{@code webhooks.admin}: signing secrets (rotate, add, retire), the
 **       internal-network settings of an endpoint, deleting endpoints, and
 **       purging old deliveries.</li>
 ** </ul>
 **
 ** <p>Hosts grant {@link #ALL} (plus {@code webhooks.hasAccess}, the base
 ** permission QQQ derives for the app) to their administrators.</p>
 *******************************************************************************/
public final class WebhooksPermissions
{
   public static final String VIEW       = "webhooks.view";
   public static final String MANAGE     = "webhooks.manage";
   public static final String ADMIN      = "webhooks.admin";
   public static final String HAS_ACCESS = "webhooks.hasAccess";

   public static final List<String> ALL = List.of(VIEW, MANAGE, ADMIN, HAS_ACCESS);



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksPermissions()
   {
   }
}
