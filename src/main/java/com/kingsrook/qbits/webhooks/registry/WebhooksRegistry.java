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


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.QSupplementalInstanceMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The event types endpoints can subscribe to, stored on the QInstance (so two
 ** instances in one JVM, as in tests, never share it).
 *******************************************************************************/
public class WebhooksRegistry implements QSupplementalInstanceMetaData
{
   public static final String NAME = "com.kingsrook.qbits.webhooks.registry.WebhooksRegistry";

   private final Map<String, WebhookEventType> eventTypes = new LinkedHashMap<>();



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   private WebhooksRegistry()
   {
   }



   /*******************************************************************************
    ** The registry of an instance (created when missing).
    *******************************************************************************/
   public static WebhooksRegistry of(QInstance qInstance)
   {
      return (QSupplementalInstanceMetaData.ofOrWithNew(qInstance, NAME, WebhooksRegistry::new));
   }



   /*******************************************************************************
    ** Register the types implied by the config: table, ESB-table and process
    ** event types.
    *******************************************************************************/
   public void registerFromConfig(QInstance qInstance, WebhooksQBitConfig config)
   {
      for(String table : config.getTableEventSources())
      {
         registerTable(qInstance, table, false);
      }
      for(String table : config.getEsbTableEventSources())
      {
         registerTable(qInstance, table, true);
      }
      for(String process : config.getProcessEventSources())
      {
         register(new WebhookEventType().withName(processTypeName(process, WebhookEventKind.PROCESS_COMPLETED)).withKind(WebhookEventKind.PROCESS_COMPLETED)
            .withProcessName(process).withLabel(processLabel(qInstance, process) + " completed"));
         register(new WebhookEventType().withName(processTypeName(process, WebhookEventKind.PROCESS_FAILED)).withKind(WebhookEventKind.PROCESS_FAILED)
            .withProcessName(process).withLabel(processLabel(qInstance, process) + " failed"));
      }
   }



   /*******************************************************************************
    ** Inserted, updated and deleted types for one table.
    *******************************************************************************/
   private void registerTable(QInstance qInstance, String table, boolean remote)
   {
      QTableMetaData tableMetaData = qInstance.getTable(table);
      String         label         = tableMetaData != null && tableMetaData.getLabel() != null ? tableMetaData.getLabel() : QInstanceEnricher.nameToLabel(table);
      for(WebhookEventKind kind : List.of(WebhookEventKind.TABLE_INSERT, WebhookEventKind.TABLE_UPDATE, WebhookEventKind.TABLE_DELETE))
      {
         String name = tableTypeName(table, kind);
         if(!eventTypes.containsKey(name))
         {
            register(new WebhookEventType().withName(name).withKind(kind).withTableName(table).withRemote(remote).withLabel(label + " " + kind.getSuffix()));
         }
      }
   }



   /*******************************************************************************
    ** {@code <table>.inserted|updated|deleted|stored}.
    *******************************************************************************/
   public static String tableTypeName(String table, WebhookEventKind kind)
   {
      return (table + "." + kind.getSuffix());
   }



   /*******************************************************************************
    ** {@code process.<name>.completed|failed}.
    *******************************************************************************/
   public static String processTypeName(String process, WebhookEventKind kind)
   {
      return ("process." + process + "." + kind.getSuffix());
   }



   /*******************************************************************************
    ** Label of a process, or its name.
    *******************************************************************************/
   private static String processLabel(QInstance qInstance, String process)
   {
      return (qInstance.getProcess(process) != null && qInstance.getProcess(process).getLabel() != null
         ? qInstance.getProcess(process).getLabel() : QInstanceEnricher.nameToLabel(process));
   }



   /*******************************************************************************
    ** Add (or replace) a type.
    *******************************************************************************/
   public WebhooksRegistry register(WebhookEventType eventType)
   {
      eventTypes.put(eventType.getName(), eventType);
      return (this);
   }



   /*******************************************************************************
    ** A type by name, or null.
    *******************************************************************************/
   public WebhookEventType get(String name)
   {
      return (name == null ? null : eventTypes.get(name));
   }



   /*******************************************************************************
    ** All types, in registration order.
    *******************************************************************************/
   public List<WebhookEventType> getAll()
   {
      return (new ArrayList<>(eventTypes.values()));
   }



   /*******************************************************************************
    ** The table types (of the given kinds) for a table.
    *******************************************************************************/
   public List<WebhookEventType> forTable(String tableName, List<WebhookEventKind> kinds)
   {
      List<WebhookEventType> list = new ArrayList<>();
      for(WebhookEventType type : eventTypes.values())
      {
         if(type.getKind() != null && kinds.contains(type.getKind()) && tableName.equals(type.getTableName()))
         {
            list.add(type);
         }
      }
      return (list);
   }



   /*******************************************************************************
    ** The process types (of a kind) for a process.
    *******************************************************************************/
   public List<WebhookEventType> forProcess(String processName, WebhookEventKind kind)
   {
      List<WebhookEventType> list = new ArrayList<>();
      for(WebhookEventType type : eventTypes.values())
      {
         if(kind.equals(type.getKind()) && processName.equals(type.getProcessName()))
         {
            list.add(type);
         }
      }
      return (list);
   }



   /*******************************************************************************
    ** Fill in labels.
    *******************************************************************************/
   @Override
   public void enrich(QInstance qInstance)
   {
      for(WebhookEventType type : eventTypes.values())
      {
         if(!StringUtils.hasContent(type.getLabel()))
         {
            type.setLabel(QInstanceEnricher.nameToLabel(type.getName()));
         }
      }
   }



   /*******************************************************************************
    ** Validate every type.
    *******************************************************************************/
   @Override
   public void validate(QInstance qInstance, QInstanceValidator validator)
   {
      for(WebhookEventType type : eventTypes.values())
      {
         String prefix = "Webhook event type " + type.getName() + ": ";
         validator.assertCondition(StringUtils.hasContent(type.getName()) && type.getName().matches("[A-Za-z0-9_.:-]+"),
            prefix + "needs a name of letters, digits and . _ : -");
         if(!validator.assertCondition(type.getKind() != null, prefix + "needs a kind"))
         {
            continue;
         }
         if(type.getKind().isTable())
         {
            validator.assertCondition(StringUtils.hasContent(type.getTableName()), prefix + "a table type needs a tableName");
            if(StringUtils.hasContent(type.getTableName()) && !Boolean.TRUE.equals(type.getRemote()))
            {
               QTableMetaData table = qInstance.getTable(type.getTableName());
               if(validator.assertCondition(table != null, prefix + "unknown table " + type.getTableName() + " (mark remote types withRemote(true))")
                  && StringUtils.hasContent(type.getFieldName()))
               {
                  validator.assertCondition(table.getFields().containsKey(type.getFieldName()), prefix + "unknown field " + type.getFieldName());
               }
            }
         }
         if(type.getKind().isProcess())
         {
            validator.assertCondition(StringUtils.hasContent(type.getProcessName()), prefix + "a process type needs a processName");
         }
         if(type.getCustomizer() != null)
         {
            validator.validateSimpleCodeReference(prefix + "customizer: ", type.getCustomizer(), WebhookPayloadCustomizerInterface.class);
         }
      }
   }



   /*******************************************************************************
    ** Supplemental metadata name.
    *******************************************************************************/
   @Override
   public String getName()
   {
      return (NAME);
   }
}
