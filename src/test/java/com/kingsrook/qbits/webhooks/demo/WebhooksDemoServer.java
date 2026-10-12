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

package com.kingsrook.qbits.webhooks.demo;


import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.WebhooksQBitProducer;
import com.kingsrook.qbits.webhooks.WebhooksRuntime;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport;
import com.kingsrook.qbits.webhooks.WebhooksTestSupport.Receiver;
import com.kingsrook.qbits.webhooks.delivery.DeliveryEngine;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.permissions.WebhooksPermissions;
import com.kingsrook.qbits.webhooks.support.SystemWrites;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleDispatcher;
import com.kingsrook.qqq.backend.core.modules.authentication.implementations.FullyAnonymousAuthenticationModule;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.QueryManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;


/*******************************************************************************
 ** A local demo (for screenshots and manual checks, never shipped): H2, a
 ** cms_blog_post table, the webhooks qbit, Next UI on the given port, and 24
 ** hours of real traffic made by the real engine against local receivers
 ** (one healthy, one failing, one rate-limiting, one internal).
 **
 ** <pre>
 ** ./mvnw -o test-compile exec:java -Dexec.classpathScope=test \
 **    -Dexec.mainClass=com.kingsrook.qbits.webhooks.demo.WebhooksDemoServer -Dexec.args=8099
 ** </pre>
 *******************************************************************************/
public class WebhooksDemoServer extends AbstractQQQApplication
{
   private static final String BLOG = "cms_blog_post";



   /*******************************************************************************
    ** Anonymous, with every webhooks permission (demo only; swapped into the
    ** dispatcher's map by reflection, because it keeps the first module per
    ** type).
    *******************************************************************************/
   public static class DemoAuthenticationModule extends FullyAnonymousAuthenticationModule
   {
      /*******************************************************************************
       ** Session.
       *******************************************************************************/
      @Override
      public QSession createSession(QInstance qInstance, Map<String, String> context)
      {
         QSession session = super.createSession(qInstance, context);
         session.withPermissions(WebhooksPermissions.ALL).withPermission(BLOG + ".read");
         return (session);
      }
   }



   /*******************************************************************************
    ** The database.
    *******************************************************************************/
   static RDBMSBackendMetaData backend()
   {
      RDBMSBackendMetaData backend = new RDBMSBackendMetaData().withName("rdbms").withVendor("h2").withHostName("mem").withDatabaseName("webhooks_demo").withUsername("sa");
      backend.setJdbcUrl(ConnectionManager.getJdbcUrl(backend) + ";DATABASE_TO_UPPER=FALSE");
      return (backend);
   }



