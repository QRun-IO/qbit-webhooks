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


import java.util.List;
import com.kingsrook.qbits.webhooks.esb.WebhooksEsbSource;
import com.kingsrook.qbits.webhooks.metadata.WebhooksHelp;
import com.kingsrook.qqq.backend.core.actions.help.HelpTargetCatalog;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.help.bundles.HelpBundles;
import com.kingsrook.qqq.backend.core.testing.HelpBundleAssertions;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Shipped help covers the qbit, including its optional ESB integration.
 *******************************************************************************/
class WebhooksHelpBundleTest
{
   /*******************************************************************************
    ** Validate all entries and live references against produced metadata.
    *******************************************************************************/
   @Test
   void bundleIsComplete() throws Exception
   {
      QInstance instance = WebhooksTestSupport.setUp(config -> config.withEsbDestinations(List.of("events")));
      WebhooksEsbSource.configure(instance);
      HelpBundleAssertions.forInstance(instance)
         .bundles("help/qbit-webhooks.yaml")
         .owning(target -> target.name() != null && target.name().startsWith("webhooks"))
         .assertComplete();
      // RC.7 requires app/table/process/field coverage; also enforce our widgets and value sources.
      var entries = WebhooksHelp.entries();
      for(var target : HelpTargetCatalog.collect(instance))
      {
         if(target.name() != null && target.name().startsWith("webhooks")
            && List.of("widget", "possibleValueSource").contains(target.kind()))
         {
            assertThat(entries).as(target.key()).containsKey(target.key());
         }
      }
   }



   /*******************************************************************************
    ** Hosts need no help table or ESB integration to register the bundle.
    *******************************************************************************/
   @Test
   void registersWithoutHelpTable() throws Exception
   {
      QInstance instance = WebhooksTestSupport.setUp();
      assertThat(instance.getTable("helpContent")).isNull();
      HelpBundles.syncOnStartup(instance);
      assertThat(HelpBundles.of(instance).getBundles()).singleElement().satisfies(bundle ->
      {
         assertThat(bundle.getId()).isEqualTo("qbit-webhooks");
         assertThat(bundle.getVersion()).as("the help bundle carries the artifact version").isEqualTo(System.getProperty("qbit.artifactVersion"));
      });
   }
}
