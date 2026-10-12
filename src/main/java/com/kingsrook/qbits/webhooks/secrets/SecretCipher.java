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


import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Encrypts generated signing secrets at rest with AES-256-GCM.
 **
 ** <p>The key is 32 random bytes, base64, in the environment variable (or JVM
 ** system property) the config names ({@code WEBHOOKS_SECRET_KEY} by default).
 ** Stored text is {@code v1:<base64(12-byte nonce || ciphertext+tag)>}; the
 ** endpoint id is bound in as associated data, so a ciphertext copied to
 ** another endpoint does not decrypt. Without the key, generated secrets
 ** cannot be created or used, and only environment-reference secrets work.</p>
 *******************************************************************************/
public final class SecretCipher
{
   private static final String       PREFIX    = "v1:";
   private static final int          NONCE_LEN = 12;
   private static final int          TAG_BITS  = 128;
   private static final SecureRandom RANDOM    = new SecureRandom();

   private final byte[] key;



   /*******************************************************************************
    ** Constructor with a raw 32-byte key.
    *******************************************************************************/
   public SecretCipher(byte[] key)
   {
      if(key == null || key.length != 32)
      {
         throw (new IllegalArgumentException("The webhooks secret key must be 32 bytes (base64 of 32 random bytes)"));
      }
      this.key = key.clone();
   }



   /*******************************************************************************
    ** The cipher from the named environment variable or system property, or
    ** null when it is not set.
    **
    ** @throws QException when it is set but is not base64 of 32 bytes
    *******************************************************************************/
   public static SecretCipher fromEnvironment(String name) throws QException
   {
      String value = EnvironmentSecrets.lookup(name);
      if(!StringUtils.hasContent(value))
      {
         return (null);
      }
      try
      {
         return (new SecretCipher(Base64.getDecoder().decode(value.trim())));
      }
      catch(IllegalArgumentException e)
      {
         throw (new QException("The webhooks secret key in " + name + " must be base64 of 32 random bytes"));
      }
   }



   /*******************************************************************************
    ** Encrypt.
    *******************************************************************************/
   public String encrypt(String plaintext, Integer endpointId) throws QException
   {
      try
      {
         byte[] nonce = new byte[NONCE_LEN];
         RANDOM.nextBytes(nonce);
         Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
         cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
         cipher.updateAAD(aad(endpointId));
         byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
         return (PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + sealed.length).put(nonce).put(sealed).array()));
      }
      catch(GeneralSecurityException e)
      {
         throw (new QException("Could not encrypt a webhook signing secret", e));
      }
   }



   /*******************************************************************************
    ** Decrypt.
    *******************************************************************************/
   public String decrypt(String stored, Integer endpointId) throws QException
   {
      if(stored == null || !stored.startsWith(PREFIX))
      {
         throw (new QException("Unrecognized encrypted secret format"));
      }
      try
      {
         byte[] all   = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
         Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
         cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, all, 0, NONCE_LEN));
         cipher.updateAAD(aad(endpointId));
         return (new String(cipher.doFinal(all, NONCE_LEN, all.length - NONCE_LEN), StandardCharsets.UTF_8));
      }
      catch(GeneralSecurityException | IllegalArgumentException e)
      {
         throw (new QException("Could not decrypt a webhook signing secret (wrong key, or the row was altered)"));
      }
   }



   /*******************************************************************************
    ** Associated data: the endpoint id.
    *******************************************************************************/
   private static byte[] aad(Integer endpointId)
   {
      return (("webhooks_endpoint:" + endpointId).getBytes(StandardCharsets.UTF_8));
   }
}
