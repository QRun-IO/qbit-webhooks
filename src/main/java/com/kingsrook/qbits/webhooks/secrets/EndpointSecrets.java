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

package com.kingsrook.qbits.webhooks.secrets;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.SecretSource;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.SecretStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import com.kingsrook.qqq.backend.core.utils.webhooks.WebhookSecret;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Signing secrets of endpoints ({@code webhooks_endpoint_secret}).
 **
 ** <p>A secret is either GENERATED (random, {@code whsec_} form, stored
 ** encrypted with {@link SecretCipher}) or a REFERENCE (the name of an
 ** environment variable holding it). A secret value is never stored in plain
 ** text, logged, audited or returned by a query; only its fingerprint is.</p>
 **
 ** <p>Rotation: the ACTIVE secret becomes PREVIOUS (still signing until
 ** {@code expiresDate}, the config's overlap), and a new ACTIVE secret is
 ** created. Every delivery is signed with every usable secret, so a receiver
 ** can switch secrets at any time within the overlap.</p>
 *******************************************************************************/
public final class EndpointSecrets
{
   private static final QLogger LOG = QLogger.getLogger(EndpointSecrets.class);



   /*******************************************************************************
    ** A usable secret.
    *******************************************************************************/
   public record SigningKey(WebhookSecret secret, String fingerprint)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private EndpointSecrets()
   {
   }



   /*******************************************************************************
    ** The secrets that sign deliveries to an endpoint now: ACTIVE ones and
    ** PREVIOUS ones that have not expired. Secrets that cannot be read (key
    ** missing, variable unset) are skipped with a warning.
    *******************************************************************************/
   public static List<SigningKey> signingKeys(Integer endpointId) throws QException
   {
      WebhooksQBitConfig config = WebhooksRuntime.config();
      Instant            now    = WebhooksRuntime.clock().instant();
      List<QRecord> rows = SystemWrites.query(WebhooksTables.SECRET, new QQueryFilter()
         .withCriteria(new QFilterCriteria("endpointId", QCriteriaOperator.EQUALS, endpointId))
         .withCriteria(new QFilterCriteria("status", QCriteriaOperator.IN, SecretStatus.ACTIVE.name(), SecretStatus.PREVIOUS.name()))
         .withOrderBy(new QFilterOrderBy("id", false)));

      List<SigningKey> keys   = new ArrayList<>();
      SecretCipher     cipher = null;
      for(QRecord row : rows)
      {
         Instant expires = ValueUtils.getValueAsInstant(row.getValue("expiresDate"));
         if(expires != null && !expires.isAfter(now))
         {
            continue;
         }
         try
         {
            String text;
            if(SecretSource.REFERENCE.name().equals(row.getValueString("source")))
            {
               text = EnvironmentSecrets.lookup(row.getValueString("secretRef"));
               if(text == null)
               {
                  LOG.warn("A webhook signing secret reference is not set in the environment", logPair("endpointId", endpointId), logPair("secretId", row.getValue("id")));
                  continue;
               }
            }
            else
            {
               if(cipher == null)
               {
                  cipher = SecretCipher.fromEnvironment(config.getEncryptionKeyEnvName());
               }
               if(cipher == null)
               {
                  LOG.warn("A generated webhook signing secret cannot be used: the encryption key is not set", logPair("endpointId", endpointId),
                     logPair("keyVariable", config.getEncryptionKeyEnvName()));
                  continue;
               }
               text = cipher.decrypt(row.getValueString("encryptedSecret"), endpointId);
            }
            WebhookSecret secret = WebhookSecret.parse(text);
            keys.add(new SigningKey(secret, secret.fingerprint()));
         }
         catch(QException | IllegalArgumentException e)
         {
            LOG.warn("A webhook signing secret could not be read", logPair("endpointId", endpointId), logPair("secretId", row.getValue("id")), logPair("reason", e.getMessage()));
         }
      }
      return (keys);
   }



   /*******************************************************************************
    ** True when generated secrets can be created (the encryption key is set).
    *******************************************************************************/
   public static boolean canGenerate() throws QException
   {
      WebhooksQBitConfig config = WebhooksRuntime.config();
      return (config != null && SecretCipher.fromEnvironment(config.getEncryptionKeyEnvName()) != null);
   }



   /*******************************************************************************
    ** Rotate: any ACTIVE secret becomes PREVIOUS (signing until the overlap
    ** ends), and a new generated secret becomes ACTIVE.
    **
    ** @return the new secret's text (whsec_...), to show once
    *******************************************************************************/
   public static String rotate(Integer endpointId, String note) throws QException
   {
      WebhooksQBitConfig config = WebhooksRuntime.config();
      SecretCipher       cipher = SecretCipher.fromEnvironment(config.getEncryptionKeyEnvName());
      if(cipher == null)
      {
         throw (new QException("Signing secrets cannot be generated: set the " + config.getEncryptionKeyEnvName()
            + " environment variable to base64 of 32 random bytes, or add a secret that references an environment variable."));
      }
      Instant now     = WebhooksRuntime.clock().instant();
      Instant expires = now.plus(Duration.ofHours(config.getRotationOverlapHours()));

      return (SystemWrites.call(() ->
      {
         List<QRecord> active = SystemWrites.query(WebhooksTables.SECRET, new QQueryFilter()
            .withCriteria(new QFilterCriteria("endpointId", QCriteriaOperator.EQUALS, endpointId))
            .withCriteria(new QFilterCriteria("status", QCriteriaOperator.EQUALS, SecretStatus.ACTIVE.name())));
         List<QRecord> demote = new ArrayList<>();
         for(QRecord row : active)
         {
            demote.add(new QRecord().withValue("id", row.getValue("id")).withValue("status", SecretStatus.PREVIOUS.name()).withValue("expiresDate", expires));
         }
         SystemWrites.update(WebhooksTables.SECRET, demote, null);

         WebhookSecret secret = WebhookSecret.generate();
         QRecord inserted = SystemWrites.insertOne(WebhooksTables.SECRET, new QRecord()
            .withValue("endpointId", endpointId)
            .withValue("status", SecretStatus.ACTIVE.name())
            .withValue("source", SecretSource.GENERATED.name())
            .withValue("encryptedSecret", cipher.encrypt(secret.reveal(), endpointId))
            .withValue("fingerprint", secret.fingerprint())
            .withValue("note", note));
         if(CollectionUtils.nullSafeHasContents(inserted.getErrors()))
         {
            throw (new QException("Could not store the new signing secret: " + inserted.getErrors().get(0).getMessage()));
         }
         return (secret.reveal());
      }));
   }



   /*******************************************************************************
    ** Retire PREVIOUS secrets whose overlap has ended. Returns how many.
    *******************************************************************************/
   public static int retireExpired() throws QException
   {
      Instant now = WebhooksRuntime.clock().instant();
      return (SystemWrites.call(() ->
      {
         List<QRecord> expired = SystemWrites.query(WebhooksTables.SECRET, new QQueryFilter()
            .withCriteria(new QFilterCriteria("status", QCriteriaOperator.EQUALS, SecretStatus.PREVIOUS.name()))
            .withCriteria(new QFilterCriteria("expiresDate", QCriteriaOperator.LESS_THAN_OR_EQUALS, now)));
         List<QRecord> updates = new ArrayList<>();
         for(QRecord row : expired)
         {
            updates.add(new QRecord().withValue("id", row.getValue("id")).withValue("status", SecretStatus.RETIRED.name()).withValue("retiredDate", now));
         }
         SystemWrites.update(WebhooksTables.SECRET, updates, null);
         return (updates.size());
      }));
   }
}
