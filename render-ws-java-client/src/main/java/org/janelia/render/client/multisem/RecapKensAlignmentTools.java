package org.janelia.render.client.multisem;

import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ImageProcessor;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;
import mpicbg.stitching.ImageCollectionElement;
import mpicbg.stitching.TextFileAccess;
import mpicbg.trakem2.transform.CoordinateTransform;
import mpicbg.trakem2.transform.CoordinateTransformList;
import mpicbg.trakem2.transform.TransformMesh;
import mpicbg.trakem2.transform.TransformMeshMapping;
import stitching.utils.Log;

public class RecapKensAlignmentTools
{
	/*
	 * From stitching code
	 */
	public static ArrayList< ImageCollectionElement > getLayoutFromFile( final String directory, final String layoutFile )
	{
		final ArrayList< ImageCollectionElement > elements = new ArrayList< ImageCollectionElement >();
		int dim = -1;
		int index = 0;
		boolean multiSeries = false;
		// A HashMap using the filename (including the full path) as the key is
		// used to access the individual tiles of a multiSeriesFile. This way
		// it's very easy to check if a file has already been opened. Note that
		// the map doesn't get used in the case of single series files below!
		// TODO: check performance on large datasets! Use an array for the
		// ImagePlus'es otherwise and store the index number in the hash map!
		Map<String, ImagePlus[]> multiSeriesMap = new HashMap<String, ImagePlus[]>();
		String pfx = "Stitching_Grid.getLayoutFromFile: ";
		try {
			final BufferedReader in = TextFileAccess.openFileRead( new File( directory, layoutFile ) );
			if ( in == null ) {
				Log.error(pfx + "Cannot find tileconfiguration file '" + new File( directory, layoutFile ).getAbsolutePath() + "'");
				return null;
			}
			int lineNo = 0;
			pfx += "Line ";
			while ( in.ready() ) {
				String line = in.readLine().trim();
				lineNo++;
				if ( !line.startsWith( "#" ) && line.length() > 3 ) {
					if ( line.startsWith( "dim" ) ) {  // dimensionality parsing
						String entries[] = line.split( "=" );
						if ( entries.length != 2 ) {
							Log.error(pfx + lineNo + " does not look like [ dim = n ]: " + line);
							return null;						
						}
						
						try {
							dim = Integer.parseInt( entries[1].trim() );
						}
						catch ( NumberFormatException e ) {
							Log.error(pfx + lineNo + ": Cannot parse dimensionality: " + entries[1].trim());
							return null;														
						}

					} else if ( line.startsWith( "multiseries" ) )  {
						String entries[] = line.split( "=" );
						if ( entries.length != 2 ) {
							Log.error(pfx + lineNo + " does not look like [ multiseries = (true|false) ]: " + line);
							return null;
						}

						if (entries[1].trim().equals("true")) {
							multiSeries = true;
							Log.info(pfx + lineNo + ": parsing MultiSeries configuration.");
						}

					} else {  // body parsing (tiles + coordinates)
						if ( dim < 0 ) {
							Log.error(pfx + lineNo + ": Header missing, should look like [dim = n], but first line is: " + line);
							return null;							
						}
						
						if ( dim < 2 || dim > 3 ) {
							Log.error(pfx + lineNo + ": only dimensions of 2 and 3 are supported: " + line);
							return null;							
						}
						
						// read image tiles
						String entries[] = line.split(";");
						if (entries.length != 3) {
							Log.error(pfx + lineNo + " does not have 3 entries! [fileName; seriesNr; (x,y,...)]");
							return null;						
						}

						String imageName = entries[0].trim();
						if (imageName.length() == 0) {
							Log.error(pfx + lineNo + ": You have to give a filename [fileName; ; (x,y,...)]: " + line);
							return null;						
						}
						
						int seriesNr = -1;
						if (multiSeries) {
							String imageSeries = entries[1].trim();  // sub-volume (series nr)
							if (imageSeries.length() == 0) {
								Log.info(pfx + lineNo + ": Series index required [fileName; series; (x,y,...)" );
							} else {
								try {
									seriesNr = Integer.parseInt( imageSeries );
									Log.info(pfx + lineNo + ": Series nr (sub-volume): " + seriesNr);
								}
								catch ( NumberFormatException e ) {
									Log.error(pfx + lineNo + ": Cannot parse series nr: " + imageSeries);
									return null;
								}
							}
						}

						String point = entries[2].trim();  // coordinates
						if (!point.startsWith("(") || !point.endsWith(")")) {
							Log.error(pfx + lineNo + ": Wrong format of coordinates: (x,y,...): " + point);
							return null;
						}
						
						point = point.substring(1, point.length() - 1);  // crop enclosing braces
						String points[] = point.split(",");
						if (points.length != dim) {
							Log.error(pfx + lineNo + ": Wrong format of coordinates: (x,y,z,...), dim = " + dim + ": " + point);
							return null;
						}
						final float[] offset = new float[ dim ];
						for ( int i = 0; i < dim; i++ ) {
							try {
								offset[ i ] = Float.parseFloat( points[i].trim() ); 
							}
							catch (NumberFormatException e) {
								Log.error(pfx + lineNo + ": Cannot parse number: " + points[i].trim());
								return null;							
							}
						}
						
						// now we can assemble the ImageCollectionElement:
						ImageCollectionElement element = new ImageCollectionElement(
								new File( directory, imageName ), index++ );
						element.setDimensionality( dim );
						if ( dim == 3 )
							element.setModel( new TranslationModel3D() );
						else
							element.setModel( new TranslationModel2D() );
						element.setOffset( offset );

						if (multiSeries) {
							throw new RuntimeException( "not supported");
							/*
							final String imageNameFull = element.getFile().getAbsolutePath();
							if (multiSeriesMap.get(imageNameFull) == null) {
								Log.info(pfx + lineNo + ": Loading MultiSeries file: " + imageNameFull);
								multiSeriesMap.put(imageNameFull, openBFDefault(imageNameFull));
							}
							element.setImagePlus(multiSeriesMap.get(imageNameFull)[seriesNr]);*/
						}

						elements.add( element );
					}
				}
			}
		}
		catch ( IOException e ) {
			Log.error( "Stitching_Grid.getLayoutFromFile: " + e );
			return null;
		}

		return elements;
	}

