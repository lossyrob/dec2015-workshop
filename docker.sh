#!/bin/bash

# docker-machine-nfs dev --shared-folder=/Users/rob/proj/gt/geotrellis --shared-folder=/Users/rob/proj/workshops/ksat-workshop

docker run --rm -it -v /Users/rob/proj/gt/geotrellis:/root/geotrellis \
    -v /Users/rob/proj/workshops/ksat-workshop/:/root/workshops \
    -v /Users/rob/proj/workshops/.ivy2/:/root/.ivy2 \
    -v /Users/rob/proj/workshops/.sbt/:/root/.sbt \
    java:7 \
    bash

