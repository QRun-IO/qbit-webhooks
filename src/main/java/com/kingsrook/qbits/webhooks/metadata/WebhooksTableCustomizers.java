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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.events.SubscriptionMatcher;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.SecretSource;
import com.kingsrook.qbits.webhooks.permissions.WebhooksPermissionChecker;
import com.kingsrook.qbits.webhooks.permissions.WebhooksPermissions;
import com.kingsrook.qbits.webhooks.registry.WebhookEventType;
import com.kingsrook.qbits.webhooks.registry.WebhooksRegistry;
import com.kingsrook.qbits.webhooks.secrets.EnvironmentSecrets;
import com.kingsrook.qbits.webhooks.shared.http.AddressRules;
import com.kingsrook.qbits.webhooks.shared.http.SafeHttpException;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import com.kingsrook.qqq.backend.core.utils.webhooks.WebhookSecret;


/*******************************************************************************
 ** Validation that runs on every write to the people-editable tables, so the
 ** UI, the API and code all get the same rules.
 *******************************************************************************/
public final class WebhooksTableCustomizers
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksTableCustomizers()
   {
   }



   /*******************************************************************************
    ** The old record for a record being updated, if any.
    *******************************************************************************/
   static QRecord old(Optional<List<QRecord>> oldRecordList, QRecord record)
   {
      if(oldRecordList == null || oldRecordList.isEmpty())
      {
         return (null);
      }
      Serializable id = record.getValue("id");
      for(QRecord candidate : oldRecordList.get())
      {
         if(Objects.equals(ValueUtils.getValueAsString(candidate.getValue("id")), ValueUtils.getValueAsString(id)))
         {
            return (candidate);
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** The value a record will have after the write: the new value if given,
    ** else the old one.
    *******************************************************************************/
   static Serializable effective(QRecord record, QRecord old, String field)
   {
      if(record.getValues().containsKey(field))
      {
         return (record.getValue(field));
      }
      return (old == null ? null : old.getValue(field));
   }



   /*******************************************************************************
    ** webhooks_endpoint: URL policy, and internal-network settings for admins
    ** only.
    *******************************************************************************/
   public static class EndpointCustomizer implements TableCustomizerInterface
   {
      /*******************************************************************************
       ** Validate.
       *******************************************************************************/
      @Override
      public List<QRecord> preInsertOrUpdate(AbstractActionInput input, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
      {
         WebhooksQBitConfig config = WebhooksRuntime.config();
         for(QRecord record : records)
         {
            QRecord old = old(oldRecordList, record);

            boolean internalChanged = changed(record, old, "allowInternalTargets") || changed(record, old, "internalAllowlist");
            boolean internalSet     = Boolean.TRUE.equals(ValueUtils.getValueAsBoolean(effective(record, old, "allowInternalTargets")))
               || StringUtils.hasContent(ValueUtils.getValueAsString(effective(record, old, "internalAllowlist")));
            if(internalChanged && (old != null || internalSet) && !SystemWrites.active() && !WebhooksPermissionChecker.has(WebhooksPermissions.ADMIN))
            {
               record.addError(new BadInputStatusMessage("Only webhook administrators (" + WebhooksPermissions.ADMIN + ") can change internal-network targets."));
               continue;
            }

            List<String> allowlist = new ArrayList<>();
            String       text      = ValueUtils.getValueAsString(effective(record, old, "internalAllowlist"));
            if(StringUtils.hasContent(text))
            {
               allowlist.addAll(Arrays.stream(text.split("[,\\s]+")).filter(s -> !s.isBlank()).toList());
               try
               {
                  new AddressRules(allowlist);
               }
               catch(IllegalArgumentException e)
               {
                  record.addError(new BadInputStatusMessage("Internal allowlist: " + e.getMessage()));
                  continue;
               }
            }

            if(record.getValues().containsKey("url") || old == null)
            {
               String url = ValueUtils.getValueAsString(effective(record, old, "url"));
               boolean allowInternal = Boolean.TRUE.equals(ValueUtils.getValueAsBoolean(effective(record, old, "allowInternalTargets")));
               try
               {
                  WebhooksRuntime.httpClient(WebhooksRuntime.policyFor(config, null, allowInternal ? allowlist : List.of())).checkUrl(url);
               }
               catch(SafeHttpException e)
               {
                  if(e.getReason() != SafeHttpException.Reason.DNS_FAILURE)
                  {
                     record.addError(new BadInputStatusMessage("URL: " + e.getMessage()));
                  }
               }
            }

            Integer perMinute = ValueUtils.getValueAsInteger(effective(record, old, "maxPerMinute"));
            Integer timeout   = ValueUtils.getValueAsInteger(effective(record, old, "timeoutSeconds"));
            if(perMinute != null && perMinute < 1)
            {
               record.addError(new BadInputStatusMessage("Max deliveries per minute must be at least 1 (leave it empty for no limit)."));
            }
            if(timeout != null && (timeout < 1 || timeout * 1000L > config.getTotalTimeoutMillis()))
            {
               record.addError(new BadInputStatusMessage("Timeout must be between 1 and " + (config.getTotalTimeoutMillis() / 1000) + " seconds."));
            }
         }
         return (records);
      }



      /*******************************************************************************
       ** True when the write sets a field to a different value.
       *******************************************************************************/
      private static boolean changed(QRecord record, QRecord old, String field)
      {
         if(!record.getValues().containsKey(field))
         {
            return (false);
         }
         Serializable value = record.getValue(field);
         if(old == null)
         {
            return (Boolean.TRUE.equals(value) || (value instanceof String text && !text.isBlank() && !"false".equalsIgnoreCase(text)));
         }
         return (!Objects.equals(ValueUtils.getValueAsString(old.getValue(field)), ValueUtils.getValueAsString(value)));
      }
   }



   /*******************************************************************************
    ** webhooks_subscription: a known event type, a valid filter, and read access
    ** to the event's table for the person subscribing.
    *******************************************************************************/
   public static class SubscriptionCustomizer implements TableCustomizerInterface
   {
      /*******************************************************************************
       ** Validate.
       *******************************************************************************/
      @Override
      public List<QRecord> preInsertOrUpdate(AbstractActionInput input, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
      {
         WebhooksRegistry registry = WebhooksRegistry.of(QContext.getQInstance());
         for(QRecord record : records)
         {
            QRecord old = old(oldRecordList, record);
            String  typeName = ValueUtils.getValueAsString(effective(record, old, "eventTypeName"));
            WebhookEventType type = registry.get(typeName);
            if(type == null)
            {
               record.addError(new BadInputStatusMessage("Unknown event type: " + typeName));
               continue;
            }
            String filterJson = ValueUtils.getValueAsString(effective(record, old, "filterJson"));
            if(StringUtils.hasContent(filterJson))
            {
               try
               {
                  SubscriptionMatcher.parseFilter(filterJson);
               }
               catch(QException e)
               {
                  record.addError(new BadInputStatusMessage(e.getMessage()));
                  continue;
               }
            }
            if(!SystemWrites.active() && type.getTableName() != null && QContext.getQInstance().getTable(type.getTableName()) != null
               && QContext.getQSession() != null && !PermissionsHelper.hasTablePermission(input, type.getTableName(), TablePermissionSubType.READ))
            {
               record.addError(new BadInputStatusMessage("You cannot subscribe to " + typeName + " because you cannot read the " + type.getTableName() + " table."));
            }
         }
         return (records);
      }
   }



   /*******************************************************************************
    ** webhooks_endpoint_secret: people add environment references only;
    ** generated secrets come from the Rotate Signing Secret action.
    *******************************************************************************/
   public static class SecretCustomizer implements TableCustomizerInterface
   {
      /*******************************************************************************
       ** Validate.
       *******************************************************************************/
      @Override
      public List<QRecord> preInsertOrUpdate(AbstractActionInput input, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
      {
         if(SystemWrites.active())
         {
            return (records);
         }
         for(QRecord record : records)
         {
            QRecord old = old(oldRecordList, record);
            if(record.getValues().containsKey("encryptedSecret") || record.getValues().containsKey("fingerprint") && old == null)
            {
               record.removeValue("encryptedSecret");
               record.removeValue("fingerprint");
            }
            String source = ValueUtils.getValueAsString(effective(record, old, "source"));
            if(old == null && !SecretSource.REFERENCE.name().equals(source))
            {
               record.addError(new BadInputStatusMessage("Add generated secrets with the Rotate Signing Secret action; here you can only add an environment reference."));
               continue;
            }
            if(SecretSource.REFERENCE.name().equals(source))
            {
               String ref = ValueUtils.getValueAsString(effective(record, old, "secretRef"));
               if(!StringUtils.hasContent(ref) || !ref.matches(EnvironmentSecrets.NAME_PATTERN))
               {
                  record.addError(new BadInputStatusMessage("Environment variable must be a variable name (letters, digits and _), never the secret itself."));
                  continue;
               }
               String value = EnvironmentSecrets.lookup(ref);
               if(value != null)
               {
                  try
                  {
                     record.setValue("fingerprint", WebhookSecret.parse(value).fingerprint());
                  }
                  catch(IllegalArgumentException e)
                  {
                     record.addError(new BadInputStatusMessage("The secret in " + ref + " is too short (at least 16 bytes) or not a valid whsec_ value."));
                  }
               }
            }
         }
         return (records);
      }
   }
}
