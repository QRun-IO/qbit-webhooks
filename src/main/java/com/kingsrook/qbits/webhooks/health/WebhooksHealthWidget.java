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


import java.util.List;
import java.util.Set;
import com.kingsrook.qbits.webhooks.support.FilterLinks;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.health.HealthMetaDataProvider;
import com.kingsrook.qqq.backend.core.health.HealthStatus;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.StatisticsData;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** One native status line linking to central health, using the viewer's access.
 *******************************************************************************/
public class WebhooksHealthWidget extends AbstractWidgetRenderer
{
   /*******************************************************************************
    ** Read the central component rollup; never run a probe from the home.
    *******************************************************************************/
   @Override
   public RenderWidgetOutput render(RenderWidgetInput input) throws QException
   {
      String status = "No checks have run yet";
      if(QContext.getQInstance().getTable(HealthMetaDataProvider.COMPONENT_TABLE) != null)
      {
         List<QRecord> records = new QueryAction().execute(new QueryInput(HealthMetaDataProvider.COMPONENT_TABLE)
            .withFilter(new QQueryFilter(new QFilterCriteria("name", QCriteriaOperator.EQUALS, "webhooks")).withLimit(1))
            .withFieldNamesToInclude(Set.of("status"))).getRecords();
         if(!records.isEmpty())
         {
            String stored = records.getFirst().getValueString("status");
            status = List.of("UP", "DEGRADED", "DOWN", "UNKNOWN").contains(stored == null ? "" : stored) ? stored : HealthStatus.UNKNOWN.name();
         }
      }
      return (new RenderWidgetOutput(new StatisticsData().withCount(status).withCountContext("Webhooks - System Health")
         .withCountURL(FilterLinks.href("/app/" + HealthMetaDataProvider.CHECK_TABLE,
            new QQueryFilter(new QFilterCriteria("component", QCriteriaOperator.EQUALS, "webhooks"))))));
   }
}