   /*******************************************************************************
    ** The instance.
    *******************************************************************************/
   @Override
   public QInstance defineQInstance() throws QException
   {
      QInstance instance = new QInstance();
      instance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      instance.addBackend(backend());
      instance.addTable(new QTableMetaData().withName(BLOG).withLabel("Blog Post").withBackendName("rdbms").withPrimaryKeyField("id")
         .withRecordLabelFormatAndFields("%s", "title")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withBackendName("id"))
         .withField(new QFieldMetaData("title", QFieldType.STRING).withBackendName("title"))
         .withField(new QFieldMetaData("slug", QFieldType.STRING).withBackendName("slug"))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withBackendName("status")));
      instance.addApp(new QAppMetaData().withName("cms").withLabel("CMS").withIcon(new QIcon("article")).withChild(instance.getTable(BLOG)));

      new WebhooksQBitProducer().withQBitConfig(new WebhooksQBitConfig()
            .withBackendName("rdbms")
            .withSourceName("demo")
            .withEncryptionKeyEnvName(WebhooksTestSupport.KEY_PROPERTY)
            .withTableEventSources(List.of(BLOG))
            .withAllowPlainHttp(true)
            .withMaxAttempts(3)
            .withRetryMinutes(List.of(1, 5))
            .withUnhealthyAfterConsecutiveFailures(5)
            .withTablePathPrefix("/app"))
         .produce(instance);
      instance.setHomeAppName("webhooks");
      return (instance);
   }



   /*******************************************************************************
    ** Create tables and 24 hours of traffic.
    *******************************************************************************/
   static void seed(QInstance instance, Receiver receiver) throws Exception
   {
      try(var connection = new ConnectionManager().getConnection(backend()))
      {
         String sql = new String(WebhooksDemoServer.class.getResourceAsStream("/webhooks-h2.sql").readAllBytes());
         StringBuilder kept = new StringBuilder();
         for(String line : sql.split("\n"))
         {
            if(!line.startsWith("--"))
            {
               kept.append(line).append("\n");
            }
         }
         for(String statement : kept.toString().split(";"))
         {
            if(!statement.isBlank())
            {
               QueryManager.executeUpdate(connection, statement);
            }
         }
         QueryManager.executeUpdate(connection, "CREATE TABLE cms_blog_post (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200), slug VARCHAR(200), status VARCHAR(20))");
      }

      QContext.init(instance, new QSession().withPermissions(WebhooksPermissions.ALL).withPermission(BLOG + ".read"));
      Random random = new Random(42);
      receiver.respond(r -> switch(r.path())
      {
         case "/crm" -> Receiver.Response.of(503);
         case "/slack" -> random.nextInt(5) == 0 ? new Receiver.Response(429, Map.of("Retry-After", "60"), "slow down", 0) : Receiver.Response.of(200);
         default -> new Receiver.Response(200, Map.of(), "ok", 15 + random.nextInt(60));
      });

      Integer search = WebhooksTestSupport.endpoint("Search index sync", receiver.url("/search"));
      Integer crm    = WebhooksTestSupport.endpoint("CRM contacts", receiver.url("/crm"));
      Integer slack  = WebhooksTestSupport.endpoint("Slack #content", receiver.url("/slack"));
      Integer next   = WebhooksTestSupport.endpoint("Next.js revalidate", receiver.url("/revalidate"));
      SystemWrites.updateOne(WebhooksTables.ENDPOINT, new QRecord().withValue("id", search).withValue("description", "Keeps the site search index in step with posts."));
      SystemWrites.updateOne(WebhooksTables.ENDPOINT, new QRecord().withValue("id", slack).withValue("maxPerMinute", 30));
      SystemWrites.updateOne(WebhooksTables.ENDPOINT, new QRecord().withValue("id", next).withValue("serialDelivery", true).withValue("timeoutSeconds", 5));
      for(Integer endpoint : List.of(search, next))
      {
         for(String type : List.of("inserted", "updated", "deleted"))
         {
            WebhooksTestSupport.subscribe(endpoint, BLOG + "." + type, s -> { });
         }
      }
      WebhooksTestSupport.subscribe(slack, BLOG + ".inserted", s -> s.withValue("detailLevel", "KEYS_ONLY").withValue("description", "New posts only"));
      WebhooksTestSupport.subscribe(crm, BLOG + ".updated", s -> s.withValue("excludeFields", "slug"));

      Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
      int     n   = 0;
      for(int hour = 23; hour >= 0; hour--)
      {
         WebhooksTestSupport.CLOCK.set(now.minus(Duration.ofHours(hour)).minus(Duration.ofMinutes(random.nextInt(50))));
         int posts = 2 + random.nextInt(hour % 6 == 0 ? 8 : 4);
         for(int i = 0; i < posts; i++)
         {
            n++;
            QRecord post = WebhooksTestSupport.insert(BLOG, new QRecord().withValue("title", "Post " + n).withValue("slug", "post-" + n).withValue("status", "draft"));
            if(random.nextBoolean())
            {
               new UpdateAction().execute(new UpdateInput(BLOG).withRecord(new QRecord().withValue("id", post.getValue("id")).withValue("status", "published")));
            }
            if(random.nextInt(12) == 0)
            {
               new DeleteAction().execute(new DeleteInput(BLOG).withPrimaryKeys(List.of(post.getValue("id"))));
            }
         }
         for(int run = 0; run < 3; run++)
         {
            new DeliveryEngine().runOnce();
            WebhooksTestSupport.CLOCK.advance(Duration.ofMinutes(6));
         }
      }
      WebhooksRuntime.setClock(java.time.Clock.systemUTC());
      QContext.clear();
   }



   /*******************************************************************************
    ** Run.
    *******************************************************************************/
   public static void main(String[] args) throws Exception
   {
      int port = args.length > 0 ? Integer.parseInt(args[0]) : 8099;
      System.setProperty(WebhooksTestSupport.KEY_PROPERTY, java.util.Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));
      java.lang.reflect.Field modules = QAuthenticationModuleDispatcher.class.getDeclaredField("authenticationTypeToModuleClassNameMap");
      modules.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, String> map = (Map<String, String>) modules.get(null);
      map.put(QAuthenticationType.FULLY_ANONYMOUS.getName(), DemoAuthenticationModule.class.getName());
      WebhooksRuntime.setClock(WebhooksTestSupport.CLOCK);

      WebhooksDemoServer application = new WebhooksDemoServer();
      Receiver           receiver    = new Receiver();
      QInstance          seedInstance = application.defineQInstance();
      new com.kingsrook.qqq.backend.core.instances.QInstanceValidator().validate(seedInstance);
      seed(seedInstance, receiver);

      new QApplicationJavalinServer(application).withPort(port).withServeFrontendNext(true).start();
      System.out.println("Webhooks demo on http://localhost:" + port + "/app/webhooks");
   }
}
