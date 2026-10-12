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


import com.kingsrook.qqq.backend.core.instances.QMetaDataVariableInterpreter;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Looks up a secret by name: a JVM system property, then an environment
 ** variable, then QQQ's variable interpreter (which also reads a .env file).
 ** Kubernetes secrets from External Secrets or Infisical arrive as environment
 ** variables, so a reference is just the variable's name.
 *******************************************************************************/
public final class EnvironmentSecrets
{
   /** What a reference may look like: an environment variable name. */
   public static final String NAME_PATTERN = "[A-Za-z_][A-Za-z0-9_]{0,99}";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private EnvironmentSecrets()
   {
   }



   /*******************************************************************************
    ** The value, or null.
    *******************************************************************************/
   public static String lookup(String name)
   {
      if(!StringUtils.hasContent(name) || !name.matches(NAME_PATTERN))
      {
         return (null);
      }
      String value = System.getProperty(name);
      if(!StringUtils.hasContent(value))
      {
         value = System.getenv(name);
      }
      if(!StringUtils.hasContent(value))
      {
         String interpreted = new QMetaDataVariableInterpreter().interpret("${env." + name + "}");
         value = interpreted == null || interpreted.startsWith("${") ? null : interpreted;
      }
      return (StringUtils.hasContent(value) ? value : null);
   }
}
