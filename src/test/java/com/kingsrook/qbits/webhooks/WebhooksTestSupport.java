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


import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import com.kingsrook.qbits.webhooks.metadata.WebhooksTables;
import com.kingsrook.qbits.webhooks.permissions.WebhooksPermissions;
import com.kingsrook.qbits.webhooks.secrets.EndpointSecrets;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QBackendMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryBackendModule;
import com.kingsrook.qqq.backend.core.modules.backend.implementations.memory.MemoryRecordStore;
import com.sun.net.httpserver.HttpServer;


/*******************************************************************************
 ** Shared test setup: an instance with a memory backend, a {@code person}
 ** table, a {@code testProcess}, the qbit, an admin session, a settable clock,
 ** and a local HTTP receiver.
 *******************************************************************************/
public final class WebhooksTestSupport
{
   public static final String MEMORY       = "memory";
   public static final String PERSON       = "person";
   public static final String TEST_PROCESS = "testProcess";
   public static final String KEY_PROPERTY = "WEBHOOKS_TEST_SECRET_KEY";

   public static final MutableClock CLOCK = new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksTestSupport()
   {
   }



   /*******************************************************************************
    ** Default setup.
    *******************************************************************************/
   public static QInstance setUp() throws QException
   {
      return (setUp(config -> { }));
   }



   /*******************************************************************************
    ** Setup with config changes.
    *******************************************************************************/
   public static QInstance setUp(Consumer<WebhooksQBitConfig> tweak) throws QException
   {
      return (setUp(tweak, instance -> { }));
   }



   /*******************************************************************************
    ** Setup with config and instance changes (before the qbit is produced).
    *******************************************************************************/
   public static QInstance setUp(Consumer<WebhooksQBitConfig> tweak, Consumer<QInstance> instanceTweak) throws QException
   {
      MemoryRecordStore.fullReset();
      CLOCK.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
      WebhooksRuntime.setClock(CLOCK);
      WebhooksRuntime.setHostResolver(null);
      System.setProperty(KEY_PROPERTY, Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes()));

      QInstance instance = baseInstance();
      instance.addBackend(new QBackendMetaData().withName(MEMORY).withBackendType(MemoryBackendModule.class));
      addAppTables(instance, MEMORY);
      instanceTweak.accept(instance);

