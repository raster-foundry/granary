ALTER TABLE predictions ADD COLUMN output_location_copy jsonb not null default '[]';

UPDATE predictions SET output_location_copy = CASE
    WHEN right(output_location, 7) = 'geojson'
      THEN jsonb_build_array(
        jsonb_build_object(
	    'href', output_location,
	    'title', 'Model Output',
	    'type', 'application/geo+json',
	    'roles', jsonb_build_array('data')
	  )
	)
    WHEN right(output_location, 4) = 'json'
      THEN jsonb_build_array(
        jsonb_build_object(
	    'href', output_location,
	    'title', 'Model Output',
	    'type', 'application/json',
	    'roles', jsonb_build_array('data')
	  )
        )
    WHEN right(output_location, 3) = 'tif' OR right(output_location, 4) = 'tiff'
      THEN jsonb_build_array(
        jsonb_build_object(
	  'href', output_location,
	  'title', 'Model Output',
	  'type', 'image/tiff; application=geotiff; profile=cloud-optimized',
	  'roles', jsonb_build_array('data')
	)
      )
    ELSE
      '[]' :: jsonb
    END;

ALTER TABLE predictions DROP COLUMN output_location;

ALTER TABLE predictions RENAME COLUMN output_location_copy TO results;
