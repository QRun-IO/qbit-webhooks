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

package com.kingsrook.qbits.webhooks.support;


import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;


/*******************************************************************************
 ** Links to a filtered table, in the form every QQQ qbit uses and the Next UI
 ** reads: {@code <path>?filter=<url-encoded JSON QQueryFilter>}.
 *******************************************************************************/
public final class FilterLinks
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private FilterLinks()
   {
   }



   /*******************************************************************************
    ** The path (a table path, with any prefix), with the filter as URL-encoded JSON.
    *******************************************************************************/
   public static String href(String path, QQueryFilter filter)
   {
      Map<String, Object> link = new LinkedHashMap<>();
      link.put("criteria", filter.getCriteria());
      link.put("orderBys", filter.getOrderBys());
      link.put("subFilters", filter.getSubFilters());
      link.put("booleanOperator", filter.getBooleanOperator());
      return (path + "?filter=" + URLEncoder.encode(JsonUtils.toJson(link), StandardCharsets.UTF_8));
   }
}
