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

package com.kingsrook.qbits.webhooks.metadata;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.kingsrook.qbits.webhooks.WebhooksQBitConfig;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.ActiveStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.DetailLevel;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.HealthStatus;
import com.kingsrook.qbits.webhooks.metadata.WebhooksEnums.SecretStatus;
import com.kingsrook.qbits.webhooks.permissions.WebhooksPermissionChecker;
import com.kingsrook.qbits.webhooks.registry.WebhookEventTypePossibleValueSource;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.ChildRecordListRenderer;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.FieldAdornment;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinOn;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.ExposedJoin;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.SectionFactory;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;


/*******************************************************************************
 ** The qbit's five tables. All are standard, visible QQQ tables (queryable,
 ** exportable, audited), secured by the {@code webhooks.*} permissions and the
 ** host's record security locks.
 **
 ** <ul>
 **   <li>{@code webhooks_endpoint}: where to send (URL, status, health,
 **       limits, internal-target allowlist).</li>
 **   <li>{@code webhooks_endpoint_secret}: signing secrets per endpoint
 **       (encrypted or an environment reference; never plaintext).</li>
 **   <li>{@code webhooks_subscription}: which event types an endpoint gets,
 **       with filters and payload shaping.</li>
 **   <li>{@code webhooks_delivery}: one row per event per endpoint (the
 **       outbox): payload, status, retries, dead letter.</li>
 **   <li>{@code webhooks_delivery_attempt}: one row per HTTP attempt (also the
 **       claim: unique on delivery and attempt number).</li>
 ** </ul>
 *******************************************************************************/
public final class WebhooksTables
{
   public static final String ENDPOINT     = "webhooks_endpoint";
   public static final String SECRET       = "webhooks_endpoint_secret";
   public static final String SUBSCRIPTION = "webhooks_subscription";
   public static final String DELIVERY     = "webhooks_delivery";
   public static final String ATTEMPT      = "webhooks_delivery_attempt";

   public static final List<String> ALL = List.of(ENDPOINT, SECRET, SUBSCRIPTION, DELIVERY, ATTEMPT);

   /** The tables in the app nav (the rest are relations on their parents). */
   public static final List<String> MAIN = List.of(ENDPOINT, SUBSCRIPTION, DELIVERY);

   /** Written only by the qbit (read-only to people, through the API too). */
   public static final List<String> SYSTEM_WRITTEN = List.of(DELIVERY, ATTEMPT);

   public static final String PVS_ACTIVE_STATUS   = "webhooks_active_status";
   public static final String PVS_HEALTH_STATUS   = "webhooks_health_status";
   public static final String PVS_DELIVERY_STATUS = "webhooks_delivery_status";
   public static final String PVS_ATTEMPT_OUTCOME = "webhooks_attempt_outcome";
   public static final String PVS_EVENT_SOURCE    = "webhooks_event_source";
   public static final String PVS_SECRET_STATUS   = "webhooks_secret_status";
   public static final String PVS_SECRET_SOURCE   = "webhooks_secret_source";
   public static final String PVS_DETAIL_LEVEL    = "webhooks_detail_level";

   public static final String JOIN_ENDPOINT_SUBSCRIPTION = ENDPOINT + "_join_" + SUBSCRIPTION;
   public static final String JOIN_ENDPOINT_SECRET       = ENDPOINT + "_join_" + SECRET;
   public static final String JOIN_ENDPOINT_DELIVERY     = ENDPOINT + "_join_" + DELIVERY;
   public static final String JOIN_SUBSCRIPTION_DELIVERY = SUBSCRIPTION + "_join_" + DELIVERY;
   public static final String JOIN_DELIVERY_ATTEMPT      = DELIVERY + "_join_" + ATTEMPT;

   public static final String WIDGET_ENDPOINT_SUBSCRIPTIONS = "webhooksEndpointSubscriptions";
   public static final String WIDGET_ENDPOINT_SECRETS       = "webhooksEndpointSecrets";
   public static final String WIDGET_ENDPOINT_DELIVERIES    = "webhooksEndpointDeliveries";
   public static final String WIDGET_SUBSCRIPTION_DELIVERIES = "webhooksSubscriptionDeliveries";
   public static final String WIDGET_DELIVERY_ATTEMPTS      = "webhooksDeliveryAttempts";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private WebhooksTables()
   {
   }



