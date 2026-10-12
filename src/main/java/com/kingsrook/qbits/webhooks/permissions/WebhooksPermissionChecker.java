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


import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.processes.WebhooksProcesses;
import com.kingsrook.qqq.backend.core.actions.permissions.CustomPermissionChecker;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;


/*******************************************************************************
 ** Maps every action on the qbit's tables, processes, widgets and app to one
 ** of {@code webhooks.view}, {@code webhooks.manage} or {@code webhooks.admin}
 ** (admin implies manage, manage implies view).
 **
 ** <p>Deliveries and attempts are written only by the qbit (its own writes run
 ** in an authorized scope); people and the API can read them and act on them
 ** through the processes.</p>
 *******************************************************************************/
public class WebhooksPermissionChecker implements CustomPermissionChecker
{
   /*******************************************************************************
    ** Check, or throw.
    *******************************************************************************/
   @Override
   public void checkPermissionsThrowing(AbstractActionInput actionInput, MetaDataWithPermissionRules metaData) throws QPermissionDeniedException
   {
      String needed = required(actionInput, metaData);
      if(needed == null)
      {
         throw (new QPermissionDeniedException("Webhook deliveries and attempts are written only by the webhooks qbit. Use the Send Now, Replay or Cancel actions."));
      }
      if(!has(needed))
      {
         throw (new QPermissionDeniedException("This needs the " + needed + " permission."));
      }
   }



   /*******************************************************************************
    ** The permission an action needs (null: nobody may do it directly).
    *******************************************************************************/
   static String required(AbstractActionInput actionInput, MetaDataWithPermissionRules metaData)
   {
      boolean write = actionInput instanceof InsertInput || actionInput instanceof UpdateInput || actionInput instanceof DeleteInput;
      if(metaData instanceof QTableMetaData table)
      {
         if(!write)
         {
            return (WebhooksPermissions.VIEW);
         }
         if(WebhooksTables.SYSTEM_WRITTEN.contains(table.getName()))
         {
            return (null);
         }
         if(WebhooksTables.SECRET.equals(table.getName()) || (actionInput instanceof DeleteInput && WebhooksTables.ENDPOINT.equals(table.getName())))
         {
            return (WebhooksPermissions.ADMIN);
         }
         return (WebhooksPermissions.MANAGE);
      }
      if(metaData instanceof QProcessMetaData process)
      {
         return (WebhooksProcesses.permissionFor(process.getName()));
      }
      if(metaData instanceof com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData widget
         && com.kingsrook.qbits.webhooks.app.WebhooksApp.SECRET_VALUE_WIDGET.equals(widget.getName()))
      {
         return (WebhooksPermissions.ADMIN);
      }
      return (WebhooksPermissions.VIEW);
   }



   /*******************************************************************************
    ** True when the current session holds the permission (or a higher one).
    *******************************************************************************/
   public static boolean has(String permission)
   {
      QSession session = QContext.getQSession();
      if(session == null)
      {
         return (false);
      }
      if(session.hasPermission(WebhooksPermissions.ADMIN))
      {
         return (true);
      }
      if(WebhooksPermissions.ADMIN.equals(permission))
      {
         return (false);
      }
      if(session.hasPermission(WebhooksPermissions.MANAGE))
      {
         return (true);
      }
      return (WebhooksPermissions.VIEW.equals(permission) && session.hasPermission(WebhooksPermissions.VIEW));
   }
}
