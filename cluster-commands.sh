#!/bin/sh

spark-submit \
--class sampleapp.FindMinMaxTime \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
sampleapp-assembly-0.1.0.jar

spark-submit \
--class sampleapp.Ingest \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
sampleapp-assembly-0.1.0.jar \
s3 \
10

spark-submit \
--class sampleapp.Ingest \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
sampleapp-assembly-0.1.0.jar \
accumulo \
10

spark-submit \
--class sampleapp.TimeSeriesExample \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
sampleapp-assembly-0.1.0.jar \
s3

spark-submit \
--class sampleapp.TimeSeriesExample \
--master mesos://zk://zookeeper.service.ksat-demo.internal:2181/mesos \
--conf spark.executorEnv.SPARK_LOCAL_DIRS="/media/ephemeral0,/media/ephemeral1" \
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
--executor-memory 4g \
--num-executors 1 \
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
