/**
 * Copyright 2019 LinkedIn Corporation. All rights reserved.
 * Licensed under the BSD-2 Clause license.
 * See LICENSE in the project root for license information.
 */
package com.linkedin.coral.schema.avro;

import com.linkedin.coral.hive.hive2rel.HiveMetastoreClient;
import com.linkedin.coral.hive.hive2rel.parsetree.UnhandledASTTokenException;
import org.apache.avro.Schema;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.ql.metadata.HiveException;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


public class ViewToAvroSchemaConverterTests {
  private HiveMetastoreClient hiveMetastoreClient;

  @BeforeClass
  public void beforeClass() throws HiveException, MetaException {
    hiveMetastoreClient = TestUtils.setup();
  }

  @Test
  public void testBaseTable() {
    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "basecomplex");

    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("base-complex.avsc"));
  }

  @Test
  public void testSelectStar() {
    String viewSql = "CREATE VIEW v AS SELECT * FROM basecomplex";

    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "v");

    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("testSelectStar-expected.avsc"));
  }

  @Test
  public void testFilter() {
    String viewSql = "CREATE VIEW v AS "
        + "SELECT bc.Id AS Id_View_Col, bc.Array_Col AS Array_View_Col "
        + "FROM basecomplex bc "
        + "WHERE bc.Id > 0 AND bc.Struct_Col IS NOT NULL";

    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "v");

    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("testFilter-expected.avsc"));
  }

  @Test
  public void testSelectWithLiterals() {
    String viewSql = "CREATE VIEW v AS "
        + "SELECT bc.Id AS Id_View_Col, 100 AS Additional_Int, 200, bc.Array_Col AS Array_View_Col "
        + "FROM basecomplex bc "
        + "WHERE bc.Id > 0 AND bc.Struct_Col IS NOT NULL";

    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "v");

    // TODO: need to improve default name for literal later
    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("testSelectWithLiterals-expected.avsc"));
  }


  @Test
  public void testAggregate() {
    String viewSql = "CREATE VIEW v AS "
        + "SELECT bc.Id AS Id_View_Col, COUNT(bc.Map_Col), 100 AS Additional_Int, bc.Struct_Col AS Struct_View_Col "
        + "FROM basecomplex bc "
        + "WHERE bc.Id > 0 AND bc.Map_Col IS NOT NULL AND bc.Struct_Col IS NOT NULL "
        + "GROUP BY bc.Id, bc.Struct_Col";

    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "v");

    // TODO: need to improve default name for aggregation later
    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("testAggregate-expected.avsc"));
  }

  @Test
  public void testSubQueryFrom() {
    String viewSql = "CREATE VIEW v AS "
        + "SELECT Id, Map_Col "
        + "FROM "
        + "( "
        + "SELECT Id, Map_Col "
        + "FROM basecomplex "
        + "WHERE Id > 0 AND Struct_Col IS NOT NULL "
        + ") t";
    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "v");

    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("testSubQueryFrom-expected.avsc"));
  }

  @Test
  public void testSelectEnum() {
    String viewSql = "CREATE VIEW v AS "
        + "SELECT bc.Enum_Top_Col "
        + "FROM baseenum bc";
    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "v");

    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("testSelectEnum-expected.avsc"));
  }

  @Test
  public void testSelectSameLiterals() {
    // TODO: implement deduplication of literal names (SELECT 1, 1, 1 etc)
  }

  @Test
  public void testUnion() {
    String viewSql = "CREATE VIEW v AS "
        + "SELECT b1.Id AS Id_View_Col, b1.Struct_Col AS Struct_View_Col "
        + "FROM basecomplex b1 "
        + "UNION ALL "
        + "SELECT b2.Id AS Id_View_Col, b2.Struct_Col AS Struct_View_Col "
        + "FROM basecomplex b2 "
        + "UNION ALL "
        + "SELECT b3.Id AS Id_View_Col, b3.Struct_Col AS Struct_View_Col "
        + "FROM basecomplex b3";

    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "v");

    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("testUnion-expected.avsc"));
  }

  @Test
  public void testUdf() {
    // TODO: implement this test
  }

  @Test
  public void testLateralView() {
    String viewSql = "CREATE VIEW v AS "
        + "SELECT bc.Id AS Id_View_Col, t.Array_Lateral_View_Col "
        + "FROM basecomplex bc "
        + "LATERAL VIEW explode(bc.Array_Col) t as Array_Lateral_View_Col";

    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "v");

    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("testLateralView-expected.avsc"));
  }

  @Test
  public void testLateralViewOuter() {
    String viewSql = "CREATE VIEW v AS "
        + "SELECT bc.Id AS Id_View_Col, t.Array_Lateral_View_Col "
        + "FROM basecomplex bc "
        + "LATERAL VIEW OUTER explode(bc.Array_Col) t as Array_Lateral_View_Col";

    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "v");

    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("testLateralViewOuter-expected.avsc"));
  }

  @Test
  public void testMultipleLateralView() {
    String viewSql = "CREATE VIEW v AS "
        + "SELECT bc.Id AS Id_View_Col, t1.Array_Lateral_View_Col_1, t2.Array_Lateral_View_Col_2 "
        + "FROM basecomplex bc "
        + "LATERAL VIEW explode(bc.Array_Col) t1 as Array_Lateral_View_Col_1 "
        + "LATERAL VIEW explode(bc.Array_Col) t2 as Array_Lateral_View_Col_2";

    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "v");

    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("testMultipleLateralView-expected.avsc"));
  }

  @Test
  public void testMultipleLateralViewDifferentArrayType() {
    String viewSql = "CREATE VIEW v AS "
        + "SELECT bl.Id AS Id_View_Col, t1.Array_Lateral_View_String_Col, t2.Array_Lateral_View_Double_Col "
        + "FROM baselateralview bl "
        + "LATERAL VIEW explode(bl.Array_Col_String) t1 as Array_Lateral_View_String_Col "
        + "LATERAL VIEW explode(bl.Array_Col_Double) t2 as Array_Lateral_View_Double_Col";

    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "v");

    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("testMultipleLateralViewDifferentArrayType-expected.avsc"));
  }

  // TODO: handle complex type (Array[Struct] in lateral view:  LIHADOOP-46695)
  @Test(enabled = false)
  public void testLateralViewArrayWithComplexType() {
    String viewSql = "CREATE VIEW v AS "
        + "SELECT bl.Id AS Id_View_Col, bl.Array_Col_Struct AS Array_Struct_View_Col, "
        + "t.Array_Col_Struct_Flatten "
        + "FROM baselateralview bl "
        + "LATERAL VIEW explode(bl.Array_Col_Struct) t as Array_Col_Struct_Flatten";

    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    Schema actualSchema = viewToAvroSchemaConverter.toAvroSchema("default", "v");

    Assert.assertEquals(actualSchema.toString(true),
        TestUtils.loadSchema("testLateralViewArrayWithComplexType-expected.avsc"));
  }

  // Currently coral-hive does not support lateral view on map type and
  // it throws UnhandledASTTokenException while converting it to RelNode
  @Test(expectedExceptions = UnhandledASTTokenException.class)
  public void testLateralViewMap() {
    String viewSql = "CREATE VIEW v AS "
        + "SELECT bl.Id AS Id_View_Col, t.Col1, t.Col2 "
        + "FROM baselateralview bl "
        + "LATERAL VIEW explode(bl.Map_Col_String) t as Col1, Col2";

    TestUtils.executeCreateViewQuery("default", "v", viewSql);

    ViewToAvroSchemaConverter viewToAvroSchemaConverter = ViewToAvroSchemaConverter.create(hiveMetastoreClient);
    viewToAvroSchemaConverter.toAvroSchema("default", "v");
  }

  @Test
  public void testSubQueryWhere() {
    // TODO: implement this test
  }

  @Test
  public void testJoin() {
    // TODO: implement this test
  }

  // TODO: add more unit tests
}