   /*******************************************************************************
    ** Permission rules shared by every qbit table, process, widget and the app.
    ** The custom checker maps actions to webhooks.view / manage / admin.
    *******************************************************************************/
   public static QPermissionRules permissionRules()
   {
      return (new QPermissionRules()
         .withLevel(PermissionLevel.HAS_ACCESS_PERMISSION)
         .withPermissionBaseName("webhooks")
         .withCustomPermissionChecker(new QCodeReference(WebhooksPermissionChecker.class)));
   }



   /*******************************************************************************
    ** Possible-value sources.
    *******************************************************************************/
   public static List<QPossibleValueSource> possibleValueSources()
   {
      return (List.of(
         QPossibleValueSource.newForEnum(PVS_ACTIVE_STATUS, WebhooksEnums.ActiveStatus.values()),
         QPossibleValueSource.newForEnum(PVS_HEALTH_STATUS, WebhooksEnums.HealthStatus.values()),
         QPossibleValueSource.newForEnum(PVS_DELIVERY_STATUS, WebhooksEnums.DeliveryStatus.values()),
         QPossibleValueSource.newForEnum(PVS_ATTEMPT_OUTCOME, WebhooksEnums.AttemptOutcome.values()),
         QPossibleValueSource.newForEnum(PVS_EVENT_SOURCE, WebhooksEnums.EventSource.values()),
         QPossibleValueSource.newForEnum(PVS_SECRET_STATUS, WebhooksEnums.SecretStatus.values()),
         QPossibleValueSource.newForEnum(PVS_SECRET_SOURCE, WebhooksEnums.SecretSource.values()),
         QPossibleValueSource.newForEnum(PVS_DETAIL_LEVEL, WebhooksEnums.DetailLevel.values()),
         WebhookEventTypePossibleValueSource.definition(),
         QPossibleValueSource.newForTable(ENDPOINT),
         QPossibleValueSource.newForTable(SUBSCRIPTION),
         QPossibleValueSource.newForTable(DELIVERY)));
   }



