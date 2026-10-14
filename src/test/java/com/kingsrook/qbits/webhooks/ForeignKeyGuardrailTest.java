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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinOn;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Referential integrity guardrail: docs/ddl/webhooks-postgres.sql, applied to
 ** a real PostgreSQL, has a foreign key for every join and every table
 ** possible-value field between the qbit's tables (or the relation is in
 ** {@link #EXEMPT} with a reason), each with the intended ON DELETE. A new
 ** table or join without its key fails here. The upgrade script is checked
 ** too: it quarantines orphans, adds the same keys, and is idempotent.
 *******************************************************************************/
class ForeignKeyGuardrailTest
{
   private static final Path DDL     = Path.of("docs/ddl/webhooks-postgres.sql");
   private static final Path UPGRADE = Path.of("docs/ddl/webhooks-foreign-keys-upgrade.sql");

   /////////////////////////////////////////////////////////////////////////////
   // child_table.child_column -> parent_table : ON DELETE (pg confdeltype)    //
   // c = CASCADE, n = SET NULL, r = RESTRICT                                  //
   /////////////////////////////////////////////////////////////////////////////
   private static final Map<String, String> EXPECTED = new TreeMap<>(Map.of(
      "webhooks_endpoint_secret.endpoint_id -> webhooks_endpoint", "c",
      "webhooks_subscription.endpoint_id -> webhooks_endpoint", "c",
      "webhooks_delivery.endpoint_id -> webhooks_endpoint", "r",
      "webhooks_delivery.subscription_id -> webhooks_subscription", "n",
      "webhooks_delivery_attempt.delivery_id -> webhooks_delivery", "c",
      "webhooks_delivery_attempt.endpoint_id -> webhooks_endpoint", "r"));

   /////////////////////////////////////////////////////////////////////////////
   // relations that are deliberately not foreign keys, with the reason       //
   /////////////////////////////////////////////////////////////////////////////
   private static final Map<String, String> EXEMPT = Map.of();

   private static PostgreSQLContainer postgres;



   /*******************************************************************************
    ** Start PostgreSQL once (a missing Docker fails in CI, skips locally).
    *******************************************************************************/
   @BeforeAll
   static void startPostgres()
   {
      boolean docker;
      try
      {
         docker = DockerClientFactory.instance().isDockerAvailable();
      }
      catch(RuntimeException e)
      {
         docker = false;
      }
      if(!docker && System.getenv("CI") != null)
      {
         Assertions.fail("Docker is required for the foreign key guardrail in CI");
      }
      Assumptions.assumeTrue(docker, "Docker is not available");
      postgres = new PostgreSQLContainer("postgres:17-alpine").withDatabaseName("webhooks").withUsername("qqq").withPassword("qqq");
      postgres.start();
   }



   /*******************************************************************************
    ** Stop PostgreSQL.
    *******************************************************************************/
   @AfterAll
   static void stopPostgres()
   {
      if(postgres != null)
      {
         postgres.stop();
      }
   }



   /*******************************************************************************
    ** A clean schema from the reference DDL.
    *******************************************************************************/
   @BeforeEach
   void setUp() throws Exception
   {
      try(Connection c = connect(); Statement st = c.createStatement())
      {
         st.execute("DROP SCHEMA public CASCADE");
         st.execute("CREATE SCHEMA public");
         st.execute(Files.readString(DDL, StandardCharsets.UTF_8));
      }
   }



   /*******************************************************************************
    ** Clear the QContext the metadata setup opened.
    *******************************************************************************/
   @AfterEach
   void tearDown()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** Every declared relation is a foreign key (or exempt), with the intended
    ** ON DELETE, and every foreign key column leads an index.
    *******************************************************************************/
   @Test
   void everyRelationHasAForeignKey() throws Exception
   {
      TreeSet<String> declared = declaredRelations(WebhooksTestSupport.setUp());
      Map<String, String> keys = foreignKeys();

      List<String> missing = new ArrayList<>();
      for(String relation : declared)
      {
         if(!keys.containsKey(relation) && !EXEMPT.containsKey(relation))
         {
            missing.add(relation);
         }
      }
      assertThat(missing).as("relations declared in metadata without a foreign key in " + DDL + " (add one, or exempt it with a reason)").isEmpty();
      assertThat(declared).as("every expected relation is still declared").containsAll(EXPECTED.keySet());
      assertThat(keys).as("foreign keys and their ON DELETE").isEqualTo(EXPECTED);
      assertThat(unindexedForeignKeys()).as("foreign key columns that lead no index").isEmpty();
   }



   /*******************************************************************************
    ** The upgrade script adds the same keys to a schema without them, recording
    ** and removing orphans first, and is a no-op the second time.
    *******************************************************************************/
   @Test
   void upgradeQuarantinesOrphansAndIsIdempotent() throws Exception
   {
      try(Connection c = connect(); Statement st = c.createStatement())
      {
         dropForeignKeys(st);
         st.execute("""
            INSERT INTO webhooks_endpoint (id, name, url) VALUES (1, 'one', 'https://one.example');
            INSERT INTO webhooks_subscription (id, endpoint_id, event_type_name) VALUES (1, 1, 'person.inserted');
            INSERT INTO webhooks_delivery (id, event_id, event_type_name, endpoint_id, subscription_id, dedup_key, status)
               VALUES (1, 'e1', 'person.inserted', 1, 1, 'd1', 'PENDING'),
                      (2, 'e2', 'person.inserted', 1, 99, 'd2', 'PENDING'),
                      (3, 'e3', 'person.inserted', 98, NULL, 'd3', 'PENDING');
            INSERT INTO webhooks_delivery_attempt (id, delivery_id, endpoint_id, attempt_number, outcome)
               VALUES (1, 1, 1, 1, 'SUCCESS'), (2, 3, 1, 1, 'FAILURE');
            """);

         st.execute(Files.readString(UPGRADE, StandardCharsets.UTF_8));
         assertThat(foreignKeys()).isEqualTo(EXPECTED);
         assertThat(quarantine(st)).containsExactlyInAnyOrder(
            "webhooks_delivery.subscription_id row 2 parent 99 NULLED",
            "webhooks_delivery.endpoint_id row 3 parent 98 DELETED",
            "webhooks_delivery_attempt.delivery_id row 2 parent 3 DELETED");
         assertThat(count(st, "SELECT COUNT(*) FROM webhooks_delivery")).isEqualTo(2);
         assertThat(count(st, "SELECT COUNT(*) FROM webhooks_delivery WHERE id = 2 AND subscription_id IS NULL")).isEqualTo(1);

         st.execute(Files.readString(UPGRADE, StandardCharsets.UTF_8));
         assertThat(foreignKeys()).isEqualTo(EXPECTED);
         assertThat(quarantine(st)).hasSize(3);
      }
   }



   /*******************************************************************************
    ** Deleting an endpoint takes its secrets and subscriptions with it, and is
    ** refused while it has delivery history.
    *******************************************************************************/
   @Test
   void endpointDeleteCascadesConfigurationButNotHistory() throws Exception
   {
      try(Connection c = connect(); Statement st = c.createStatement())
      {
         st.execute("""
            INSERT INTO webhooks_endpoint (id, name, url) VALUES (1, 'one', 'https://one.example'), (2, 'two', 'https://two.example');
            INSERT INTO webhooks_endpoint_secret (endpoint_id, source) VALUES (1, 'GENERATED'), (2, 'GENERATED');
            INSERT INTO webhooks_subscription (id, endpoint_id, event_type_name) VALUES (1, 1, 'person.inserted'), (2, 2, 'person.inserted');
            INSERT INTO webhooks_delivery (id, event_id, event_type_name, endpoint_id, subscription_id, dedup_key, status)
               VALUES (1, 'e1', 'person.inserted', 2, 2, 'd1', 'DELIVERED');
            INSERT INTO webhooks_delivery_attempt (delivery_id, endpoint_id, attempt_number, outcome) VALUES (1, 2, 1, 'SUCCESS');
            """);

         st.execute("DELETE FROM webhooks_endpoint WHERE id = 1");
         assertThat(count(st, "SELECT COUNT(*) FROM webhooks_endpoint_secret WHERE endpoint_id = 1")).isZero();
         assertThat(count(st, "SELECT COUNT(*) FROM webhooks_subscription WHERE endpoint_id = 1")).isZero();

         assertThatThrownBy(() -> st.execute("DELETE FROM webhooks_endpoint WHERE id = 2")).isInstanceOf(SQLException.class).hasMessageContaining("fk_webhooks_delivery");

         st.execute("DELETE FROM webhooks_subscription WHERE id = 2");
         assertThat(count(st, "SELECT COUNT(*) FROM webhooks_delivery WHERE id = 1 AND subscription_id IS NULL")).isEqualTo(1);

         st.execute("DELETE FROM webhooks_delivery WHERE id = 1");
         assertThat(count(st, "SELECT COUNT(*) FROM webhooks_delivery_attempt")).isZero();
         st.execute("DELETE FROM webhooks_endpoint WHERE id = 2");
      }
   }



   /*******************************************************************************
    ** child_table.child_column -> parent_table for every join and table PVS
    ** field between two of the qbit's tables.
    *******************************************************************************/
   static TreeSet<String> declaredRelations(QInstance instance)
   {
      TreeSet<String> relations = new TreeSet<>();
      for(QTableMetaData table : instance.getTables().values())
      {
         if(!isQbitTable(table))
         {
            continue;
         }
         for(QFieldMetaData field : table.getFields().values())
         {
            QPossibleValueSource pvs = field.getPossibleValueSourceName() == null ? null : instance.getPossibleValueSource(field.getPossibleValueSourceName());
            if(pvs != null && pvs.getType() == QPossibleValueSourceType.TABLE && isQbitTable(instance.getTable(pvs.getTableName())))
            {
               relations.add(relation(table, field, instance.getTable(pvs.getTableName())));
            }
         }
      }
      for(QJoinMetaData join : instance.getJoins().values())
      {
         QTableMetaData left  = instance.getTable(join.getLeftTable());
         QTableMetaData right = instance.getTable(join.getRightTable());
         if(!isQbitTable(left) || !isQbitTable(right))
         {
            continue;
         }
         for(JoinOn on : join.getJoinOns())
         {
            if(on.getLeftField().equals(left.getPrimaryKeyField()))
            {
               relations.add(relation(right, right.getField(on.getRightField()), left));
            }
            else if(on.getRightField().equals(right.getPrimaryKeyField()))
            {
               relations.add(relation(left, left.getField(on.getLeftField()), right));
            }
         }
      }
      return (relations);
   }



   /*******************************************************************************
    ** Whether a table is one of the qbit's.
    *******************************************************************************/
   private static boolean isQbitTable(QTableMetaData table)
   {
      return (table != null && table.getName().startsWith("webhooks_"));
   }



   /*******************************************************************************
    ** A relation key.
    *******************************************************************************/
   private static String relation(QTableMetaData child, QFieldMetaData field, QTableMetaData parent)
   {
      String column = field.getBackendName() != null ? field.getBackendName() : field.getName();
      return (child.getName() + "." + column + " -> " + parent.getName());
   }



   /*******************************************************************************
    ** The single-column foreign keys in the database, with their ON DELETE.
    *******************************************************************************/
   private static Map<String, String> foreignKeys() throws SQLException
   {
      Map<String, String> keys = new TreeMap<>();
      try(Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("""
         SELECT c.conrelid::regclass::text AS child, a.attname AS col, c.confrelid::regclass::text AS parent, c.confdeltype::text AS rule
         FROM pg_constraint c JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
         WHERE c.contype = 'f' AND cardinality(c.conkey) = 1
         """))
      {
         while(rs.next())
         {
            keys.put(rs.getString("child") + "." + rs.getString("col") + " -> " + rs.getString("parent"), rs.getString("rule"));
         }
      }
      return (keys);
   }



   /*******************************************************************************
    ** Foreign key columns that are not the first column of any index.
    *******************************************************************************/
   private static List<String> unindexedForeignKeys() throws SQLException
   {
      List<String> list = new ArrayList<>();
      try(Connection c = connect(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery("""
         SELECT c.conrelid::regclass::text || '.' || a.attname AS col
         FROM pg_constraint c JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
         WHERE c.contype = 'f'
           AND NOT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = c.conrelid AND i.indkey[0] = c.conkey[1])
         """))
      {
         while(rs.next())
         {
            list.add(rs.getString("col"));
         }
      }
      return (list);
   }



   /*******************************************************************************
    ** Drop every foreign key (a schema as it was before they were declared).
    *******************************************************************************/
   private static void dropForeignKeys(Statement st) throws SQLException
   {
      List<String> drops = new ArrayList<>();
      try(ResultSet rs = st.executeQuery("SELECT conrelid::regclass::text AS t, conname FROM pg_constraint WHERE contype = 'f'"))
      {
         while(rs.next())
         {
            drops.add("ALTER TABLE " + rs.getString("t") + " DROP CONSTRAINT " + rs.getString("conname"));
         }
      }
      for(String drop : drops)
      {
         st.execute(drop);
      }
   }



   /*******************************************************************************
    ** The quarantine rows, as text.
    *******************************************************************************/
   private static List<String> quarantine(Statement st) throws SQLException
   {
      List<String> rows = new ArrayList<>();
      try(ResultSet rs = st.executeQuery("SELECT table_name, column_name, row_id, missing_parent_id, action FROM qqq_fk_quarantine ORDER BY id"))
      {
         while(rs.next())
         {
            rows.add(rs.getString(1) + "." + rs.getString(2) + " row " + rs.getString(3) + " parent " + rs.getString(4) + " " + rs.getString(5));
         }
      }
      return (rows);
   }



   /*******************************************************************************
    ** A count.
    *******************************************************************************/
   private static int count(Statement st, String sql) throws SQLException
   {
      try(ResultSet rs = st.executeQuery(sql))
      {
         rs.next();
         return (rs.getInt(1));
      }
   }



   /*******************************************************************************
    ** A connection.
    *******************************************************************************/
   private static Connection connect() throws SQLException
   {
      return (DriverManager.getConnection(postgres.getJdbcUrl(), "qqq", "qqq"));
   }
}
