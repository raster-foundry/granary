# Example:
# python3 calculate-water.py s3:/sentinel-s2-l1c/tiles/36/Q/VL/2019/10/18/0/B08.jp2 \
#                      s3:/sentinel-s2-l1c/tiles/36/Q/VL/2019/10/18/0/B03.jp2 \
#                      ./test.geojson

import json
import os
import shutil
import tempfile
from urllib.parse import urlparse
from uuid import uuid4

import click
import rasterio
import numpy
from rasterio import features
import shapely.geometry
import geopandas
import subprocess
from typing import List


def download_s3_file(src: str, dest: str) -> str:
    """Downloads S3 file from source to destination"""
    subprocess.check_call(['aws', 's3', 'cp', src, dest, '--request-payer'])
    return dest


def downsample_tif(src: str, dest: str, percent_scale: int = 10) -> str:
    """Uses GDAL to downsample tif - polygonization can be memory intensive otherwise"""
    subprocess.check_call(['gdal_translate', '-outsize', f'{percent_scale}%', '0', src, dest])
    return dest


def reproject_tif(src: str, dest: str) -> str:
    """Reprojects tiff so the coordinates we get for polygons are in lat/lng"""
    subprocess.check_call(['gdalwarp', '-t_srs', 'epsg:4326', src, dest])
    return dest


def preprocess_band(band_path: str, working_directory: str) -> str:
    """Procesess initial band into downsampled and reprojected tiff, combining previous functions"""
    parsed_band_path = urlparse(band_path)
    filename = os.path.split(band_path)[-1]
    random_uuid = uuid4()

    local_path = os.path.join(working_directory, filename)

    if parsed_band_path.scheme == 's3':
        download_s3_file(band_path, local_path)
    else:
        shutil.copyfile(band_path, local_path)

    sampled_path = downsample_tif(local_path, os.path.join(working_directory, f'sampled-{random_uuid}.tif'))

    warped_path = reproject_tif(sampled_path, os.path.join(working_directory, f'warped-{random_uuid}.tif'))
    return warped_path


def get_water_polygons(nir_tif: str, green_tif: str, threshold: float = 0.3) -> List[shapely.geometry.Polygon]:
    """Use rasterio to extract water polygons with NDWI and a threshold applied"""
    with rasterio.open(nir_tif) as src:
        nir = src.read(1)
        transform = src.transform

    with rasterio.open(green_tif) as src:
        green = src.read(1)

    numpy.seterr(divide='ignore', invalid='ignore')
    ndwi = ((green.astype(float) - nir.astype(float)) / (green + nir)).astype('float32')

    # Set Water = 1, Everything Else 0
    ndwi[ndwi >= threshold] = 1
    ndwi[ndwi < threshold] = 0

    # Get geometries
    shapes = features.shapes(ndwi, transform=transform, connectivity=8)
    return [shapely.geometry.shape(p) for p, v in shapes if v == 1.0]


def copy_results(polygons: List[shapely.geometry.Polygon], local_temp_path: str, remote_path: str) -> None:
    """Copies results to local or remote S3 location as geojson"""
    click.echo(click.style(f'Copying results to {remote_path}', fg='green'))

    with open(local_temp_path, 'w') as fh:
        json.dump(geopandas.GeoSeries(polygons).__geo_interface__, fh)

    parsed_remote_path = urlparse(remote_path)
    if parsed_remote_path.scheme == 's3':
        subprocess.check_call(['aws', 's3', 'cp', local_temp_path, remote_path])
    else:
        shutil.copyfile(local_temp_path, remote_path)


@click.command("calculate")
@click.argument("nir_band")
@click.argument("green_band")
@click.argument("output_location")
@click.option("--webhook")
def run(nir_band: str, green_band: str, output_location: str, webhook: str):
    click.echo(click.style(f'Generating Water Polygons with Red {nir_band} and Green {green_band}', fg='green'))

    with tempfile.TemporaryDirectory() as dirname:
        click.echo(click.style(f'Using temporary directory {dirname}', fg='green'))
        warped_nir_path = preprocess_band(nir_band, dirname)
        warped_green_path = preprocess_band(green_band, dirname)
        polygons = get_water_polygons(warped_nir_path, warped_green_path)
        copy_results(polygons, os.path.join(dirname, 'output.geojson'), output_location)


if __name__ == '__main__':
    run()
