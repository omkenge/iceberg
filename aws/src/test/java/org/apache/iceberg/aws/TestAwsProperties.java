/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iceberg.aws;

import static org.apache.iceberg.aws.AwsProperties.DYNAMODB_TABLE_NAME;
import static org.apache.iceberg.aws.AwsProperties.GLUE_CATALOG_ID;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.apache.iceberg.TestHelpers;
import org.apache.iceberg.relocated.com.google.common.collect.ImmutableMap;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class TestAwsProperties {

  @ParameterizedTest
  @MethodSource("org.apache.iceberg.TestHelpers#serializers")
  public void testSerialization(TestHelpers.RoundTripSerializer<AwsProperties> roundTripSerializer)
      throws IOException, ClassNotFoundException {
    AwsProperties awsPropertiesWithProps =
        new AwsProperties(ImmutableMap.of(GLUE_CATALOG_ID, "foo", DYNAMODB_TABLE_NAME, "ice"));
    AwsProperties deSerializedAwsPropertiesWithProps =
        roundTripSerializer.apply(awsPropertiesWithProps);
    assertThat(deSerializedAwsPropertiesWithProps.glueCatalogId())
        .isEqualTo(awsPropertiesWithProps.glueCatalogId());
    assertThat(deSerializedAwsPropertiesWithProps.dynamoDbTableName())
        .isEqualTo(awsPropertiesWithProps.dynamoDbTableName());
  }
}
