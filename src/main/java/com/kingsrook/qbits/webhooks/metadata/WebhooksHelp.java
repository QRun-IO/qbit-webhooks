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


import java.util.LinkedHashMap;
import java.util.Map;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import com.kingsrook.qqq.backend.core.model.metadata.help.bundles.HelpBundleEntry;
import com.kingsrook.qqq.backend.core.model.metadata.help.bundles.HelpBundles;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** Applies help/qbit-webhooks.yaml (the QQQ help-bundle format) to the
 ** Webhooks app and its webhooks_* tables: what each table is for and how its
 ** rows get created, shown on the table screen and when it is empty.
 ** Retains inline empty-state help for hosts without the helpContent table.
 ** Core owns bundle parsing, registration and persistent help synchronization.
 *******************************************************************************/
public final class WebhooksHelp
{
   public static final String RESOURCE = "help/qbit-webhooks.yaml";
   public static final String SLOT     = "help";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksHelp()
   {
   }



   /*******************************************************************************
    ** The bundle's entries by key, validated (fails startup if malformed).
    *******************************************************************************/
   public static Map<String, HelpBundleEntry> entries() throws QException
   {
      Map<String, HelpBundleEntry> entries = new LinkedHashMap<>();
      for(HelpBundleEntry entry : HelpBundles.load(RESOURCE, WebhooksHelp.class.getClassLoader()).getEntries())
      {
         entries.put(entry.getKey(), entry);
      }
      return (entries);
   }



   /*******************************************************************************
    ** Apply the help to the app and every webhooks_* table in the instance.
    ** A host's own help on a table or the app is kept (the bundle only fills
    ** the slot).
    *******************************************************************************/
   public static void apply(QInstance qInstance, WebhooksQBitConfig config) throws QException
   {
      Map<String, HelpBundleEntry> entries = entries();
      for(QTableMetaData table : qInstance.getTables().values())
      {
         HelpBundleEntry entry = entries.get("table:" + table.getName() + ";slot:" + SLOT);
         if(entry != null && (table.getHelpContent() == null || !table.getHelpContent().containsKey(SLOT)))
         {
            table.withHelpContent(SLOT, content(entry));
         }
      }
      QAppMetaData    app      = qInstance.getApp(config.getAppName());
      HelpBundleEntry appEntry = entries.get("app:webhooks;slot:" + SLOT);
      if(app != null && appEntry != null && (app.getHelpContent() == null || !app.getHelpContent().containsKey(SLOT)))
      {
         app.withHelpContent(SLOT, content(appEntry));
      }
   }



   /*******************************************************************************
    ** Summary as the first paragraph, then the content, as markdown.
    *******************************************************************************/
   static QHelpContent content(HelpBundleEntry entry)
   {
      return (new QHelpContent().withContentAsMarkdown(entry.getSummary().strip() + "\n\n" + entry.getContent().strip()));
   }

}
