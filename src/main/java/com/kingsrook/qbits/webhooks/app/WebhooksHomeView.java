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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** Renders the Webhooks home as HTML for an HTML widget.
 **
 ** <p>Next UI's sanitizer removes {@code <style>} elements, so everything is
 ** inline styles. Layout rules (owner, 2026-10-11):</p>
 ** <ul>
 **   <li>one page scroll: nothing scrolls inside the widget;</li>
 **   <li>no horizontal scroll at any width;</li>
 **   <li>list rows are cards whose labelled cells wrap by themselves
 **       ({@code auto-fit} grids with {@code min(100%, ...)}), so they read
 **       at 360 px and at 3200 px without media queries;</li>
 **   <li>dates compact and on one line, plain labels;</li>
 **   <li>colours from the current text colour with opacity, plus a green and
 **       a red that read on light and dark backgrounds.</li>
 ** </ul>
 *******************************************************************************/
public final class WebhooksHomeView
{
   private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);

   private static final String OK_COLOR   = "#2e9e5b";
   private static final String FAIL_COLOR = "#d64545";

   private static final String HOME  = "display:flex;flex-direction:column;gap:1rem;min-width:0;font-size:.95rem;line-height:1.4;";
   private static final String KPIS  = "display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,9.5rem),1fr));gap:.75rem;";
   private static final String KPI   = "border:1px solid rgba(127,127,127,.3);border-radius:8px;padding:.75rem 1rem;min-width:0;";
   private static final String VALUE = "font-size:1.6rem;font-weight:600;line-height:1.2;overflow-wrap:anywhere;";
   private static final String LABEL = "opacity:.75;font-size:.85rem;";
   private static final String CARD  = "border:1px solid rgba(127,127,127,.3);border-radius:8px;padding:1rem;min-width:0;";
   private static final String TITLE = "margin:0 0 .75rem;font-size:1rem;font-weight:600;";
   private static final String MUTED = "opacity:.75;margin:0;";
   private static final String ROW   = "display:grid;grid-template-columns:repeat(auto-fit,minmax(min(100%,11rem),1fr));gap:.25rem 1rem;padding:.6rem 0;"
      + "border-top:1px solid rgba(127,127,127,.2);align-items:start;";
   private static final String CELL  = "min-width:0;overflow-wrap:anywhere;";
   private static final String CAP   = "display:block;font-size:.75rem;font-weight:600;opacity:.65;";



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
      StringBuilder html = new StringBuilder("<div style=\"" + HOME + "\">");
      setup(html, data);
      kpis(html, data, tablePrefix);
      volume(html, data);
      attention(html, data, tablePrefix);
      deadLetters(html, data, tablePrefix);
      return (html.append("</div>").toString());
   }



   /*******************************************************************************
    ** The Setup card: each thing to configure, its state and the next step.
    *******************************************************************************/
   private static void setup(StringBuilder html, WebhooksHomeData data)
   {
      html.append("<div style=\"").append(CARD).append("\"><h3 style=\"").append(TITLE).append("\">Setup</h3>");
      for(WebhooksSetupStatus.Step step : data.getSetup())
      {
         String label = step.href() == null ? esc(step.label()) : "<a href=\"" + esc(step.href()) + "\">" + esc(step.label()) + "</a>";
         String state = WebhooksSetupStatus.NEEDS_SETUP.equals(step.state()) ? "<span style=\"color:" + FAIL_COLOR + "\">" + esc(step.state()) + "</span>"
            : WebhooksSetupStatus.SET_UP.equals(step.state()) ? "<span style=\"color:" + OK_COLOR + "\">" + esc(step.state()) + "</span>" : esc(step.state());
         html.append("<div style=\"").append(ROW).append("\">").append(cell("Step", label)).append(cell("State", state)).append(cell("Next step", esc(step.next()))).append("</div>");
      }
      html.append("</div>");
   }



   /*******************************************************************************
    ** The KPI tiles.
    *******************************************************************************/
   private static void kpis(StringBuilder html, WebhooksHomeData data, String prefix)
   {
      html.append("<div style=\"").append(KPIS).append("\">");
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
      String valueStyle = VALUE + (bad ? "color:" + FAIL_COLOR + ";" : "");
      html.append("<div style=\"").append(KPI).append("\"><div style=\"").append(valueStyle).append("\">");
      if(href != null)
      {
         html.append("<a href=\"").append(esc(href)).append("\" style=\"color:inherit;text-decoration:none\">").append(esc(value)).append("</a>");
      }
      else
      {
         html.append(esc(value));
      }
      html.append("</div><div style=\"").append(LABEL).append("\">").append(esc(label)).append("</div></div>");
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
      html.append("<div style=\"").append(CARD).append("\"><h3 style=\"").append(TITLE).append("\">Delivery attempts, last 24 hours</h3>");
      if(total == 0)
      {
         html.append("<p style=\"").append(MUTED).append("\">No delivery attempts in the last 24 hours.</p></div>");
         return;
      }
      html.append("<div role=\"img\" aria-label=\"").append(total).append(" attempts in the last 24 hours, by hour\" style=\"display:flex;align-items:flex-end;gap:2px;height:7rem;width:100%\">");
      for(int i = 0; i < 24; i++)
      {
         html.append("<div title=\"").append(ok[i]).append(" succeeded, ").append(failed[i]).append(" failed\" style=\"flex:1 1 0;min-width:0;height:100%;display:flex;flex-direction:column-reverse\">")
            .append("<div style=\"background:").append(OK_COLOR).append(";height:").append(pct(ok[i], max)).append("%\"></div>")
            .append("<div style=\"background:").append(FAIL_COLOR).append(";height:").append(pct(failed[i], max)).append("%\"></div></div>");
      }
      html.append("</div><div style=\"display:flex;justify-content:space-between;font-size:.75rem;opacity:.7;margin-top:.25rem\"><span>24 h ago</span><span>Now</span></div>")
         .append("<div style=\"display:flex;gap:1rem;flex-wrap:wrap;font-size:.8rem;margin-top:.5rem\">")
         .append(legend(OK_COLOR, "Succeeded")).append(legend(FAIL_COLOR, "Failed"))
         .append("<span style=\"opacity:.75\">Peak ").append(max).append(" per hour; ").append(data.getRateLimited24h()).append(" rate-limited</span></div></div>");
   }



   /*******************************************************************************
    ** A legend entry.
    *******************************************************************************/
   private static String legend(String color, String label)
   {
      return ("<span><span style=\"display:inline-block;width:.7rem;height:.7rem;border-radius:2px;margin-right:.3rem;vertical-align:-1px;background:" + color + "\"></span>"
         + esc(label) + "</span>");
   }



   /*******************************************************************************
    ** Endpoints needing attention, one card row each.
    *******************************************************************************/
   private static void attention(StringBuilder html, WebhooksHomeData data, String prefix)
   {
      html.append("<div style=\"").append(CARD).append("\"><h3 style=\"").append(TITLE).append("\">Endpoints needing attention</h3>");
      if(data.getEndpointCount() == 0)
      {
         html.append("<p style=\"").append(MUTED).append("\">No endpoints yet. <a href=\"").append(esc(prefix + "/" + WebhooksTables.ENDPOINT))
            .append("\">Add an endpoint</a>, then subscribe it to events.</p></div>");
         return;
      }
      if(data.getAttention().isEmpty())
      {
         html.append("<p style=\"").append(MUTED).append("\">All ").append(data.getEndpointCount()).append(" endpoints are healthy.</p></div>");
         return;
      }
      List<QRecord> rows = data.getAttention();
      for(int i = 0; i < rows.size(); i++)
      {
         QRecord endpoint = rows.get(i);
         String  message  = endpoint.getValueString("lastFailureMessage");
         html.append("<div style=\"").append(ROW).append("\">")
            .append(cell("Endpoint", "<a href=\"" + esc(prefix + "/" + WebhooksTables.ENDPOINT + "/" + endpoint.getValue("id")) + "\">"
               + esc(endpoint.getValueString("name")) + "</a>"))
            .append(cell("Why", esc(data.getAttentionWhy().get(i)) + (message == null ? "" : "<br><span style=\"opacity:.75\">" + esc(clamp(message, 140)) + "</span>")))
            .append(cell("Last failure", when(endpoint.getValue("lastFailureDate"))))
            .append(cell("Last success", when(endpoint.getValue("lastSuccessDate"))))
            .append("</div>");
      }
      html.append("</div>");
   }



   /*******************************************************************************
    ** Newest dead letters.
    *******************************************************************************/
   private static void deadLetters(StringBuilder html, WebhooksHomeData data, String prefix)
   {
      html.append("<div style=\"").append(CARD).append("\"><h3 style=\"").append(TITLE).append("\">Newest dead letters</h3>");
      if(data.getRecentDeadLetters().isEmpty())
      {
         html.append("<p style=\"").append(MUTED).append("\">No dead letters.</p></div>");
         return;
      }
      for(QRecord delivery : data.getRecentDeadLetters())
      {
         html.append("<div style=\"").append(ROW).append("\">")
            .append(cell("Delivery", "<a href=\"" + esc(prefix + "/" + WebhooksTables.DELIVERY + "/" + delivery.getValue("id")) + "\">#"
               + esc(String.valueOf(delivery.getValue("id"))) + "</a>"))
            .append(cell("Event type", esc(delivery.getValueString("eventTypeName"))))
            .append(cell("Last error", esc(clamp(delivery.getValueString("lastError"), 140))))
            .append(cell("Dead-lettered", when(delivery.getValue("deadLetterDate"))))
            .append("</div>");
      }
      html.append("<p style=\"").append(MUTED).append("margin-top:.5rem\">Select dead letters in Deliveries and run Replay to send them again.</p></div>");
   }



   /*******************************************************************************
    ** A labelled cell.
    *******************************************************************************/
   private static String cell(String label, String valueHtml)
   {
      return ("<div style=\"" + CELL + "\"><span style=\"" + CAP + "\">" + esc(label) + "</span>" + valueHtml + "</div>");
   }



   /*******************************************************************************
    ** Percent of max, one decimal.
    *******************************************************************************/
   private static String pct(int value, int max)
   {
      return (String.format(Locale.ROOT, "%.1f", max == 0 ? 0.0 : 100.0 * value / max));
   }



   /*******************************************************************************
    ** A compact date (never wrapped), or a dash.
    *******************************************************************************/
   private static String when(Object value)
   {
      Instant instant = value == null ? null : ValueUtils.getValueAsInstant((Serializable) value);
      return (instant == null ? "-" : "<span style=\"white-space:nowrap\">" + esc(WHEN.format(instant)) + "</span>");
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
