# spark-submit \
#     --deploy-mode cluster \
#     --num-executors 8 \
#     --class NlcdRender \
#     --conf spark.executor.memory=10G \
#     s3://geotrellis-test/jars/geotrellis-etl-assembly-render.jar \
#     --input s3 \
#     --format geotiff \
#     -I bucket=com.azavea.datahub key=raw splitSize=256 \
#     --output s3 \
#     --layer nlcd \
#     --tileSize 256 \
#     --crs EPSG:3857 \
#     -O bucket=com.azavea.datahub key=catalog \
#     --cache NONE

spark-submit \
    --class com.azavea.datahub.etl.Ingest \
    --conf spark.executor.memory=5G \
    target/scala-2.10/ingest-assembly-0.1.0.jar \
    --input hadoop \
    --format geotiff \
    -I path=file:///Users/rob/proj/adh/data/tiled/population-density-clipped \
    --output hadoop \
    -O path=file:///Users/rob/proj/adh/data/test/population-density-test \
    --layer pop-density \
    --tileSize 1024 \
    --crs EPSG:3857 \
    --layoutScheme floating \
    --cache NONE
