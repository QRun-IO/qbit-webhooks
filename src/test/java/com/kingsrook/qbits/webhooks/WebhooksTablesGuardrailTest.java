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

package com.kingsrook.qbits.webhooks;


import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import com.kingsrook.qbits.webhooks.metadata.WebhooksHelp;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Guardrail (owner requirement 2026-10-14: "all tables should be populated and
 ** the qbit should be fully using them"). For every webhooks_* table:
 ** - runtime code (not just metadata or the home screen) reads or writes it;
 ** - it is either seeded by shipped defaults, or listed here with why it starts
 **   empty (nothing in this qbit is seeded: endpoints, secrets and
 **   subscriptions are the owner's choices, deliveries and attempts are logs);
 ** - it has bundle help that says how its rows get created (shown on the table
 **   screen and when it is empty).
 ** A new table fails this test until it is wired, seeded or explained.
 *******************************************************************************/
class WebhooksTablesGuardrailTest
{
   private static final Path MAIN = Path.of("src", "main", "java", "com", "kingsrook", "qbits", "webhooks");

   /*******************************************************************************
    ** Tables that legitimately start empty, and where their rows come from.
    *******************************************************************************/
   static final Map<String, String> STARTS_EMPTY = Map.of(
      WebhooksTables.ENDPOINT, "host-specific: an administrator adds each receiver URL",
      WebhooksTables.SECRET, "secrets are never seeded: an administrator generates one or names an environment variable",
      WebhooksTables.SUBSCRIPTION, "an administrator picks the event types an endpoint receives",
      WebhooksTables.DELIVERY, "log: written when an event matches a subscription",
      WebhooksTables.ATTEMPT, "log: written on every HTTP attempt");



   /*******************************************************************************
    ** Every table is read or written by runtime code (delivery, events, secrets,
    ** processes), not just declared in metadata or shown on the home.
    *******************************************************************************/
   @Test
   void everyTableIsUsedAtRuntime() throws Exception
   {
      List<String> runtimeSource;
      try(Stream<Path> files = Files.walk(MAIN))
      {
         runtimeSource = files.filter(p -> p.toString().endsWith(".java"))
            .filter(p -> !p.startsWith(MAIN.resolve("metadata")) && !p.startsWith(MAIN.resolve("app")))
            .map(WebhooksTablesGuardrailTest::read).toList();
      }
      for(String table : WebhooksTables.ALL)
      {
         String constant = "WebhooksTables." + constantFor(table);
         assertThat(runtimeSource.stream().anyMatch(source -> source.contains(constant)))
            .as(table + " must be read or written by runtime code, not only declared").isTrue();
      }
   }



   /*******************************************************************************
    ** Every table is seeded (none are) or explained.
    *******************************************************************************/
   @Test
   void everyTableIsSeededOrExplained()
   {
      assertThat(STARTS_EMPTY.keySet()).containsExactlyInAnyOrderElementsOf(WebhooksTables.ALL);
   }



   /*******************************************************************************
    ** Every table and the app have bundle help that says how rows get created.
    *******************************************************************************/
   @Test
   void everyTableHasEmptyStateHelp() throws Exception
   {
      QInstance instance = WebhooksTestSupport.setUp();
      assertThat(WebhooksHelp.entries()).allSatisfy((key, entry) -> assertThat(entry.path("summary").asText().length()).as(key).isLessThanOrEqualTo(120));
      for(String table : WebhooksTables.ALL)
      {
         List<QHelpContent> help = instance.getTable(table).getHelpContent() == null ? null : instance.getTable(table).getHelpContent().get(WebhooksHelp.SLOT);
         assertThat(help).as(table + " table help").isNotEmpty();
         assertThat(help.get(0).getContent()).as(table + " help says how rows get created").contains("How rows get created");
      }
      assertThat(instance.getApp("webhooks").getHelpContent()).containsKey(WebhooksHelp.SLOT);
      assertThat(WebhooksHelp.entries().get("table:webhooks_subscription;slot:help").path("content").asText()).contains("registry");
      assertThat(WebhooksHelp.entries().keySet()).allMatch(key -> !key.startsWith("table:") || instance.getTable(key.substring(6, key.indexOf(';'))) != null,
         "help names only real tables");
   }



   /*******************************************************************************
    ** The WebhooksTables constant name of a table.
    *******************************************************************************/
   private static String constantFor(String table)
   {
      return (switch(table)
      {
         case WebhooksTables.ENDPOINT -> "ENDPOINT";
         case WebhooksTables.SECRET -> "SECRET";
         case WebhooksTables.SUBSCRIPTION -> "SUBSCRIPTION";
         case WebhooksTables.DELIVERY -> "DELIVERY";
         case WebhooksTables.ATTEMPT -> "ATTEMPT";
         default -> throw new IllegalStateException("no constant for " + table);
      });
   }



   /*******************************************************************************
    ** Read a file.
    *******************************************************************************/
   private static String read(Path path)
   {
      try
      {
         return (Files.readString(path, StandardCharsets.UTF_8));
      }
      catch(Exception e)
      {
         throw (new IllegalStateException(e));
      }
   }

}
