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


import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** Renders the Webhooks home as HTML for an HTML widget.
 **
 ** <p>Layout rules (owner, 2026-10-11): one page scroll (nothing scrolls
 ** inside the widget), no horizontal scroll at any width, tables full width
 ** that become stacked cards on narrow screens, dates compact on one line,
 ** and plain labels. Colours come from the current text colour with opacity,
 ** plus green and red that read on light and dark backgrounds.</p>
 *******************************************************************************/
public final class WebhooksHomeView
{
   private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

   private static final String OK_COLOR   = "#2e9e5b";
   private static final String FAIL_COLOR = "#d64545";

   private static final String STYLE = """
      <style>
      .wh-home{display:flex;flex-direction:column;gap:1rem;min-width:0;font-size:.95rem;line-height:1.4}
      .wh-home *{box-sizing:border-box}
      .wh-kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,10.5rem),1fr));gap:.75rem}
      .wh-kpi{border:1px solid rgba(127,127,127,.3);border-radius:8px;padding:.75rem 1rem;min-width:0}
      .wh-kpi .v{font-size:1.6rem;font-weight:600;line-height:1.2}
      .wh-kpi .l{opacity:.75;font-size:.85rem}
      .wh-kpi.bad .v{color:%FAIL%}
      .wh-card{border:1px solid rgba(127,127,127,.3);border-radius:8px;padding:1rem;min-width:0}
      .wh-card h3{margin:0 0 .75rem;font-size:1rem;font-weight:600}
      .wh-muted{opacity:.75}
      .wh-bars{display:flex;align-items:flex-end;gap:2px;height:7rem;width:100%}
      .wh-bar{flex:1 1 0;min-width:0;display:flex;flex-direction:column-reverse;height:100%}
      .wh-bar .ok{background:%OK%;opacity:.85}
      .wh-bar .no{background:%FAIL%;opacity:.9}
      .wh-axis{display:flex;justify-content:space-between;font-size:.75rem;opacity:.7;margin-top:.25rem}
      .wh-legend{display:flex;gap:1rem;flex-wrap:wrap;font-size:.8rem;margin-top:.5rem}
      .wh-legend i{display:inline-block;width:.7rem;height:.7rem;border-radius:2px;margin-right:.3rem;vertical-align:-1px}
      .wh-table{width:100%;border-collapse:collapse}
      .wh-table th{text-align:left;font-weight:600;font-size:.8rem;opacity:.75;padding:.35rem .5rem;border-bottom:1px solid rgba(127,127,127,.3)}
      .wh-table td{padding:.45rem .5rem;border-bottom:1px solid rgba(127,127,127,.15);vertical-align:top;overflow-wrap:anywhere}
      .wh-table td.nw{white-space:nowrap}
      @media (max-width:640px){
        .wh-table thead{display:none}
        .wh-table tr{display:block;border:1px solid rgba(127,127,127,.25);border-radius:6px;margin-bottom:.5rem;padding:.25rem .5rem}
        .wh-table td{display:flex;gap:.5rem;border:0;padding:.2rem 0}
        .wh-table td::before{content:attr(data-label);font-weight:600;opacity:.7;min-width:6.5rem;flex:0 0 auto}
        .wh-table td.nw{white-space:normal}
      }
      </style>
      """.replace("%OK%", OK_COLOR).replace("%FAIL%", FAIL_COLOR);



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksHomeView()
   {
   }



   /*******************************************************************************
    ** The home.
    *******************************************************************************/
   public static String render(WebhooksHomeData data, String tablePrefix)
   {
      StringBuilder html = new StringBuilder(STYLE).append("<div class=\"wh-home\">");
      kpis(html, data, tablePrefix);
      volume(html, data);
      attention(html, data, tablePrefix);
      deadLetters(html, data, tablePrefix);
      return (html.append("</div>").toString());
   }



