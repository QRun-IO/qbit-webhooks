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


import java.util.List;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.app.WebhooksSetupStatus.Step;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The Setup card: states follow what is configured, and it shows on the home.
 *******************************************************************************/
class WebhooksSetupStatusTest
{
   /*******************************************************************************
    ** A fresh install: everything the administrator chooses needs setup, each
    ** with a next step.
    *******************************************************************************/
   @Test
   void freshInstallNeedsSetup()
   {
      List<Step> steps = WebhooksSetupStatus.steps("/admin/webhooks", "WEBHOOKS_SECRET_KEY", 0, false, 0, 0, 0);
      assertThat(steps).extracting(Step::label).containsExactly("Event types", "Encryption key", "Endpoints", "Signing secrets", "Subscriptions");
      assertThat(steps).extracting(Step::state).containsExactly("Needs setup", "Optional", "Needs setup", "Needs setup", "Needs setup");
      assertThat(steps).allSatisfy(step -> assertThat(step.next()).isNotBlank());
      assertThat(steps.get(1).next()).contains("WEBHOOKS_SECRET_KEY");
   }



   /*******************************************************************************
    ** A secretless endpoint is called out; a complete setup is all set up.
    *******************************************************************************/
   @Test
   void progressIsReflected()
   {
      List<Step> partial = WebhooksSetupStatus.steps("/p", "K", 3, true, 2, 1, 0);
      assertThat(partial).extracting(Step::state).containsExactly("Set up", "Set up", "Set up", "Needs setup", "Needs setup");
      assertThat(partial.get(3).next()).contains("1 endpoint(s) have no secret");

      List<Step> done = WebhooksSetupStatus.steps("/p", "K", 3, true, 2, 2, 4);
      assertThat(done).extracting(Step::state).containsOnly("Set up");
   }



   /*******************************************************************************
    ** The live card: an empty install needs endpoints, and an endpoint moves it
    ** forward.
    *******************************************************************************/
   @Test
   void liveStatusFollowsTheTables() throws Exception
   {
      WebhooksTestSupport.setUp();
      List<Step> empty = WebhooksSetupStatus.load();
      assertThat(empty).filteredOn(step -> step.label().equals("Endpoints")).extracting(Step::state).containsExactly("Needs setup");
      assertThat(empty).filteredOn(step -> step.label().equals("Event types")).extracting(Step::state).containsExactly("Set up");

      WebhooksTestSupport.endpoint("crm", "https://example.com/hook");
      assertThat(WebhooksSetupStatus.load()).filteredOn(step -> step.label().equals("Endpoints")).extracting(Step::state).containsExactly("Set up");
   }
}
