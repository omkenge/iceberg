---
title: "Hive"
---
<!--
 - Licensed to the Apache Software Foundation (ASF) under one or more
 - contributor license agreements.  See the NOTICE file distributed with
 - this work for additional information regarding copyright ownership.
 - The ASF licenses this file to You under the Apache License, Version 2.0
 - (the "License"); you may not use this file except in compliance with
 - the License.  You may obtain a copy of the License at
 -
 -   http://www.apache.org/licenses/LICENSE-2.0
 -
 - Unless required by applicable law or agreed to in writing, software
 - distributed under the License is distributed on an "AS IS" BASIS,
 - WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 - See the License for the specific language governing permissions and
 - limitations under the License.
 -->

# Hive

Iceberg supports reading and writing Iceberg tables through [Hive](https://hive.apache.org) by using
a [StorageHandler](https://cwiki.apache.org/confluence/display/Hive/StorageHandlers).

## Feature support

Hive supports the following features with Hive version 4.0.0 and above:

* Creating an Iceberg table.
* Creating an Iceberg identity-partitioned table.
* Creating an Iceberg table with any partition spec, including the various transforms supported by Iceberg.
* Creating a table from an existing table (CTAS table).
* Dropping a table.
* Altering a table while keeping Iceberg and Hive schemas in sync.
* Altering the partition schema (updating columns).
* Altering the partition schema by specifying partition transforms.
* Truncating a table / partition, dropping a partition.
* Migrating tables in Avro, Parquet, or ORC (Non-ACID) format to Iceberg.
* Reading an Iceberg table.
* Reading the schema of a table.
* Querying Iceberg metadata tables.
* Time travel applications.
* Inserting into a table / partition (INSERT INTO).
* Inserting data overwriting existing data (INSERT OVERWRITE) in a table / partition.
* Copy-on-write support for delete, update and merge queries, CRUD support for Iceberg V1 tables.
* Altering a table with expiring snapshots.
* Create a table like an existing table (CTLT table).
* Support adding parquet compression type via Table properties [Compression types](https://spark.apache.org/docs/2.4.3/sql-data-sources-parquet.html#configuration).
* Altering a table metadata location.
* Supporting table rollback.
* Honors sort orders on existing tables when writing a table [Sort orders specification](../../spec.md#sort-orders).
* Creating, writing to and dropping an Iceberg branch / tag.
* Allowing expire snapshots by Snapshot ID, by time range, by retention of last N snapshots and using table properties.
* Set current snapshot using snapshot ID for an Iceberg table.
* Support for renaming an Iceberg table.
* Altering a table to convert to an Iceberg table.
* Fast forwarding, cherry-picking commit to an Iceberg branch.
* Creating a branch from an Iceberg tag.
* Set current snapshot using branch/tag for an Iceberg table.
* Delete orphan files for an Iceberg table.
* Allow full table compaction of Iceberg tables.
* Support of showing partition information for Iceberg tables (SHOW PARTITIONS).

!!! warning
    DML operations work only with Tez execution engine.


## Enabling Iceberg support in Hive

Starting from 1.8.0 Iceberg doesn't release Hive runtime connector. For Hive query engine integration (specifically
with Hive 2.x and 3.x) use Hive runtime connector coming with Iceberg 1.6.1, or use Hive 4.0.0 or later
which is released with embedded Iceberg integration.

### Hive 4.0.x

Hive 4.0.x comes with Iceberg 1.4.3 included.

#### Enabling support

If the Iceberg storage handler is not in Hive's classpath, then Hive cannot load or update the metadata for an Iceberg
table when the storage handler is set. To avoid the appearance of broken tables in Hive, Iceberg will not add the
storage handler to a table unless Hive support is enabled. The storage handler is kept in sync (added or removed) every
time Hive engine support for the table is updated, i.e. turned on or off in the table properties. There are two ways to
enable Hive support: globally in Hadoop Configuration and per-table using a table property.

##### Hadoop configuration

To enable Hive support globally for an application, set `iceberg.engine.hive.enabled=true` in its Hadoop configuration.
For example, setting this in the `hive-site.xml` loaded by Spark will enable the storage handler for all tables created
by Spark.

##### Table property configuration

Alternatively, the property `engine.hive.enabled` can be set to `true` and added to the table properties when creating
the Iceberg table. Here is an example of doing it programmatically:

```java
Catalog catalog=...;
    Map<String, String> tableProperties=Maps.newHashMap();
    tableProperties.put(TableProperties.ENGINE_HIVE_ENABLED,"true"); // engine.hive.enabled=true
    catalog.createTable(tableId,schema,spec,tableProperties);
```

The table level configuration overrides the global Hadoop configuration.

## Catalog Management

### Global Hive catalog

HiveCatalog integration supports Hive 2.3.10 or 3.1.3 or later.

From the Hive engine's perspective, there is only one global data catalog that is defined in the Hadoop configuration in
the runtime environment. In contrast, Iceberg supports multiple different data catalog types such as Hive, Hadoop, AWS
Glue, or custom catalog implementations. Iceberg also allows loading a table directly based on its path in the file
system. Those tables do not belong to any catalog. Users might want to read these cross-catalog and path-based tables
through the Hive engine for use cases like join.

To support this, a table in the Hive metastore can represent three different ways of loading an Iceberg table, depending
on the table's `iceberg.catalog` property:

1. The table will be loaded using a `HiveCatalog` that corresponds to the metastore configured in the Hive environment
   if no `iceberg.catalog` is set
2. The table will be loaded using a custom catalog if `iceberg.catalog` is set to a catalog name (see below)
3. The table can be loaded directly using the table's root location if `iceberg.catalog` is set
   to `location_based_table`

For cases 2 and 3 above, users can create an overlay of an Iceberg table in the Hive metastore, so that different table
types can work together in the same Hive environment. See [CREATE EXTERNAL TABLE](#create-external-table-overlaying-an-existing-iceberg-table)
and [CREATE TABLE](#create-table) for more details.

### Custom Iceberg catalogs

To globally register different catalogs, set the following Hadoop configurations:

| Config Key                                    | Description                                            |
| --------------------------------------------- | ------------------------------------------------------ |
| iceberg.catalog.<catalog_name\>.type          | type of catalog: `hive`, `hadoop`, or left unset if using a custom catalog  |
| iceberg.catalog.<catalog_name\>.catalog-impl  | catalog implementation, must not be null if type is empty |
| iceberg.catalog.<catalog_name\>.<key\>        | any config key and value pairs for the catalog         |

Here are some examples using Hive CLI:

Register a `HiveCatalog` called `another_hive`:

```
SET iceberg.catalog.another_hive.type=hive;
SET iceberg.catalog.another_hive.uri=thrift://example.com:9083;
SET iceberg.catalog.another_hive.clients=10;
SET iceberg.catalog.another_hive.warehouse=hdfs://example.com:8020/warehouse;
```

Register a `HadoopCatalog` called `hadoop`:

```
SET iceberg.catalog.hadoop.type=hadoop;
SET iceberg.catalog.hadoop.warehouse=hdfs://example.com:8020/warehouse;
```

Register an AWS `GlueCatalog` called `glue`:

```
SET iceberg.catalog.glue.type=glue;
SET iceberg.catalog.glue.warehouse=s3://my-bucket/my/key/prefix;
SET iceberg.catalog.glue.lock.table=myGlueLockTable;
```

## DDL Commands

### CREATE TABLE

#### Non partitioned tables

The Hive `CREATE EXTERNAL TABLE` command creates an Iceberg table when you specify the storage handler as follows:

```sql
CREATE EXTERNAL TABLE x (i int) STORED BY ICEBERG;
```

If you want to create external tables using CREATE TABLE, configure the MetaStoreMetadataTransformer on the cluster,
and `CREATE TABLE` commands are transformed to create external tables. For example:

```sql
CREATE TABLE x (i int) STORED BY ICEBERG;
```

You can specify the default file format (Avro, Parquet, ORC) at the time of the table creation.
The default is Parquet:

```sql
CREATE TABLE x (i int) STORED BY ICEBERG STORED AS ORC;
```

#### Partitioned tables
You can create Iceberg partitioned tables using a command familiar to those who create non-Iceberg tables:

```sql
CREATE TABLE x (i int) PARTITIONED BY (j int) STORED BY ICEBERG;
```

!!! info
    The resulting table does not create partitions in HMS, but instead, converts partition data into Iceberg identity partitions.

Use the DESCRIBE command to get information about the Iceberg identity partitions:

```sql
DESCRIBE x;
```
The result is:

| col_name                           | data_type      | comment
| ---------------------------------- | -------------- | -------
| i                                  | int            |
| j                                  | int            |
|                                    | NULL           | NULL
| # Partition Transform Information  | NULL           | NULL
| # col_name                         | transform_type | NULL
| j                                  | IDENTITY       | NULL

You can create Iceberg partitions using the following Iceberg partition specification syntax
(supported only from Hive 4.0.0):

```sql
CREATE TABLE x (i int, ts timestamp) PARTITIONED BY SPEC (month(ts), bucket(2, i)) STORED BY ICEBERG;
DESCRIBE x;
```
The result is:

| col_name                           | data_type      | comment
| ---------------------------------- | -------------- | -------
| i                                  | int            |
| ts                                 | timestamp      |
|                                    | NULL           | NULL
| # Partition Transform Information  | NULL           | NULL
| # col_name                         | transform_type | NULL
| ts                                 | MONTH          | NULL
| i                                  | BUCKET\[2\]    | NULL

The supported transformations for Hive are the same as for Spark:

* years(ts): partition by year
* months(ts): partition by month
* days(ts) or date(ts): equivalent to dateint partitioning
* hours(ts) or date_hour(ts): equivalent to dateint and hour partitioning
* bucket(N, col): partition by hashed value mod N buckets
* truncate(L, col): partition by value truncated to L
     - Strings are truncated to the given length
     - Integers and longs truncate to bins: truncate(10, i) produces partitions 0, 10, 20, 30,

!!! info
    The resulting table does not create partitions in HMS, but instead, converts partition data into Iceberg partitions.


### CREATE TABLE AS SELECT

`CREATE TABLE AS SELECT` operation resembles the native Hive operation with a single important difference.
The Iceberg table and the corresponding Hive table are created at the beginning of the query execution.
The data is inserted / committed when the query finishes. So for a transient period the table already exists but contains no data.

```sql
CREATE TABLE target PARTITIONED BY SPEC (year(year_field), identity_field) STORED BY ICEBERG AS
    SELECT * FROM source;
```

### CREATE TABLE LIKE TABLE

```sql
CREATE TABLE target LIKE source STORED BY ICEBERG;
```
 
### CREATE EXTERNAL TABLE overlaying an existing Iceberg table

The `CREATE EXTERNAL TABLE` command is used to overlay a Hive table "on top of" an existing Iceberg table. Iceberg
tables are created using either a [`Catalog`](../../javadoc/{{ icebergVersion }}/org/apache/iceberg/catalog/Catalog.html), or an implementation of the [`Tables`](../../javadoc/{{ icebergVersion }}/org/apache/iceberg/Tables.html) interface, and Hive needs to be configured accordingly to operate on these different types of table.

#### Hive catalog tables

As described before, tables created by the `HiveCatalog` with Hive engine feature enabled are directly visible by the
Hive engine, so there is no need to create an overlay.

#### Custom catalog tables

For a table in a registered catalog, specify the catalog name in the statement using table property `iceberg.catalog`.
For example, the SQL below creates an overlay for a table in a `hadoop` type catalog named `hadoop_cat`:

```sql
SET
iceberg.catalog.hadoop_cat.type=hadoop;
SET
iceberg.catalog.hadoop_cat.warehouse=hdfs://example.com:8020/hadoop_cat;

CREATE
EXTERNAL TABLE database_a.table_a
STORED BY 'org.apache.iceberg.mr.hive.HiveIcebergStorageHandler'
TBLPROPERTIES ('iceberg.catalog'='hadoop_cat');
```

When `iceberg.catalog` is missing from both table properties and the global Hadoop configuration, `HiveCatalog` will be
used as default.

#### Path-based Hadoop tables

Iceberg tables created using `HadoopTables` are stored entirely in a directory in a filesystem like HDFS. These tables
are considered to have no catalog. To indicate that, set `iceberg.catalog` property to `location_based_table`. For
example:

```sql
CREATE
EXTERNAL TABLE table_a 
STORED BY 'org.apache.iceberg.mr.hive.HiveIcebergStorageHandler' 
LOCATION 'hdfs://some_bucket/some_path/table_a'
TBLPROPERTIES ('iceberg.catalog'='location_based_table');
```

#### CREATE TABLE overlaying an existing Iceberg table

You can also create a new table that is managed by a custom catalog. For example, the following code creates a table in
a custom Hadoop catalog:

```sql
SET
iceberg.catalog.hadoop_cat.type=hadoop;
SET
iceberg.catalog.hadoop_cat.warehouse=hdfs://example.com:8020/hadoop_cat;

CREATE TABLE database_a.table_a
(
    id   bigint,
    name string
) PARTITIONED BY (
  dept string
) STORED BY 'org.apache.iceberg.mr.hive.HiveIcebergStorageHandler'
TBLPROPERTIES ('iceberg.catalog'='hadoop_cat');
```

!!! danger
    If the table to create already exists in the custom catalog, this will create a managed overlay
    table. This means technically you can omit the `EXTERNAL` keyword when creating an overlay table. However, this is **not
    recommended** because creating managed overlay tables could pose a risk to the shared data files in case of accidental
    drop table commands from the Hive side, which would unintentionally remove all the data in the table.

### ALTER TABLE
#### Table properties
For HiveCatalog tables the Iceberg table properties and the Hive table properties stored in HMS are kept in sync.
    
!!! info
    IMPORTANT: This feature is not available for other Catalog implementations.

```sql
ALTER TABLE t SET TBLPROPERTIES('...'='...');
```

#### Schema evolution
The Hive table schema is kept in sync with the Iceberg table. If an outside source (Impala/Spark/Java API/etc)
changes the schema, the Hive table immediately reflects the changes. You alter the table schema using Hive commands:

* Rename a table
```sql
ALTER TABLE orders RENAME TO renamed_orders;
```

* Add a column
```sql
ALTER TABLE orders ADD COLUMNS (nickname string);
```
* Rename a column
```sql
ALTER TABLE orders CHANGE COLUMN item fruit string;
```
* Reorder columns
```sql
ALTER TABLE orders CHANGE COLUMN quantity quantity int AFTER price;
```
* Change a column type - only if the Iceberg defined the column type change as safe
```sql
ALTER TABLE orders CHANGE COLUMN price price long;
```
* Drop column by using REPLACE COLUMN to remove the old column
```sql
ALTER TABLE orders REPLACE COLUMNS (remaining string);
```
!!! info
    Note, that dropping columns is only thing REPLACE COLUMNS can be used for
    i.e. if columns are specified out-of-order an error will be thrown signalling this limitation.


#### Partition evolution
You change the partitioning schema using the following commands:

* Change the partitioning schema to new identity partitions:
```sql
ALTER TABLE default.customers SET PARTITION SPEC (last_name);
```
* Alternatively, provide a partition specification:
```sql
ALTER TABLE order SET PARTITION SPEC (month(ts));
```

#### Table migration
You can migrate Avro / Parquet / ORC external tables to Iceberg tables using the following command:
```sql
ALTER TABLE t SET TBLPROPERTIES ('storage_handler'='org.apache.iceberg.mr.hive.HiveIcebergStorageHandler');
```
During the migration the data files are not changed, only the appropriate Iceberg metadata files are created.
After the migration, handle the table as a normal Iceberg table.

#### Drop partitions
You can drop partitions based on a single / multiple partition specification using the following commands:
```sql
ALTER TABLE orders DROP PARTITION (buy_date == '2023-01-01', market_price > 1000), PARTITION (buy_date == '2024-01-01', market_price <= 2000);
```
The partition specification supports only identity-partition columns. Transform columns in partition specification are not supported.

#### Branches and tags

`ALTER TABLE ... CREATE BRANCH`

Branches can be created via the CREATE BRANCH statement with the following options:

* Create a branch using default properties.
* Create a branch at a specific snapshot ID.
* Create a branch using system time.
* Create a branch with a specified number of snapshot retentions.
* Create a branch using specific tag.

```sql
-- CREATE branch1 with default properties.
ALTER TABLE test CREATE BRANCH branch1;

-- CREATE branch1 at a specific snapshot ID.
ALTER TABLE test CREATE BRANCH branch1 FOR SYSTEM_VERSION AS OF 3369973735913135680;

-- CREATE branch1 using system time.
ALTER TABLE test CREATE BRANCH branch1 FOR SYSTEM_TIME AS OF '2023-09-16 09:46:38.939 Etc/UTC';

-- CREATE branch1 with a specified number of snapshot retentions.
ALTER TABLE test CREATE BRANCH branch1 FOR SYSTEM_VERSION AS OF 3369973735913135680 WITH SNAPSHOT RETENTION 5 SNAPSHOTS;

-- CREATE branch1 using a specific tag.
ALTER TABLE test CREATE BRANCH branch1 FOR TAG AS OF tag1;
```

`ALTER TABLE ... CREATE TAG`

Tags can be created via the CREATE TAG statement with the following options:

* Create a tag using default properties.
* Create a tag at a specific snapshot ID.
* Create a tag using system time.

```sql
-- CREATE tag1 with default properties.
ALTER TABLE test CREATE TAG tag1;

-- CREATE tag1 at a specific snapshot ID.
ALTER TABLE test CREATE TAG tag1 FOR SYSTEM_VERSION AS OF 3369973735913135680;

-- CREATE tag1 using system time.
ALTER TABLE test CREATE TAG tag1 FOR SYSTEM_TIME AS OF '2023-09-16 09:46:38.939 Etc/UTC';
```

`ALTER TABLE ... DROP BRANCH`

Branches can be dropped via the DROP BRANCH statement with the following options:

* Do not fail if the branch does not exist with IF EXISTS

```sql
-- DROP branch1
ALTER TABLE test DROP BRANCH branch1;

-- DROP branch1 IF EXISTS
ALTER TABLE test DROP BRANCH IF EXISTS branch1;
```

`ALTER TABLE ... DROP TAG`

Tags can be dropped via the DROP TAG statement with the following options:

* Do not fail if the tag does not exist with IF EXISTS

```sql
-- DROP tag1
ALTER TABLE test DROP TAG tag1;

-- DROP tag1 IF EXISTS
ALTER TABLE test DROP TAG IF EXISTS tag1;
```

`ALTER TABLE ... EXECUTE FAST-FORWARD`

An iceberg branch which is an ancestor of another branch can be fast-forwarded to the state of the other branch.

```sql
-- This fast-forwards the branch1 to the state of main branch of the Iceberg table.
ALTER table test EXECUTE FAST-FORWARD 'branch1' 'main';

-- This fast-forwards the branch1 to the state of branch2.
ALTER table test EXECUTE FAST-FORWARD 'branch1' 'branch2';
```

#### `ALTER TABLE ... EXECUTE CHERRY-PICK`

Cherry-pick of a snapshot requires the ID of the snapshot. Cherry-pick of snapshots as of now is supported only on the main branch of an Iceberg table.

```sql
 ALTER table test EXECUTE CHERRY-PICK 8602659039622823857;
```

### TRUNCATE TABLE
The following command truncates the Iceberg table:
```sql
TRUNCATE TABLE t;
```

#### TRUNCATE TABLE ... PARTITION
The following command truncates the partition in an Iceberg table:
```sql
TRUNCATE TABLE orders PARTITION (customer_id = 1, first_name = 'John');
```
The partition specification supports only identity-partition columns. Transform columns in partition specification are not supported.

### DROP TABLE

Tables can be dropped using the `DROP TABLE` command:

```sql
DROP TABLE [IF EXISTS] table_name [PURGE];
```

### METADATA LOCATION

The metadata location (snapshot location) only can be changed if the new path contains the exact same metadata json. 
It can be done only after migrating the table to Iceberg, the two operation cannot be done in one step. 

```sql
ALTER TABLE t set TBLPROPERTIES ('metadata_location'='<path>/hivemetadata/00003-a1ada2b8-fc86-4b5b-8c91-400b6b46d0f2.metadata.json');
```

## DML Commands

### SELECT
Select statements work the same on Iceberg tables in Hive. You will see the Iceberg benefits over Hive in compilation and execution:

* **No file system listings** - especially important on blob stores, like S3
* **No partition listing from** the Metastore
* **Advanced partition filtering** - the partition keys are not needed in the queries when they could be calculated
* Could handle **higher number of partitions** than normal Hive tables

Here are the features highlights for Iceberg Hive read support:

1. **Predicate pushdown**: Pushdown of the Hive SQL `WHERE` clause has been implemented so that these filters are used at the Iceberg `TableScan` level as well as by the Parquet and ORC Readers.
2. **Column projection**: Columns from the Hive SQL `SELECT` clause are projected down to the Iceberg readers to reduce the number of columns read.
3. **Hive query engines**: With Hive 4.x, the Tez query execution engine is supported.

Some of the advanced / little used optimizations are not yet implemented for Iceberg tables, so you should check your individual queries.
Also currently the statistics stored in the MetaStore are used for query planning. This is something we are planning to improve in the future.

Hive 4 supports select operations on branches which also work similar to the table level select operations. However, the branch must be provided as follows - 
```sql
-- Branches should be specified as <database_name>.<table_name>.branch_<branch_name>
SELECT * FROM default.test.branch_branch1;
```

### INSERT INTO

Hive supports the standard single-table INSERT INTO operation:

```sql
INSERT INTO table_a
VALUES ('a', 1);
INSERT INTO table_a
SELECT...;
```

Multi-table insert is also supported, but it will not be atomic. Commits occur one table at a time.
Partial changes will be visible during the commit process and failures can leave partial changes committed.
Changes within a single table will remain atomic.

Insert-into operations on branches also work similar to the table level select operations. However, the branch must be provided as follows -
```sql
-- Branches should be specified as <database_name>.<table_name>.branch_<branch_name>
INSERT INTO default.test.branch_branch1
VALUES ('a', 1);
INSERT INTO default.test.branch_branch1
SELECT...;
```

Here is an example of inserting into multiple tables at once in Hive SQL:

```sql
FROM customers
   INSERT INTO target1 SELECT customer_id, first_name
   INSERT INTO target2 SELECT last_name, customer_id;
```

#### INSERT INTO ... PARTITION

Hive 4 supports partition-level INSERT INTO operation:

```sql
INSERT INTO table_a PARTITION (customer_id = 1, first_name = 'John')
VALUES (1,2);
INSERT INTO table_a PARTITION (customer_id = 1, first_name = 'John')
SELECT...;
```
The partition specification supports only identity-partition columns. Transform columns in partition specification are not supported.

### INSERT OVERWRITE
INSERT OVERWRITE can replace data in the table with the result of a query. Overwrites are atomic operations for Iceberg tables.
For nonpartitioned tables the content of the table is always removed. For partitioned tables the partitions
that have rows produced by the SELECT query will be replaced.
```sql
INSERT OVERWRITE TABLE target SELECT * FROM source;
```

#### INSERT OVERWRITE ... PARTITION

Hive 4 supports partition-level INSERT OVERWRITE operation:

```sql
INSERT OVERWRITE TABLE target PARTITION (customer_id = 1, first_name = 'John') SELECT * FROM source;
```
The partition specification supports only identity-partition columns. Transform columns in partition specification are not supported.

### DELETE FROM

Hive 4 supports DELETE FROM queries to remove data from tables.

Delete queries accept a filter to match rows to delete.

```sql
DELETE FROM target WHERE id > 1 AND id < 10;

DELETE FROM target WHERE id IN (SELECT id FROM source);

DELETE FROM target WHERE id IN (SELECT min(customer_id) FROM source);
```
If the delete filter matches entire partitions of the table, Iceberg will perform a metadata-only delete. If the filter matches individual rows of a table, then Iceberg will rewrite only the affected data files.

### UPDATE

Hive 4 supports UPDATE queries which accept a filter to match rows to update.

```sql
UPDATE target SET first_name = 'Raj' WHERE id > 1 AND id < 10;

UPDATE target SET first_name = 'Raj' WHERE id IN (SELECT id FROM source);

UPDATE target SET first_name = 'Raj' WHERE id IN (SELECT min(customer_id) FROM source);
```
For more complex row-level updates based on incoming data, see the section on MERGE INTO.

### MERGE INTO

Hive 4 added support for MERGE INTO queries that can express row-level updates.

MERGE INTO updates a table, called the target table, using a set of updates from another query, called the source. The update for a row in the target table is found using the ON clause that is like a join condition.

```sql
MERGE INTO target AS t        -- a target table
USING source s                -- the source updates
ON t.id = s.id                -- condition to find updates for target rows
WHEN ...                      -- updates
```

Updates to rows in the target table are listed using WHEN MATCHED ... THEN .... Multiple MATCHED clauses can be added with conditions that determine when each match should be applied. The first matching expression is used.
```sql
WHEN MATCHED AND s.op = 'delete' THEN DELETE
WHEN MATCHED AND t.count IS NULL AND s.op = 'increment' THEN UPDATE SET t.count = 0
WHEN MATCHED AND s.op = 'increment' THEN UPDATE SET t.count = t.count + 1
```

Source rows (updates) that do not match can be inserted:
```sql
WHEN NOT MATCHED THEN INSERT VALUES (s.a, s.b, s.c)
```
Only one record in the source data can update any given row of the target table, or else an error will be thrown.

### QUERYING METADATA TABLES
Hive supports querying of the Iceberg Metadata tables. The tables could be used as normal
Hive tables, so it is possible to use projections / joins / filters / etc.
To reference a metadata table the full name of the table should be used, like:
<DB_NAME>.<TABLE_NAME>.<METADATA_TABLE_NAME>.

Currently the following metadata tables are available in Hive:

* all_data_files
* all_delete_files
* all_entries
* all_files
* all_manifests
* data_files
* delete_files
* entries
* files
* history
* manifests
* metadata_log_entries
* partitions
* refs
* snapshots

```sql
SELECT * FROM default.table_a.files;
```

### TIMETRAVEL
Hive supports snapshot id based and time base timetravel queries.
For these views it is possible to use projections / joins / filters / etc.
The function is available with the following syntax:
```sql
SELECT * FROM table_a FOR SYSTEM_TIME AS OF '2021-08-09 10:35:57';
SELECT * FROM table_a FOR SYSTEM_VERSION AS OF 1234567;
```

You can expire snapshots of an Iceberg table using an ALTER TABLE query from Hive. You should periodically expire snapshots to delete data files that is no longer needed, and reduce the size of table metadata.

Each write to an Iceberg table from Hive creates a new snapshot, or version, of a table. Snapshots can be used for time-travel queries, or the table can be rolled back to any valid snapshot. Snapshots accumulate until they are expired by the expire_snapshots operation.
Enter a query to expire snapshots having the following timestamp: `2021-12-09 05:39:18.689000000`
```sql
ALTER TABLE test_table EXECUTE expire_snapshots('2021-12-09 05:39:18.689000000');
```

### `DELETE ORPHAN-FILES`

Used to remove files which are not referenced in any metadata files of an Iceberg table and can thus be considered "orphaned".
The function is available with the following syntax:
```sql
ALTER TABLE table_a EXECUTE DELETE ORPHAN-FILES;
ALTER TABLE table_a EXECUTE DELETE ORPHAN-FILES OLDER THAN ('2021-12-09 05:39:18.689000000');
```

### Type compatibility

Hive and Iceberg support different set of types. Iceberg can perform type conversion automatically, but not for all
combinations, so you may want to understand the type conversion in Iceberg in prior to design the types of columns in
your tables. You can enable auto-conversion through Hadoop configuration (not enabled by default):

| Config key                               | Default                     | Description                                         |
| -----------------------------------------| --------------------------- | --------------------------------------------------- |
| iceberg.mr.schema.auto.conversion        | false                       | if Hive should perform type auto-conversion         |

### Hive type to Iceberg type

This type conversion table describes how Hive types are converted to the Iceberg types. The conversion applies on both
creating Iceberg table and writing to Iceberg table via Hive.

| Hive             | Iceberg                 | Notes |
|------------------|-------------------------|-------|
| boolean          | boolean                 |       |
| short            | integer                 | auto-conversion |
| byte             | integer                 | auto-conversion |
| integer          | integer                 |       |
| long             | long                    |       |
| float            | float                   |       |
| double           | double                  |       |
| date             | date                    |       |
| timestamp        | timestamp without timezone |    |
| timestamplocaltz | timestamp with timezone | Hive 3 only |
| interval_year_month |                      | not supported |
| interval_day_time |                        | not supported |
| char             | string                  | auto-conversion |
| varchar          | string                  | auto-conversion |
| string           | string                  |       |
| binary           | binary                  |       |
| decimal          | decimal                 |       |
| struct           | struct                  |       |
| list             | list                    |       |
| map              | map                     |       |
| union            |                         | not supported |

### Table rollback

Rolling back iceberg table's data to the state at an older table snapshot.

Rollback to the last snapshot before a specific timestamp

```sql
ALTER TABLE ice_t EXECUTE ROLLBACK('2022-05-12 00:00:00')
```

Rollback to a specific snapshot ID
```sql
ALTER TABLE ice_t EXECUTE ROLLBACK(1111);
```

### Compaction

Hive 4 supports full table compaction of Iceberg tables using the following commands:
```sql
-- Using the ALTER TABLE ... COMPACT syntax
ALTER TABLE t COMPACT 'major';

-- Using the OPTIMIZE TABLE ... REWRITE DATA syntax
OPTIMIZE TABLE t REWRITE DATA;
```
Both these syntax have the same effect of performing full table compaction on an Iceberg table.
