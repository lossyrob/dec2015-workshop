export CLUSTER_ID=<YOUR_CLUSTER_ID>
export ON_FAILURE=CANCEL_AND_WAIT

export JAR=s3://azavea-datahub/emr/jars/com.azavea.datahub.etl-0.1.0.jar
export REPROJECT_PY=s3://azavea-datahub/emr/python/reproject.py

export CATALOG_BUCKET=azavea-datahub
export CATALOG_KEY=catalog