   /*******************************************************************************
    ** The KPI row.
    *******************************************************************************/
   private static void kpis(StringBuilder html, WebhooksHomeData data, String prefix)
   {
      html.append("<div class=\"wh-kpis\">");
      kpi(html, String.valueOf(data.getDelivered24h()), "Delivered (24 h)", false, prefix + "/" + WebhooksTables.DELIVERY);
      kpi(html, String.valueOf(data.getFailedAttempts24h()), "Failed attempts (24 h)", data.getFailedAttempts24h() > 0, prefix + "/" + WebhooksTables.DELIVERY);
      kpi(html, String.valueOf(data.getDeadLetters()), "Dead letters", data.getDeadLetters() > 0, prefix + "/" + WebhooksTables.DELIVERY);
      kpi(html, String.valueOf(data.getBacklog()), "Waiting to send", false, prefix + "/" + WebhooksTables.DELIVERY);
      kpi(html, String.valueOf(data.getAttention().size()), "Endpoints needing attention", !data.getAttention().isEmpty(), prefix + "/" + WebhooksTables.ENDPOINT);
      kpi(html, data.getP50Millis() == null ? "None yet" : data.getP50Millis() + " / " + data.getP95Millis() + " ms", "Latency, median / 95th (24 h)", false, null);
      html.append("</div>");
   }



   /*******************************************************************************
    ** One KPI tile.
    *******************************************************************************/
   private static void kpi(StringBuilder html, String value, String label, boolean bad, String href)
   {
      html.append("<div class=\"wh-kpi").append(bad ? " bad" : "").append("\"><div class=\"v\">");
      if(href != null)
      {
         html.append("<a href=\"").append(esc(href)).append("\" style=\"color:inherit;text-decoration:none\">").append(esc(value)).append("</a>");
      }
      else
      {
         html.append(esc(value));
      }
      html.append("</div><div class=\"l\">").append(esc(label)).append("</div></div>");
   }



   /*******************************************************************************
    ** Attempts per hour, last 24 hours.
    *******************************************************************************/
   private static void volume(StringBuilder html, WebhooksHomeData data)
   {
      int[] ok     = data.getHourlyOk();
      int[] failed = data.getHourlyFailed();
      int   max    = 0;
      int   total  = 0;
      for(int i = 0; i < 24; i++)
      {
         max = Math.max(max, ok[i] + failed[i]);
         total += ok[i] + failed[i];
      }
      html.append("<div class=\"wh-card\"><h3>Delivery attempts, last 24 hours</h3>");
      if(total == 0)
      {
         html.append("<p class=\"wh-muted\">No delivery attempts in the last 24 hours.</p></div>");
         return;
      }
      html.append("<div class=\"wh-bars\" role=\"img\" aria-label=\"").append(total).append(" attempts in the last 24 hours, by hour\">");
      for(int i = 0; i < 24; i++)
      {
         double okPct   = 100.0 * ok[i] / max;
         double failPct = 100.0 * failed[i] / max;
         html.append("<div class=\"wh-bar\" title=\"").append(ok[i]).append(" succeeded, ").append(failed[i]).append(" failed\">")
            .append("<div class=\"ok\" style=\"height:").append(String.format(java.util.Locale.ROOT, "%.1f", okPct)).append("%\"></div>")
            .append("<div class=\"no\" style=\"height:").append(String.format(java.util.Locale.ROOT, "%.1f", failPct)).append("%\"></div></div>");
      }
      html.append("</div><div class=\"wh-axis\"><span>24 h ago</span><span>now</span></div>")
         .append("<div class=\"wh-legend\"><span><i style=\"background:").append(OK_COLOR).append("\"></i>Succeeded</span>")
         .append("<span><i style=\"background:").append(FAIL_COLOR).append("\"></i>Failed</span>")
         .append("<span class=\"wh-muted\">Peak ").append(max).append(" per hour; ").append(data.getRateLimited24h()).append(" rate-limited</span></div></div>");
   }



