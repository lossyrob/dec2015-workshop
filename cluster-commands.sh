#!/bin/sh

spark-submit \
--class sampleapp.FindMinMaxTime \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
sampleapp-assembly-0.1.0.jar

## Ingest S3
spark-submit \
--class sampleapp.Ingest \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
sampleapp-assembly-0.1.0.jar \
s3 \
30

## Ingest Accumulo
spark-submit \
--class sampleapp.Ingest \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
--conf spark.storage.memoryFraction=0.3 \
--conf spark.default.parallelism=50 \
sampleapp-assembly-0.1.0.jar \
accumulo \
30

## TimeSeriesExample S3
spark-submit \
--class sampleapp.TimeSeriesExample \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
--conf spark.storage.memoryFraction=0.3 \
--conf spark.default.parallelism=50 \
sampleapp-assembly-0.1.0.jar \
s3

## TimeSeriesExample Accumulo
spark-submit \
--class sampleapp.TimeSeriesExample \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
sampleapp-assembly-0.1.0.jar \
accumulo

spark-shell \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--jars sampleapp-assembly-0.1.0.jar

## local

spark-submit \
--class sampleapp.FindMinMaxTime \
target/scala-2.10/sampleapp-assembly-0.1.0.jar \
file:///Users/rob/proj/workshops/ksat-workshop/sample-app/data/rainfall-wm

spark-submit \
--class sampleapp.Ingest \
target/scala-2.10/sampleapp-assembly-0.1.0.jar \
local \
file:///Users/rob/proj/workshops/ksat-workshop/sample-app/data/rainfall-wm \
file:///Users/rob/proj/workshops/ksat-workshop/sample-app/data/catalog

spark-submit \
--class sampleapp.TimeSeriesExample \
target/scala-2.10/sampleapp-assembly-0.1.0.jar \
local \
file:///Users/rob/proj/workshops/ksat-workshop/sample-app/data/catalog





spark-submit \
../code/reproject_to_s3.py \
file:///Users/rob/proj/workshops/ksat-workshop/sample-app/data/rainfall \
file:///Users/rob/proj/workshops/ksat-workshop/sample-app/data/rainfall-wm \
--extension tif


## Accumulo shell

config -t tiles -s table.split.threshold=100M
compact -t tiles

{
    "type": "Polygon",
    "coordinates": [
        [
            [
                -20.214843749999996,
                -37.30027528134431
                ],
            [
                -20.214843749999996,
                37.16031654673677
                ],
            [
                53.96484375,
                37.16031654673677
                ],
            [
                53.96484375,
                -37.30027528134431
                ],
            [
                -20.214843749999996,
                -37.30027528134431
            ]
        ]
    ]
}
