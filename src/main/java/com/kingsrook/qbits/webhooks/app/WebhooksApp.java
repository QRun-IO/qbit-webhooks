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

package com.kingsrook.qbits.webhooks.app;


import java.io.Serializable;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.SecretSource;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.SecretStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.permissions.WebhooksPermissionChecker;
import com.kingsrook.qbits.webhooks.permissions.WebhooksPermissions;
import com.kingsrook.qbits.webhooks.secrets.SecretCipher;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.WidgetType;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** The Webhooks app: its home dashboard, and the main tables in the nav
 ** (Endpoints, Subscriptions, Deliveries). Signing secrets and delivery
 ** attempts are relations on their parent records. No process is in the nav.
 *******************************************************************************/
public final class WebhooksApp
{
   public static final String HOME_WIDGET         = "webhooksHome";
   public static final String SECRET_VALUE_WIDGET = "webhooksSecretValue";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksApp()
   {
   }



   /*******************************************************************************
    ** The widgets (home and secret value).
    *******************************************************************************/
   public static List<QWidgetMetaData> widgets()
   {
      return (List.of(
         new QWidgetMetaData().withName(HOME_WIDGET).withLabel("Overview").withType(WidgetType.HTML.getType())
            .withCodeReference(new QCodeReference(HomeRenderer.class)).withGridColumns(12).withIsCard(false)
            .withPermissionRules(WebhooksTables.permissionRules()),
         new QWidgetMetaData().withName(SECRET_VALUE_WIDGET).withLabel("Secret Value").withType(WidgetType.HTML.getType())
            .withCodeReference(new QCodeReference(SecretValueRenderer.class)).withGridColumns(12).withIsCard(true)
            .withPermissionRules(WebhooksTables.permissionRules())));
   }



   /*******************************************************************************
    ** The app, with the main tables as children.
    *******************************************************************************/
   public static QAppMetaData app(WebhooksQBitConfig config, Map<String, QTableMetaData> tables)
   {
      QAppMetaData app = new QAppMetaData()
         .withName(config.getAppName())
         .withLabel(config.getAppLabel())
         .withIcon(new QIcon("webhook"))
         .withPermissionRules(WebhooksTables.permissionRules())
         .withWidgets(List.of(HOME_WIDGET));
      for(String name : WebhooksTables.MAIN)
      {
         if(tables.containsKey(name))
         {
            app.withChild(tables.get(name));
         }
      }
      return (app);
   }



   /*******************************************************************************
    ** Home renderer.
    *******************************************************************************/
   public static class HomeRenderer extends AbstractWidgetRenderer
   {
      /*******************************************************************************
       ** Render.
       *******************************************************************************/
      @Override
      public RenderWidgetOutput render(RenderWidgetInput input) throws QException
      {
         WebhooksQBitConfig config = WebhooksRuntime.config();
         return (new RenderWidgetOutput(new RawHTML(null, WebhooksHomeView.render(WebhooksHomeData.load(), config.getTablePathPrefix()))));
      }
   }



   /*******************************************************************************
    ** The value of a generated signing secret, for webhook admins only, behind
    ** a "Show" toggle. It is decrypted on request and never stored elsewhere.
    *******************************************************************************/
   public static class SecretValueRenderer extends AbstractWidgetRenderer
   {
      /*******************************************************************************
       ** Render.
       *******************************************************************************/
      @Override
      public RenderWidgetOutput render(RenderWidgetInput input) throws QException
      {
         return (new RenderWidgetOutput(new RawHTML(null, html(input.getQueryParams()))));
      }



      /*******************************************************************************
       ** The HTML for the secret in the "id" parameter.
       *******************************************************************************/
      static String html(Map<String, String> params) throws QException
      {
         if(!WebhooksPermissionChecker.has(WebhooksPermissions.ADMIN))
         {
            return ("<p>Only webhook administrators can see secret values.</p>");
         }
         Serializable id = params == null ? null : params.get("id");
         if(id == null)
         {
            return ("<p>Open a signing secret to see its value.</p>");
         }
         List<QRecord> rows = SystemWrites.query(WebhooksTables.SECRET, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, id)));
         if(rows.isEmpty())
         {
            return ("<p>Not found.</p>");
         }
         QRecord row = rows.get(0);
         if(SecretStatus.RETIRED.name().equals(row.getValueString("status")))
         {
            return ("<p>This secret is retired and no longer signs anything.</p>");
         }
         if(SecretSource.REFERENCE.name().equals(row.getValueString("source")))
         {
            return ("<p>The value lives in the environment variable <code>" + WebhooksHomeView.esc(row.getValueString("secretRef"))
               + "</code> and is not shown here. Give the receiver the same value.</p>");
         }
         SecretCipher cipher = SecretCipher.fromEnvironment(WebhooksRuntime.config().getEncryptionKeyEnvName());
         if(cipher == null)
         {
            return ("<p>The encryption key is not set in this environment, so the value cannot be shown.</p>");
         }
         String value = cipher.decrypt(row.getValueString("encryptedSecret"), row.getValueInteger("endpointId"));
         return ("<details><summary>Show the secret</summary><p style=\"overflow-wrap:anywhere\"><code>" + WebhooksHomeView.esc(value) + "</code></p></details>"
            + "<p style=\"opacity:.75\">Give this to the receiver to verify the webhook-signature header. Treat it like a password.</p>");
      }
   }
}
