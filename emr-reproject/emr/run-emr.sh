# Runs job with auto-terminate.

BUCKET=s3://azavea-datahub

aws emr create-cluster \
  --name "Data Hub ETL" \
  --log-uri $BUCKET/emr/logs/ \
  --release-label emr-4.0.0 \
  --use-default-roles \
  --auto-terminate \
  --ec2-attributes KeyName=azavea-data \
  --applications Name=Spark \
  --instance-groups \
    InstanceCount=1,BidPrice=0.150,InstanceGroupType=MASTER,InstanceType=m3.xlarge \
    InstanceCount=4,BidPrice=0.150,InstanceGroupType=CORE,InstanceType=m3.xlarge \
  --steps Name=RUN-TREES,ActionOnFailure=CONTINUE,Type=Spark,Jar=$BUCKET/emr/jars/com.azavea.datahub.etl-0.1.0.jar,Args=[--deploy-mode,cluster,--class,com.azavea.datahub.etl.nlcd.TreeCanopy,$BUCKET/emr/jars/com.azavea.datahub.etl-0.1.0.jar,--input,s3,--format,geotiff,-I,bucket=com.azavea.datahub,key=raw,splitSize=256,--output,s3,--layer,nlcd-treecanopy-zoomed,--tileSize,256,--crs,EPSG:3857,-O,bucket=azavea-datahub,key=catalog,--cache,NONE] \
  --bootstrap-action Path=$BUCKET/emr/bootstrap.sh \
  --configurations file://./emr.json
