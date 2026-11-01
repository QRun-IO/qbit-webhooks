/*
 * QQQ - Low-code Application Framework for Engineers.
 * Copyright (C) 2021-2025.  Kingsrook, LLC
 * 651 N Broad St Ste 205 # 6917 | Middletown DE 19709 | United States
 * contact@kingsrook.com
 * https://github.com/Kingsrook/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.kingsrook.qbits.webhooks.processes;


import java.util.List;
import com.kingsrook.qbits.webhooks.BaseTest;
import com.kingsrook.qqq.backend.core.actions.processes.QProcessCallback;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Runs SendTestEventToWebhook the way a UI does: records are given only when
 ** the process starts (the init call); the continuation of the first screen
 ** carries no records.
 *******************************************************************************/
class SendTestEventToWebhookProcessTest extends BaseTest
{

   /*******************************************************************************
    ** The records chosen at init must still be there after the input screen.
    *******************************************************************************/
   @Test
   void testRecordsFromInitSurviveTheInputScreen() throws QException
   {
      Integer webhookId = insert(newWebhook("Test receiver"));

      ///////////////////////////////////////////////////////////////////////
      // init: records come in through the callback, as the UI API's init  //
      // passes recordIds; the run stops at the first frontend step        //
      ///////////////////////////////////////////////////////////////////////
      RunProcessInput initInput = new RunProcessInput();
      initInput.setProcessName(SendTestEventToWebhookProcessMetaDataProducer.NAME);
      initInput.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK);
      initInput.setCallback(new QProcessCallback()
      {
         @Override
         public QQueryFilter getQueryFilter()
         {
            return (new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.IN, List.of(webhookId))));
         }
      });
      RunProcessOutput initOutput = new RunProcessAction().execute(initInput);
      assertThat(initOutput.getException()).isEmpty();
      assertThat(initOutput.getProcessState().getNextStepName()).contains("input");

      //////////////////////////////////////////////////////////////////////
      // continue after the input screen, with no callback: the extract   //
      // must still find the webhook chosen at init                       //
      //////////////////////////////////////////////////////////////////////
      RunProcessInput stepInput = new RunProcessInput();
      stepInput.setProcessName(SendTestEventToWebhookProcessMetaDataProducer.NAME);
      stepInput.setProcessUUID(initOutput.getProcessUUID());
      stepInput.setStartAfterStep("input");
      stepInput.setFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK);
      stepInput.addValue("uponSuccessUpdateUnhealthyWebhooksToProbation", true);
      RunProcessOutput stepOutput = new RunProcessAction().execute(stepInput);

      assertThat(stepOutput.getException()).isEmpty();
      assertThat(stepOutput.getProcessState().getNextStepName()).contains("review");
      assertThat(stepOutput.getValues().get("recordCount")).isEqualTo(1);
   }

}