   /*******************************************************************************
    ** Endpoints needing attention (full width table, cards when narrow).
    *******************************************************************************/
   private static void attention(StringBuilder html, WebhooksHomeData data, String prefix)
   {
      html.append("<div class=\"wh-card\"><h3>Endpoints needing attention</h3>");
      if(data.getEndpointCount() == 0)
      {
         html.append("<p class=\"wh-muted\">No endpoints yet. <a href=\"").append(esc(prefix + "/" + WebhooksTables.ENDPOINT)).append("\">Add an endpoint</a>, then subscribe it to events.</p></div>");
         return;
      }
      if(data.getAttention().isEmpty())
      {
         html.append("<p class=\"wh-muted\">All ").append(data.getEndpointCount()).append(" endpoints are healthy.</p></div>");
         return;
      }
      html.append("<table class=\"wh-table\"><thead><tr><th>Endpoint</th><th>Why</th><th>Last failure</th><th>Last success</th></tr></thead><tbody>");
      List<QRecord> rows = data.getAttention();
      for(int i = 0; i < rows.size(); i++)
      {
         QRecord endpoint = rows.get(i);
         html.append("<tr><td data-label=\"Endpoint\"><a href=\"").append(esc(prefix + "/" + WebhooksTables.ENDPOINT + "/" + endpoint.getValue("id"))).append("\">")
            .append(esc(endpoint.getValueString("name"))).append("</a></td>")
            .append("<td data-label=\"Why\">").append(esc(data.getAttentionWhy().get(i)))
            .append(endpoint.getValueString("lastFailureMessage") == null ? "" : "<br><span class=\"wh-muted\">" + esc(clamp(endpoint.getValueString("lastFailureMessage"), 140)) + "</span>")
            .append("</td><td data-label=\"Last failure\" class=\"nw\">").append(when(endpoint.getValue("lastFailureDate")))
            .append("</td><td data-label=\"Last success\" class=\"nw\">").append(when(endpoint.getValue("lastSuccessDate"))).append("</td></tr>");
      }
      html.append("</tbody></table></div>");
   }



   /*******************************************************************************
    ** Newest dead letters.
    *******************************************************************************/
   private static void deadLetters(StringBuilder html, WebhooksHomeData data, String prefix)
   {
      html.append("<div class=\"wh-card\"><h3>Newest dead letters</h3>");
      if(data.getRecentDeadLetters().isEmpty())
      {
         html.append("<p class=\"wh-muted\">No dead letters.</p></div>");
         return;
      }
      html.append("<table class=\"wh-table\"><thead><tr><th>Delivery</th><th>Event type</th><th>Last error</th><th>Dead-lettered</th></tr></thead><tbody>");
      for(QRecord delivery : data.getRecentDeadLetters())
      {
         html.append("<tr><td data-label=\"Delivery\"><a href=\"").append(esc(prefix + "/" + WebhooksTables.DELIVERY + "/" + delivery.getValue("id"))).append("\">#")
            .append(esc(String.valueOf(delivery.getValue("id")))).append("</a></td>")
            .append("<td data-label=\"Event type\">").append(esc(delivery.getValueString("eventTypeName"))).append("</td>")
            .append("<td data-label=\"Last error\">").append(esc(clamp(delivery.getValueString("lastError"), 140))).append("</td>")
            .append("<td data-label=\"Dead-lettered\" class=\"nw\">").append(when(delivery.getValue("deadLetterDate"))).append("</td></tr>");
      }
      html.append("</tbody></table><p class=\"wh-muted\">Select dead letters in Deliveries and run Replay to send them again.</p></div>");
   }



   /*******************************************************************************
    ** A compact date, or a dash.
    *******************************************************************************/
   private static String when(Object value)
   {
      Instant instant = value == null ? null : ValueUtils.getValueAsInstant((java.io.Serializable) value);
      return (instant == null ? "-" : esc(WHEN.format(instant)));
   }



   /*******************************************************************************
    ** Cut long text.
    *******************************************************************************/
   private static String clamp(String text, int max)
   {
      if(text == null)
      {
         return ("");
      }
      return (text.length() <= max ? text : text.substring(0, max - 1) + "…");
   }



   /*******************************************************************************
    ** HTML-escape.
    *******************************************************************************/
   static String esc(String text)
   {
      if(text == null)
      {
         return ("");
      }
      return (text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"));
   }
}
