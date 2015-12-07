#!/bin/sh

spark-submit \
--class demo.FindMinMaxTime \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
demo-assembly-0.1.0.jar

## Ingest S3
spark-submit \
--class demo.Ingest \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
demo-assembly-0.1.0.jar \
s3 \
30

## Ingest Accumulo
spark-submit \
--class demo.Ingest \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
--conf spark.storage.memoryFraction=0.3 \
--conf spark.default.parallelism=50 \
demo-assembly-0.1.0.jar \
accumulo \
30

## ServerExample S3
spark-submit \
--class demo.ServerExample \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
--conf spark.storage.memoryFraction=0.3 \
--conf spark.default.parallelism=50 \
demo-assembly-0.1.0.jar \
s3

## ServerExample Accumulo
spark-submit \
--class demo.ServerExample \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
--conf spark.driver.cores=4 \
--conf spark.driver.memory=4g \
--conf spark.executor.memory=12g \
--conf spark.mesos.coarse=true \
demo-assembly-0.1.0.jar \
accumulo

spark-shell \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--jars demo-assembly-0.1.0.jar

## local

spark-submit \
--class demo.FindMinMaxTime \
target/scala-2.10/demo-assembly-0.1.0.jar \
file://`pwd`/demo/data/rainfall-wm

spark-submit \
--class demo.Ingest \
target/scala-2.10/demo-assembly-0.1.0.jar \
local \
file://`pwd`/demo/data/rainfall-wm \
file://`pwd`/demo/data/catalog

spark-submit \
--class demo.ServerExample \
target/scala-2.10/demo-assembly-0.1.0.jar \
local \
file://`pwd`/demo/data/catalog



## Running reproject locally

spark-submit \
../code/reproject_to_s3.py \
file://`pwd`/demo/data/rainfall \
file://`pwd`/demo/data/rainfall-wm \
--extension tif


## Accumulo shell

config -t tiles -s table.split.threshold=100M
compact -t tiles

## Polygon for testing (Africa)

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