	// from: register_virtual_stack_slices/src/main/java/register_virtual_stack/Transform_Virtual_Stack_MT.java
	/**
	 * Read coordinate transform from file (generated in Register_Virtual_Stack)
	 *
	 * @param filename  complete file name (including path)
	 * @return true if the coordinate transform was properly read, false otherwise.
	 */
	public static CoordinateTransformList<CoordinateTransform> readCoordinateTransform( String filename )
	{
		final CoordinateTransformList<CoordinateTransform> ctl = new CoordinateTransformList<CoordinateTransform>();
		try 
		{
			final FileReader fr = new FileReader(filename);
			final BufferedReader br = new BufferedReader(fr);
			String line = null;
			while ((line = br.readLine()) != null) 
			{
				int index = -1;
				if( (index = line.indexOf("class=")) != -1)
				{
					// skip "class"
					index+= 5;
					// read coordinate transform class name
					final int index2 = line.indexOf("\"", index+2); 
					final String ct_class = line.substring(index+2, index2);
					final CoordinateTransform ct = (CoordinateTransform) Class.forName(ct_class).newInstance();
					// read coordinate transform info
					final int index3 = line.indexOf("=", index2+1);
					final int index4 = line.indexOf("\"", index3+2); 
					final String data = line.substring(index3+2, index4);
					ct.init(data);
					ctl.add(ct);
				}
			}
			br.close();
		
		} catch (FileNotFoundException e) {
			System.err.println("File not found exception" + e);
			
		} catch (IOException e) {
			System.err.println("IOException exception" + e);
			
		} catch (NumberFormatException e) {
			System.err.println("Number format exception" + e);
			
		} catch (InstantiationException e) {
			System.err.println("Instantiation exception" + e);
			
		} catch (IllegalAccessException e) {
			System.err.println("Illegal access exception" + e);
			
		} catch (ClassNotFoundException e) {
			System.err.println("Class not found exception" + e);
			
		}
		return ctl;
	}

	// adapted from register_virtual_stack_slices/src/main/java/register_virtual_stack/Register_Virtual_Stack_MT.applyTransformAndSave()
	// to only return the bounding box
	public static Rectangle getBoundingBox(
			final int width,
			final int height,
			final CoordinateTransform transform)
	{
		// Open next image
		//final ImagePlus imp2 = readImage(source_dir + file_name);

		// Calculate transform mesh
		final TransformMesh mesh = new TransformMesh(transform, 32, width, height);
		TransformMeshMapping mapping = new TransformMeshMapping(mesh);

		// Create interpolated deformed image with black background
		//imp2.getProcessor().setValue(0);
		//final ImageProcessor ip2 = interpolate ? mapping.createMappedImageInterpolated(imp2.getProcessor()) : mapping.createMappedImage(imp2.getProcessor()); 
		//imp2.setProcessor(imp2.getTitle(), ip2);
		
		//imp2.show();

		// Accumulate bounding boxes, so in the end they can be reopened and re-saved with an enlarged canvas.
		final Rectangle currentBounds = mesh.getBoundingBox();
		return currentBounds;
		//bounds[i] = currentBounds;
		
		// Save target image
		//return new FileSaver(imp2).saveAsTiff(makeTargetPath(target_dir, file_name));
	}
}