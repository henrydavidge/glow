# Copyright 2019 The Glow Authors
# 
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
#     http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


from pyspark.sql import SparkSession
import pytest

# Set up a new Spark session for each test suite
def _spark_session():
    return SparkSession.builder \
        .master("local[2]") \
        .config("spark.hadoop.io.compression.codecs", "io.projectglow.sql.util.BGZFCodec") \
        .config("spark.ui.enabled", "false") \
        .getOrCreate()

@pytest.fixture(scope="module")
def spark():
    print("set up new spark session")
    sess = _spark_session()
    return sess.newSession()

def pytest_runtest_setup(item):
    min_spark_version = next((mark.args[0] for mark in item.iter_markers(name='min_spark')), None)
    if min_spark_version:
        min_version_components = min_spark_version.split('_')
        version = _spark_session().version
        version_components = version.split('.')
        for actual, required in zip(version_components, min_version_components):
            if int(actual) < int(required):
                pytest.skip(f'cannot run on spark {version}')