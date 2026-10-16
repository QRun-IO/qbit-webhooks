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

package com.kingsrook.qbits.webhooks.health;


import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.app.WebhooksApp;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.health.HealthMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.StatisticsData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.WidgetType;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The native home line displays only the persisted central component state.
 *******************************************************************************/
class WebhooksHealthWidgetTest
{
   /*******************************************************************************
    ** No data means unknown, with a component-filtered link to System Health.
    *******************************************************************************/
   @Test
   void emptyAndStoredComponent() throws Exception
   {
      var instance = WebhooksTestSupport.setUp();
      assertThat(instance.getWidget(WebhooksApp.HEALTH_WIDGET).getType()).isEqualTo(WidgetType.STATISTICS.getType());
      StatisticsData data = render();
      assertThat(data.getCount()).isEqualTo("No checks have run yet");
      assertThat(data.getCountURL()).startsWith("/app/health_check?filter=");
      ///////////////////////////////////////////////////////////////////////////////
      // plain URL-encoded JSON (the form every qbit uses), not base64              //
      ///////////////////////////////////////////////////////////////////////////////
      String filter = URLDecoder.decode(data.getCountURL().split("filter=")[1], StandardCharsets.UTF_8);
      QQueryFilter decoded = JsonUtils.toObject(filter, QQueryFilter.class);
      assertThat(decoded.getCriteria()).hasSize(1);
      assertThat(decoded.getCriteria().getFirst().getFieldName()).isEqualTo("component");
      assertThat(decoded.getCriteria().getFirst().getOperator()).isEqualTo(QCriteriaOperator.EQUALS);
      assertThat(decoded.getCriteria().getFirst().getValues()).containsExactly("webhooks");
      new HealthMetaDataProvider().defineAll(instance, WebhooksTestSupport.MEMORY, null);
      QContext.init(instance, new QSession().withPermissions(List.of("health.read")));
      assertThat(render().getCount()).isEqualTo("No checks have run yet");
      QRecord component = SystemWrites.insertOne(HealthMetaDataProvider.COMPONENT_TABLE, new QRecord().withValue("name", "webhooks").withValue("status", "DOWN"));
      for(String status : List.of("UP", "DEGRADED", "DOWN", "UNKNOWN", "S3NT1NEL-secret"))
      {
         SystemWrites.updateOne(HealthMetaDataProvider.COMPONENT_TABLE, new QRecord().withValue("id", component.getValue("id")).withValue("status", status));
         assertThat(render().getCount()).isEqualTo(status.equals("S3NT1NEL-secret") ? "UNKNOWN" : status);
      }
      SystemWrites.updateOne(HealthMetaDataProvider.COMPONENT_TABLE, new QRecord().withValue("id", component.getValue("id")).withValue("status", null));
      assertThat(render().getCount()).isEqualTo("UNKNOWN");
   }



   /*******************************************************************************
    ** Render the one-line native widget.
    *******************************************************************************/
   private StatisticsData render() throws Exception
   {
      return ((StatisticsData) new WebhooksHealthWidget().render(new RenderWidgetInput()).getWidgetData());
   }
}
