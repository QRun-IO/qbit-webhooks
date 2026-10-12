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

package com.kingsrook.qbits.webhooks.registry;


import java.util.Map;
import com.kingsrook.qbits.webhooks.events.WebhookEvent;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Code hook that shapes the {@code data} object of an event type's payload,
 ** after the subscription's settings (detail level, include and exclude
 ** fields) were applied. Set it with {@link WebhookEventType#withCustomizer}.
 **
 ** <p>It runs when the event is queued (the payload is stored, so a retry sends
 ** exactly the same bytes), once per subscription.</p>
 *******************************************************************************/
public interface WebhookPayloadCustomizerInterface
{
   /*******************************************************************************
    ** Change {@code data} in place (add, remove or rename keys).
    **
    ** @param event        the event being queued
    ** @param subscription the subscription it is queued for (a
    **                     webhooks_subscription record)
    ** @param data         the payload's data object
    *******************************************************************************/
   void customizeData(WebhookEvent event, QRecord subscription, Map<String, Object> data) throws QException;
}