   /*******************************************************************************
    ** All tables.
    *******************************************************************************/
   public static List<QTableMetaData> tables(WebhooksQBitConfig config)
   {
      List<QTableMetaData> tables = List.of(endpoint(config), secret(config), subscription(config), delivery(config), attempt(config));
      for(QTableMetaData table : tables)
      {
         if(SYSTEM_WRITTEN.contains(table.getName()))
         {
            //////////////////////////////////////////////////////////////////
            // no New / Edit / Delete in the UI or API: people act on these  //
            // through the actions; the qbit writes them itself              //
            //////////////////////////////////////////////////////////////////
            table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE);
         }
      }
      return (tables);
   }



   /*******************************************************************************
    ** Joins between the tables.
    *******************************************************************************/
   public static List<QJoinMetaData> joins()
   {
      return (List.of(
         join(JOIN_ENDPOINT_SUBSCRIPTION, ENDPOINT, SUBSCRIPTION, "endpointId"),
         join(JOIN_ENDPOINT_SECRET, ENDPOINT, SECRET, "endpointId"),
         join(JOIN_ENDPOINT_DELIVERY, ENDPOINT, DELIVERY, "endpointId"),
         join(JOIN_SUBSCRIPTION_DELIVERY, SUBSCRIPTION, DELIVERY, "subscriptionId"),
         join(JOIN_DELIVERY_ATTEMPT, DELIVERY, ATTEMPT, "deliveryId")));
   }



   /*******************************************************************************
    ** The child-record widgets that show sub-tables on their parent records.
    *******************************************************************************/
   public static List<QWidgetMetaData> childWidgets()
   {
      List<QWidgetMetaData> widgets = new ArrayList<>();
      widgets.add(ChildRecordListRenderer.widgetMetaDataBuilder(joinFor(JOIN_ENDPOINT_SUBSCRIPTION))
         .withName(WIDGET_ENDPOINT_SUBSCRIPTIONS).withLabel("Subscriptions").withCanAddChildRecord(true).withMaxRows(100)
         .withDisabledFieldsForNewChildRecords(Set.of("endpointId"))
         .getWidgetMetaData().withGridColumns(12).withPermissionRules(permissionRules()));
      widgets.add(ChildRecordListRenderer.widgetMetaDataBuilder(joinFor(JOIN_ENDPOINT_SECRET))
         .withName(WIDGET_ENDPOINT_SECRETS).withLabel("Signing Secrets").withMaxRows(20)
         .withOmitFieldNames(List.of("encryptedSecret", "endpointId"))
         .withOrderBys(List.of(new QFilterOrderBy("id", false)))
         .getWidgetMetaData().withGridColumns(12).withPermissionRules(permissionRules()));
      widgets.add(ChildRecordListRenderer.widgetMetaDataBuilder(joinFor(JOIN_ENDPOINT_DELIVERY))
         .withName(WIDGET_ENDPOINT_DELIVERIES).withLabel("Recent Deliveries").withMaxRows(25)
         .withOnlyIncludeFieldNames(List.of("id", "eventTypeName", "status", "attemptCount", "lastHttpStatus", "occurredDate", "deliveredDate"))
         .withOrderBys(List.of(new QFilterOrderBy("id", false)))
         .getWidgetMetaData().withGridColumns(12).withPermissionRules(permissionRules()));
      widgets.add(ChildRecordListRenderer.widgetMetaDataBuilder(joinFor(JOIN_SUBSCRIPTION_DELIVERY))
         .withName(WIDGET_SUBSCRIPTION_DELIVERIES).withLabel("Recent Deliveries").withMaxRows(25)
         .withOnlyIncludeFieldNames(List.of("id", "status", "attemptCount", "lastHttpStatus", "occurredDate", "deliveredDate"))
         .withOrderBys(List.of(new QFilterOrderBy("id", false)))
         .getWidgetMetaData().withGridColumns(12).withPermissionRules(permissionRules()));
      widgets.add(ChildRecordListRenderer.widgetMetaDataBuilder(joinFor(JOIN_DELIVERY_ATTEMPT))
         .withName(WIDGET_DELIVERY_ATTEMPTS).withLabel("Attempts").withMaxRows(50)
         .withOnlyIncludeFieldNames(List.of("attemptNumber", "outcome", "httpStatus", "durationMillis", "startDate", "errorMessage"))
         .withOrderBys(List.of(new QFilterOrderBy("attemptNumber", true)))
         .getWidgetMetaData().withGridColumns(12).withPermissionRules(permissionRules()));
      return (widgets);
   }



   /*******************************************************************************
    ** One-to-many join on a parent id field.
    *******************************************************************************/
   private static QJoinMetaData join(String name, String parent, String child, String childField)
   {
      return (new QJoinMetaData()
         .withName(name)
         .withType(JoinType.ONE_TO_MANY)
         .withLeftTable(parent)
         .withRightTable(child)
         .withJoinOn(new JoinOn("id", childField)));
   }



   /*******************************************************************************
    ** A join by name (from {@link #joins()}).
    *******************************************************************************/
   private static QJoinMetaData joinFor(String name)
   {
      for(QJoinMetaData join : joins())
      {
         if(join.getName().equals(name))
         {
            return (join);
         }
      }
      throw (new IllegalArgumentException("No join " + name));
   }



   /*******************************************************************************
    ** A table with the common id and dates.
    *******************************************************************************/
   private static QTableMetaData base(WebhooksQBitConfig config, String name, String label, String icon)
   {
      QTableMetaData table = new QTableMetaData()
         .withName(name)
         .withLabel(label)
         .withIcon(new QIcon(icon))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withPermissionRules(permissionRules())
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("createDate", QFieldType.DATE_TIME).withIsEditable(false))
         .withField(new QFieldMetaData("modifyDate", QFieldType.DATE_TIME).withIsEditable(false));
      return (table);
   }



   /*******************************************************************************
    ** Add the host's security fields and locks.
    *******************************************************************************/
   private static List<String> addSecurity(WebhooksQBitConfig config, QTableMetaData table)
   {
      List<String> names = new ArrayList<>();
      for(QFieldMetaData field : config.getSecurityFields())
      {
         table.addField(field.clone());
         names.add(field.getName());
      }
      config.getRecordSecurityLocks().forEach(table::withRecordSecurityLock);
      return (names);
   }



   /*******************************************************************************
    ** A read-only field.
    *******************************************************************************/
   private static QFieldMetaData ro(String name, QFieldType type)
   {
      return (new QFieldMetaData(name, type).withIsEditable(false));
   }



   /*******************************************************************************
    ** A T1 section with the given fields plus the security fields.
    *******************************************************************************/
   private static QFieldSection t1(List<String> security, String... fields)
   {
      List<String> names = new ArrayList<>(List.of(fields));
      names.addAll(security);
      return (SectionFactory.defaultT1(names.toArray(new String[0])));
   }



   /*******************************************************************************
    ** webhooks_endpoint.
    *******************************************************************************/
   static QTableMetaData endpoint(WebhooksQBitConfig config)
   {
      QTableMetaData table = base(config, ENDPOINT, "Webhook Endpoint", "webhook")
         .withRecordLabelFormatAndFields("%s", "name")
         .withField(new QFieldMetaData("name", QFieldType.STRING).withIsRequired(true).withMaxLength(100))
         .withField(new QFieldMetaData("description", QFieldType.TEXT))
         .withField(new QFieldMetaData("url", QFieldType.STRING).withLabel("URL").withIsRequired(true).withMaxLength(500))
         .withField(chip(new QFieldMetaData("activeStatus", QFieldType.STRING).withLabel("Status").withIsRequired(true)
            .withPossibleValueSourceName(PVS_ACTIVE_STATUS).withDefaultValue(ActiveStatus.ACTIVE.name()),
            new String[][] {{"ACTIVE", "play_circle", AdornmentType.ChipValues.COLOR_SUCCESS}, {"PAUSED", "pause_circle", AdornmentType.ChipValues.COLOR_WARNING},
               {"DISABLED", "block", AdornmentType.ChipValues.COLOR_ERROR}}))
         .withField(chip(ro("healthStatus", QFieldType.STRING).withLabel("Health").withPossibleValueSourceName(PVS_HEALTH_STATUS)
            .withDefaultValue(HealthStatus.HEALTHY.name()),
            new String[][] {{"HEALTHY", "favorite", AdornmentType.ChipValues.COLOR_SUCCESS}, {"PROBATION", "healing", AdornmentType.ChipValues.COLOR_WARNING},
               {"UNHEALTHY", "heart_broken", AdornmentType.ChipValues.COLOR_ERROR}}))
         .withField(new QFieldMetaData("serialDelivery", QFieldType.BOOLEAN).withLabel("Deliver In Order").withDefaultValue(false))
         .withField(new QFieldMetaData("maxPerMinute", QFieldType.INTEGER).withLabel("Max Deliveries Per Minute"))
         .withField(new QFieldMetaData("timeoutSeconds", QFieldType.INTEGER).withLabel("Timeout (Seconds)"))
         .withField(new QFieldMetaData("allowUnsigned", QFieldType.BOOLEAN).withLabel("Allow Unsigned Delivery").withDefaultValue(false))
         .withField(new QFieldMetaData("allowInternalTargets", QFieldType.BOOLEAN).withLabel("Allow Internal Targets").withDefaultValue(false))
         .withField(new QFieldMetaData("internalAllowlist", QFieldType.TEXT).withLabel("Internal Allowlist"))
         .withField(ro("consecutiveFailures", QFieldType.INTEGER).withDefaultValue(0))
         .withField(ro("probationFailures", QFieldType.INTEGER).withDefaultValue(0))
         .withField(ro("lastSuccessDate", QFieldType.DATE_TIME).withLabel("Last Success"))
         .withField(ro("lastFailureDate", QFieldType.DATE_TIME).withLabel("Last Failure"))
         .withField(ro("lastFailureMessage", QFieldType.STRING).withMaxLength(500))
         .withField(ro("unhealthySinceDate", QFieldType.DATE_TIME).withLabel("Unhealthy Since"))
         .withField(ro("autoDisabledDate", QFieldType.DATE_TIME).withLabel("Auto-Disabled"));
      List<String> security = addSecurity(config, table);
      table.withUniqueKey(new UniqueKey("name"))
         .withSection(t1(security, "id", "name", "url", "activeStatus", "healthStatus"))
         .withSection(SectionFactory.customT2("delivery", new QIcon("send"), "description", "serialDelivery", "maxPerMinute", "timeoutSeconds", "allowUnsigned"))
         .withSection(SectionFactory.customT2("health", new QIcon("monitor_heart"), "consecutiveFailures", "probationFailures", "lastSuccessDate", "lastFailureDate",
            "lastFailureMessage", "unhealthySinceDate", "autoDisabledDate"))
         .withSection(SectionFactory.customT2("network", new QIcon("lan"), "allowInternalTargets", "internalAllowlist").withLabel("Internal Network (Admin Only)"))
         .withSection(SectionFactory.customT2("subscriptions", new QIcon("rule")).withWidgetName(WIDGET_ENDPOINT_SUBSCRIPTIONS))
         .withSection(SectionFactory.customT2("secrets", new QIcon("key")).withWidgetName(WIDGET_ENDPOINT_SECRETS))
         .withSection(SectionFactory.customT2("deliveries", new QIcon("outbox")).withWidgetName(WIDGET_ENDPOINT_DELIVERIES))
         .withSection(SectionFactory.defaultT3("createDate", "modifyDate"))
         .withExposedJoin(new ExposedJoin().withLabel("Subscriptions").withJoinTable(SUBSCRIPTION).withJoinPath(List.of(JOIN_ENDPOINT_SUBSCRIPTION)));
      return (table);
   }



   /*******************************************************************************
    ** webhooks_endpoint_secret.
    *******************************************************************************/
   static QTableMetaData secret(WebhooksQBitConfig config)
   {
      QTableMetaData table = base(config, SECRET, "Webhook Signing Secret", "key")
         .withRecordLabelFormatAndFields("Secret %s", "fingerprint")
         .withField(new QFieldMetaData("endpointId", QFieldType.INTEGER).withLabel("Endpoint").withIsRequired(true).withPossibleValueSourceName(ENDPOINT))
         .withField(chip(new QFieldMetaData("status", QFieldType.STRING).withIsRequired(true).withPossibleValueSourceName(PVS_SECRET_STATUS)
            .withDefaultValue(SecretStatus.ACTIVE.name()),
            new String[][] {{"ACTIVE", "check_circle", AdornmentType.ChipValues.COLOR_SUCCESS}, {"PREVIOUS", "history", AdornmentType.ChipValues.COLOR_WARNING},
               {"RETIRED", "do_not_disturb_on", AdornmentType.ChipValues.COLOR_DEFAULT}}))
         .withField(new QFieldMetaData("source", QFieldType.STRING).withIsRequired(true).withPossibleValueSourceName(PVS_SECRET_SOURCE))
         .withField(new QFieldMetaData("secretRef", QFieldType.STRING).withLabel("Environment Variable").withMaxLength(100))
         .withField(ro("encryptedSecret", QFieldType.TEXT))
         .withField(ro("fingerprint", QFieldType.STRING).withMaxLength(20))
         .withField(new QFieldMetaData("expiresDate", QFieldType.DATE_TIME).withLabel("Signs Until"))
         .withField(ro("retiredDate", QFieldType.DATE_TIME))
         .withField(new QFieldMetaData("note", QFieldType.STRING).withMaxLength(250));
      table.withSection(SectionFactory.defaultT1("id", "endpointId", "status", "fingerprint"))
         .withSection(SectionFactory.defaultT2("source", "secretRef", "expiresDate", "retiredDate", "note", "encryptedSecret"))
         .withSection(SectionFactory.customT2("value", new QIcon("visibility")).withWidgetName("webhooksSecretValue"))
         .withSection(SectionFactory.defaultT3("createDate", "modifyDate"));
      return (table);
   }



   /*******************************************************************************
    ** webhooks_subscription.
    *******************************************************************************/
   static QTableMetaData subscription(WebhooksQBitConfig config)
   {
      QTableMetaData table = base(config, SUBSCRIPTION, "Webhook Subscription", "rule")
         .withRecordLabelFormatAndFields("%s: %s", "endpointId", "eventTypeName")
         .withField(new QFieldMetaData("endpointId", QFieldType.INTEGER).withLabel("Endpoint").withIsRequired(true).withPossibleValueSourceName(ENDPOINT))
         .withField(new QFieldMetaData("eventTypeName", QFieldType.STRING).withLabel("Event Type").withIsRequired(true).withMaxLength(200)
            .withPossibleValueSourceName(WebhookEventTypePossibleValueSource.NAME))
         .withField(chip(new QFieldMetaData("activeStatus", QFieldType.STRING).withLabel("Status").withIsRequired(true)
            .withPossibleValueSourceName(PVS_ACTIVE_STATUS).withDefaultValue(ActiveStatus.ACTIVE.name()),
            new String[][] {{"ACTIVE", "play_circle", AdornmentType.ChipValues.COLOR_SUCCESS}, {"PAUSED", "pause_circle", AdornmentType.ChipValues.COLOR_WARNING},
               {"DISABLED", "block", AdornmentType.ChipValues.COLOR_ERROR}}))
         .withField(new QFieldMetaData("description", QFieldType.STRING).withMaxLength(250))
         .withField(new QFieldMetaData("filterJson", QFieldType.TEXT).withLabel("Record Filter (JSON)")
            .withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR).withValue(AdornmentType.CodeEditorValues.languageMode("json"))))
         .withField(new QFieldMetaData("detailLevel", QFieldType.STRING).withIsRequired(true).withPossibleValueSourceName(PVS_DETAIL_LEVEL)
            .withDefaultValue(DetailLevel.FULL.name()))
         .withField(new QFieldMetaData("includeFields", QFieldType.STRING).withLabel("Only These Fields").withMaxLength(1000))
         .withField(new QFieldMetaData("excludeFields", QFieldType.STRING).withLabel("Never These Fields").withMaxLength(1000))
         .withField(new QFieldMetaData("apiName", QFieldType.STRING).withLabel("API Name").withMaxLength(100).withDefaultValue(config.getDefaultApiName()))
         .withField(new QFieldMetaData("apiVersion", QFieldType.STRING).withLabel("API Version").withMaxLength(50).withDefaultValue(config.getDefaultApiVersion()));
      List<String> security = addSecurity(config, table);
      table.withSection(t1(security, "id", "endpointId", "eventTypeName", "activeStatus"))
         .withSection(SectionFactory.customT2("filter", new QIcon("filter_alt"), "description", "filterJson"))
         .withSection(SectionFactory.customT2("payload", new QIcon("data_object"), "detailLevel", "includeFields", "excludeFields", "apiName", "apiVersion"))
         .withSection(SectionFactory.customT2("deliveries", new QIcon("outbox")).withWidgetName(WIDGET_SUBSCRIPTION_DELIVERIES))
         .withSection(SectionFactory.defaultT3("createDate", "modifyDate"))
         .withExposedJoin(new ExposedJoin().withLabel("Endpoint").withJoinTable(ENDPOINT).withJoinPath(List.of(JOIN_ENDPOINT_SUBSCRIPTION)));
      return (table);
   }



   /*******************************************************************************
    ** webhooks_delivery.
    *******************************************************************************/
   static QTableMetaData delivery(WebhooksQBitConfig config)
   {
      QTableMetaData table = base(config, DELIVERY, "Webhook Delivery", "outbox")
         .withRecordLabelFormatAndFields("%s (%s)", "eventTypeName", "id")
         .withField(ro("eventId", QFieldType.STRING).withLabel("Event Id (webhook-id)").withMaxLength(40))
         .withField(ro("eventTypeName", QFieldType.STRING).withLabel("Event Type").withMaxLength(200)
            .withPossibleValueSourceName(WebhookEventTypePossibleValueSource.NAME))
         .withField(ro("endpointId", QFieldType.INTEGER).withLabel("Endpoint").withPossibleValueSourceName(ENDPOINT))
         .withField(ro("subscriptionId", QFieldType.INTEGER).withLabel("Subscription").withPossibleValueSourceName(SUBSCRIPTION))
         .withField(ro("source", QFieldType.STRING).withPossibleValueSourceName(PVS_EVENT_SOURCE))
         .withField(ro("sourceTable", QFieldType.STRING).withMaxLength(100))
         .withField(ro("sourceRecordId", QFieldType.STRING).withLabel("Source Record").withMaxLength(100))
         .withField(ro("sourceEventId", QFieldType.STRING).withMaxLength(100))
         .withField(ro("dedupKey", QFieldType.STRING).withMaxLength(250))
         .withField(chip(ro("status", QFieldType.STRING).withPossibleValueSourceName(PVS_DELIVERY_STATUS),
            new String[][] {{"PENDING", "pending", AdornmentType.ChipValues.COLOR_DEFAULT}, {"SENDING", "send", AdornmentType.ChipValues.COLOR_INFO},
               {"RETRY_WAIT", "hourglass_top", AdornmentType.ChipValues.COLOR_WARNING}, {"DELIVERED", "check_circle", AdornmentType.ChipValues.COLOR_SUCCESS},
               {"DEAD_LETTER", "report", AdornmentType.ChipValues.COLOR_ERROR}, {"CANCELLED", "cancel", AdornmentType.ChipValues.COLOR_DEFAULT}}))
         .withField(ro("attemptCount", QFieldType.INTEGER).withLabel("Attempts").withDefaultValue(0))
         .withField(ro("cycleAttemptCount", QFieldType.INTEGER).withLabel("Attempts Since Replay").withDefaultValue(0))
         .withField(ro("replayCount", QFieldType.INTEGER).withLabel("Replays").withDefaultValue(0))
         .withField(ro("occurredDate", QFieldType.DATE_TIME).withLabel("Occurred"))
         .withField(ro("nextAttemptDate", QFieldType.DATE_TIME).withLabel("Next Attempt"))
         .withField(ro("leaseExpiresDate", QFieldType.DATE_TIME).withLabel("Lease Expires"))
         .withField(ro("lastHttpStatus", QFieldType.INTEGER).withLabel("Last HTTP Status"))
         .withField(ro("lastError", QFieldType.STRING).withMaxLength(500))
         .withField(ro("deliveredDate", QFieldType.DATE_TIME).withLabel("Delivered"))
         .withField(ro("deadLetterDate", QFieldType.DATE_TIME).withLabel("Dead-Lettered"))
         .withField(ro("payloadBytes", QFieldType.INTEGER))
         .withField(ro("payload", QFieldType.TEXT)
            .withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR).withValue(AdornmentType.CodeEditorValues.languageMode("json"))));
      List<String> security = addSecurity(config, table);
      table.withUniqueKey(new UniqueKey("dedupKey"))
         .withSection(t1(security, "id", "eventTypeName", "endpointId", "status"))
         .withSection(SectionFactory.customT2("progress", new QIcon("timeline"), "attemptCount", "cycleAttemptCount", "replayCount", "occurredDate",
            "nextAttemptDate", "lastHttpStatus", "lastError", "deliveredDate", "deadLetterDate"))
         .withSection(SectionFactory.customT2("source", new QIcon("input"), "eventId", "source", "sourceTable", "sourceRecordId", "sourceEventId",
            "subscriptionId", "dedupKey", "leaseExpiresDate", "payloadBytes"))
         .withSection(SectionFactory.customT2("attempts", new QIcon("history")).withWidgetName(WIDGET_DELIVERY_ATTEMPTS))
         .withSection(SectionFactory.customT2("payloadSection", new QIcon("data_object"), "payload").withLabel("Payload").withGridColumns(12))
         .withSection(SectionFactory.defaultT3("createDate", "modifyDate"))
         .withExposedJoin(new ExposedJoin().withLabel("Endpoint").withJoinTable(ENDPOINT).withJoinPath(List.of(JOIN_ENDPOINT_DELIVERY)));
      return (table);
   }



   /*******************************************************************************
    ** webhooks_delivery_attempt.
    *******************************************************************************/
   static QTableMetaData attempt(WebhooksQBitConfig config)
   {
      QTableMetaData table = base(config, ATTEMPT, "Webhook Delivery Attempt", "history")
         .withRecordLabelFormatAndFields("Attempt %s of delivery %s", "attemptNumber", "deliveryId")
         .withField(ro("deliveryId", QFieldType.INTEGER).withLabel("Delivery").withPossibleValueSourceName(DELIVERY))
         .withField(ro("endpointId", QFieldType.INTEGER).withLabel("Endpoint").withPossibleValueSourceName(ENDPOINT))
         .withField(ro("attemptNumber", QFieldType.INTEGER))
         .withField(chip(ro("outcome", QFieldType.STRING).withPossibleValueSourceName(PVS_ATTEMPT_OUTCOME),
            new String[][] {{"IN_FLIGHT", "send", AdornmentType.ChipValues.COLOR_INFO}, {"SUCCEEDED", "check_circle", AdornmentType.ChipValues.COLOR_SUCCESS},
               {"FAILED", "error", AdornmentType.ChipValues.COLOR_ERROR}, {"RATE_LIMITED", "speed", AdornmentType.ChipValues.COLOR_WARNING},
               {"BLOCKED", "block", AdornmentType.ChipValues.COLOR_ERROR}, {"LEASE_EXPIRED", "timer_off", AdornmentType.ChipValues.COLOR_WARNING}}))
         .withField(ro("claimedBy", QFieldType.STRING).withMaxLength(100))
         .withField(ro("leaseExpiresDate", QFieldType.DATE_TIME))
         .withField(ro("startDate", QFieldType.DATE_TIME).withLabel("Started"))
         .withField(ro("endDate", QFieldType.DATE_TIME).withLabel("Ended"))
         .withField(ro("durationMillis", QFieldType.INTEGER).withLabel("Duration (ms)"))
         .withField(ro("httpStatus", QFieldType.INTEGER).withLabel("HTTP Status"))
         .withField(ro("responseExcerpt", QFieldType.TEXT))
         .withField(ro("errorMessage", QFieldType.STRING).withMaxLength(1000))
         .withField(ro("remoteAddress", QFieldType.STRING).withMaxLength(64))
         .withField(ro("requestUrl", QFieldType.STRING).withLabel("URL").withMaxLength(500))
         .withField(ro("signedWith", QFieldType.STRING).withLabel("Signed With (Fingerprints)").withMaxLength(100));
      table.withUniqueKey(new UniqueKey("deliveryId", "attemptNumber"))
         .withSection(SectionFactory.defaultT1("id", "deliveryId", "endpointId", "attemptNumber", "outcome"))
         .withSection(SectionFactory.defaultT2("httpStatus", "durationMillis", "startDate", "endDate", "errorMessage", "responseExcerpt",
            "requestUrl", "remoteAddress", "signedWith", "claimedBy", "leaseExpiresDate"))
         .withSection(SectionFactory.defaultT3("createDate", "modifyDate"));
      return (table);
   }



   /*******************************************************************************
    ** Add a chip adornment (value, icon, color triples).
    *******************************************************************************/
   private static QFieldMetaData chip(QFieldMetaData field, String[][] values)
   {
      FieldAdornment adornment = new FieldAdornment(AdornmentType.CHIP);
      for(String[] value : values)
      {
         adornment.withValues(AdornmentType.ChipValues.iconAndColorValues(value[0], value[1], value[2]));
      }
      return (field.withFieldAdornment(adornment));
   }
}
