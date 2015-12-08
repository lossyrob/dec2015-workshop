#!/bin/bash

docker run --rm -it \
    -v `pwd`/:/root/workshops \
    -v `pwd`/.ivy2/:/root/.ivy2 \
    -v `pwd`/.sbt/:/root/.sbt \
    java:7 \
    bash
