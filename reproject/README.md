# Raster Pipeline

This project can be used to pipe data into a GeoTrellis backend.

### ETL (Extract, Transform, and Load)

To ingest raster data, follow these fun and easy steps.

#### Starting place

Do you have a giant GeoTiff somewhere not on S3? Great.
Already have tiles? Skip to step 3

#### Step 1: Chunk it up.

It's not fun to work with GeoTiffs that are GB's big. Let's break it up! We'll create smaller "tiles" to allow parallel processing in Step 3. These tiles are not related to the final output tiles created in that step.

```console
> gdal_retile.py -of GTiff -co compress=deflate -ps 1024 1024 -targetDir ../tiles/the-big-file/ the-big-file.tif
```

After potentially a very long time, this will produce a directory of compressed GeoTiff tiles that is the data we'll be working with.

#### Step 2: Upload it to S3

We need to get these files on S3 for processing. Use the `awscli` to do this:

```console
aws s3 cp --recursive ../tiles/the-big-file/ s3://raster-store/raw/type/the-big-file/
```

Make sure to place it somewhere usefully identifiable, with a good self-describing name. You could also include a README in the tile directory
that describes that dataset to some later, more confused version of yourself.

#### Step 3: Create an ingest step (Optional)

##### Step 3a: Reproject

Reprojecting tiled geotiffs is done as a pyspark job using [rasterio](). As an example, this is what the `spark-submit` arguments look like when doing reprojections:

```
spark-submit --deploy-mode cluster s3://raster-store/emr/python/reproject.py \
             s3://raster-store/raw/soil/ssurgo-soil-groups/*.tif \
             hdfs:/reprojected/ssurgo-soil-groups-10m \
             --dst-crs=EPSG:5070 \
             --no-data-value=0 \
             --partitions=240
```

Required arguments include the path to the raw dataset on s3, usually some form of `s3://raster-store/raw/<etc>` and the destination (e.g. `hdfs:/reprojected/<etc>`). You can also choose to place these files on s3 if you wish to work with the raw data multiple times or persist them for debugging purposes. The format this script saves the data in is a Hadoop [SequenceFile](https://hadoop.apache.org/docs/stable/api/org/apache/hadoop/io/SequenceFile.html) with a key representing the original filename (e.g. `hydro_soils_conus_10m_002_039.tif`) and the value is a `bytearray` representing a GeoTiff.

One thing to keep in mind when running the reproject step is if it is necessary to set a `nodata` on the reprojected geotiffs (e.g. `--no-data-value=0`). This will be necessary if there _is_ a value that represents `nodata` but has not been properly set in the original geotiffs, otherwise GeoTrellis will treat that value as `data` and the resulting tiles may include random `nodata` lines.

An option is available to specity the destination CRS with a valid EPSG code (`--dst-crs=EPSG:5070`), the number of partitions to split the sequence file into (`--partitions=240`) which should be some multiple of the number of cores in the cluster for maximum efficiency. Additionally, an option is available to choose a sampling method (`--sampling-method=nearest`).

Once this step is finished you can proceed to the next step of running the ingest.

#### How to connect to UI's in Elastic Map Reduce (EMR)

Follow these instructions: Namely set up the ssh tunnel using: http://docs.aws.amazon.com/ElasticMapReduce/latest/DeveloperGuide/emr-ssh-tunnel.html

```
ssh -i ~/mykeypair.pem -N -D 8157 hadoop@ec2-###-##-##-###.compute-1.amazonaws.com
```

Use foxyproxy to set up proxy forwarding; instructions are found here: http://docs.aws.amazon.com/ElasticMapReduce/latest/DeveloperGuide/emr-connect-master-node-proxy.html
However, use the following foxyproxy settings instead of the one listed in that doc (this enables getting to the Spark UI)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<foxyproxy>
   <proxies>
      <proxy name="emr-socks-proxy" id="56835462" notes="" enabled="true" color="#0055E5" mode="manual" autoconfMode="pac" lastresort="false">
         <manualconf host="localhost" port="8157" socksversion="5" isSocks="true" />
         <autoconf url="" reloadPac="false" loadNotification="true" errorNotification="true" autoReload="false" reloadFreqMins="60" disableOnBadPAC="true" />
         <matches>
            <match enabled="true" name="*ec2*.amazonaws.com*" isRegex="false" pattern="*ec2*.amazonaws.com*" reload="true" autoReload="false" isBlackList="false" isMultiLine="false" fromSubscription="false" caseSensitive="false" />
            <match enabled="true" name="*ec2*.compute*" isRegex="false" pattern="*ec2*.compute*" reload="true" autoReload="false" isBlackList="false" isMultiLine="false" fromSubscription="false" caseSensitive="false" />
            <match enabled="true" name="10.*" isRegex="false" pattern="http://10.*" reload="true" autoReload="false" isBlackList="false" isMultiLine="false" fromSubscription="false" caseSensitive="false" />
            <match enabled="true" name="*ec2.internal*" isRegex="false" pattern="*ec2.internal*" reload="true" autoReload="false" isBlackList="false" isMultiLine="false" fromSubscription="false" caseSensitive="false" />
            <match enabled="true" name="*compute.internal*" isRegex="false" pattern="*compute.internal*" reload="true" autoReload="false" isBlackList="false" isMultiLine="false" fromSubscription="false" caseSensitive="false" />
         </matches>
      </proxy>
   </proxies>
</foxyproxy>
```

After foxyproxy is enabled and the SSH tunnel is set up, you can click on the `Resource Manager` in the AWS UI for the cluster, which takes you to the YARN UI. For a running job, there will be an Tracking UI `Application Master` link all the way to the right for that job. Click on that, and it should bring up the Spark UI.

#### Performing Ingest

Create cluster using `emr/start-cluster.sh` and record the cluster id in `emr/emr-env.sh`

You might want to write a shell script `ingest/jobs/` that captures all the ingest parameters and and uses `aws emr add-steps` to add the steps to existing cluster.
These files need to be versioned as they will serve as a record of ingest parameters for future reference.

### Layer Tile Types

#### Native Resolution Tiles

These tiles are created by ingesting the data only in it's native resolution. If you ingest a 30m resolution dataset, then these tiles will also be 30 meter. These tiles will not map to the "z/x/y" tiles of web map tiles.

When to use this tile type:
- You want the most accuracy possible, for instance in a zonal calculation, since the raster is not further resampled to fit to a zoom level.
- You don't need to paint the tiles on a map, and don't need to pay the extra storage cost of upsampling a raster to fit to a zoom level resolution.

#### Zoomed Layout Tiles 

These tiles are created by finding the zoom level Z that best fits the resolution of the input data, and ingesting pyramid tiles from Z to 1.

When to use this tile type:
- You need to use the tiles in processes that fit to a `z/x/y` format, for instance in dynamic tile services.

#### Visual Tiles

These are painted PNG tiles that you can include directly onto a map, and would not be available to do any sort of GeoTrellis processing on the fly. They are the same zooms and resolutions as the Zoomed Layout Tiles.

When to use this tile type:
- You want to paint the layer on the map, without any dynamic processing (such as dynamic coloring).
- You want to serve out the tiles onto a web map directly from s3, without the need for a tile server.