      WebhooksQBitConfig config = defaultConfig(MEMORY);
      tweak.accept(config);
      new WebhooksQBitProducer().withQBitConfig(config).produce(instance);
      new QInstanceValidator().validate(instance);
      QContext.init(instance, adminSession());
      return (instance);
   }



   /*******************************************************************************
    ** An instance with anonymous authentication.
    *******************************************************************************/
   public static QInstance baseInstance()
   {
      QInstance instance = new QInstance();
      instance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("anonymous").withType(QAuthenticationType.FULLY_ANONYMOUS));
      return (instance);
   }



   /*******************************************************************************
    ** The person table and the test process.
    *******************************************************************************/
   public static void addAppTables(QInstance instance, String backend)
   {
      instance.addTable(new QTableMetaData().withName(PERSON).withLabel("Person").withBackendName(backend).withPrimaryKeyField("id")
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withBackendName("id"))
         .withField(new QFieldMetaData("firstName", QFieldType.STRING).withBackendName("first_name"))
         .withField(new QFieldMetaData("lastName", QFieldType.STRING).withBackendName("last_name"))
         .withField(new QFieldMetaData("email", QFieldType.STRING).withBackendName("email"))
         .withField(new QFieldMetaData("status", QFieldType.STRING).withBackendName("status")));
      instance.addProcess(new QProcessMetaData().withName(TEST_PROCESS).withLabel("Test Process")
         .withStep(new QBackendStepMetaData().withName("run").withCode(new QCodeReference(TestProcessStep.class))));
   }



   /*******************************************************************************
    ** The config the tests use.
    *******************************************************************************/
   public static WebhooksQBitConfig defaultConfig(String backend)
   {
      return (new WebhooksQBitConfig()
         .withBackendName(backend)
         .withSourceName("test-app")
         .withTableEventSources(List.of(PERSON))
         .withProcessEventSources(List.of(TEST_PROCESS))
         .withAllowPlainHttp(true)
         .withEncryptionKeyEnvName(KEY_PROPERTY)
         .withMaxAttempts(3)
         .withRetryMinutes(List.of(1, 5))
         .withUnhealthyAfterConsecutiveFailures(3)
         .withProbationAfterMinutes(10)
         .withAutoDisableAfterProbationFailures(2)
         .withHttpTimeoutMillis(2000)
         .withTotalTimeoutMillis(5000)
         .withLeaseSeconds(30));
   }



   /*******************************************************************************
    ** A session with every webhooks permission.
    *******************************************************************************/
   public static QSession adminSession()
   {
      return (new QSession().withPermissions(WebhooksPermissions.ALL));
   }



   /*******************************************************************************
    ** Insert one record (fails the test on errors).
    *******************************************************************************/
   public static QRecord insert(String table, QRecord record) throws QException
   {
      QRecord inserted = new InsertAction().execute(new InsertInput(table).withRecords(List.of(record))).getRecords().get(0);
      if(inserted.getErrors() != null && !inserted.getErrors().isEmpty())
      {
         throw (new IllegalStateException("Insert into " + table + " failed: " + inserted.getErrors()));
      }
      return (inserted);
   }



   /*******************************************************************************
    ** An endpoint on the local receiver (internal target allowlisted) with a
    ** generated signing secret.
    *******************************************************************************/
   public static Integer endpoint(String name, String url) throws QException
   {
      Integer id = insert(WebhooksTables.ENDPOINT, new QRecord().withValue("name", name).withValue("url", url)
         .withValue("allowInternalTargets", true).withValue("internalAllowlist", "127.0.0.1")).getValueInteger("id");
      EndpointSecrets.rotate(id, "test");
      return (id);
   }



   /*******************************************************************************
    ** A subscription.
    *******************************************************************************/
   public static Integer subscribe(Integer endpointId, String eventType, Consumer<QRecord> tweak) throws QException
   {
      QRecord record = new QRecord().withValue("endpointId", endpointId).withValue("eventTypeName", eventType);
      tweak.accept(record);
      return (insert(WebhooksTables.SUBSCRIPTION, record).getValueInteger("id"));
   }



   /*******************************************************************************
    ** All rows of a table, by id.
    *******************************************************************************/
   public static List<QRecord> all(String table) throws QException
   {
      return (new QueryAction().execute(new QueryInput(table).withFilter(new QQueryFilter().withOrderBy(new QFilterOrderBy("id")))).getRecords());
   }



   /*******************************************************************************
    ** The test process: fails when its input asks it to.
    *******************************************************************************/
   public static class TestProcessStep implements BackendStep
   {
      /*******************************************************************************
       ** Run.
       *******************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         if("yes".equals(input.getValueString("fail")))
         {
            throw (new QException("The test process failed on purpose"));
         }
      }
   }



   /*******************************************************************************
    ** A clock the tests move.
    *******************************************************************************/
   public static final class MutableClock extends Clock
   {
      private volatile Instant now;



      /*******************************************************************************
       ** Constructor.
       *******************************************************************************/
      MutableClock(Instant now)
      {
         this.now = now;
      }



      /*******************************************************************************
       ** Set the time.
       *******************************************************************************/
      public void set(Instant instant)
      {
         this.now = instant;
      }



      /*******************************************************************************
       ** Move the time.
       *******************************************************************************/
      public void advance(Duration duration)
      {
         this.now = now.plus(duration);
      }



      /*******************************************************************************
       ** Zone.
       *******************************************************************************/
      @Override
      public ZoneId getZone()
      {
         return (ZoneOffset.UTC);
      }



      /*******************************************************************************
       ** Same clock.
       *******************************************************************************/
      @Override
      public Clock withZone(ZoneId zone)
      {
         return (this);
      }



      /*******************************************************************************
       ** Now.
       *******************************************************************************/
      @Override
      public Instant instant()
      {
         return (now);
      }
   }



   /*******************************************************************************
    ** A local HTTP receiver that records requests.
    *******************************************************************************/
   public static final class Receiver implements AutoCloseable
   {
      /*******************************************************************************
       ** A received request.
       *******************************************************************************/
      public record Request(String path, Map<String, String> headers, byte[] body)
      {
         /*******************************************************************************
          ** Body as text.
          *******************************************************************************/
         public String text()
         {
            return (new String(body, java.nio.charset.StandardCharsets.UTF_8));
         }
      }



      /*******************************************************************************
       ** What to answer.
       *******************************************************************************/
      public record Response(int status, Map<String, String> headers, String body, long delayMillis)
      {
         /*******************************************************************************
          ** A plain status.
          *******************************************************************************/
         public static Response of(int status)
         {
            return (new Response(status, Map.of(), "ok", 0));
         }
      }



      private final HttpServer                   server;
      private final List<Request>                requests  = Collections.synchronizedList(new ArrayList<>());
      private volatile Function<Request, Response> responder = r -> Response.of(200);



      /*******************************************************************************
       ** Start on a free port.
       *******************************************************************************/
      public Receiver() throws IOException
      {
         server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
         server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
         server.createContext("/", exchange ->
         {
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(Locale.ROOT), v.isEmpty() ? null : v.get(0)));
            Request request = new Request(exchange.getRequestURI().getPath(), headers, exchange.getRequestBody().readAllBytes());
            requests.add(request);
            Response response = responder.apply(request);
            if(response.delayMillis() > 0)
            {
               try
               {
                  Thread.sleep(response.delayMillis());
               }
               catch(InterruptedException e)
               {
                  Thread.currentThread().interrupt();
               }
            }
            response.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            byte[] body = response.body() == null ? new byte[0] : response.body().getBytes();
            try
            {
               exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
               if(body.length > 0)
               {
                  try(OutputStream out = exchange.getResponseBody())
                  {
                     out.write(body);
                  }
               }
            }
            catch(IOException e)
            {
               // the client gave up (timeout tests)
            }
            exchange.close();
         });
         server.start();
      }



      /*******************************************************************************
       ** A URL on this receiver.
       *******************************************************************************/
      public String url(String path)
      {
         return ("http://127.0.0.1:" + server.getAddress().getPort() + path);
      }



      /*******************************************************************************
       ** Set the responder.
       *******************************************************************************/
      public void respond(Function<Request, Response> responder)
      {
         this.responder = responder;
      }



      /*******************************************************************************
       ** Requests so far.
       *******************************************************************************/
      public List<Request> requests()
      {
         synchronized(requests)
         {
            return (new ArrayList<>(requests));
         }
      }



      /*******************************************************************************
       ** Stop.
       *******************************************************************************/
      @Override
      public void close()
      {
         server.stop(0);
      }
   }
}
