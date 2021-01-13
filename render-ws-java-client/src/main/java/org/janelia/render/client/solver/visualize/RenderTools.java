package org.janelia.render.client.solver.visualize;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.janelia.alignment.RenderParameters;
import org.janelia.alignment.Renderer;
import org.janelia.alignment.spec.stack.MipmapPathBuilder;
import org.janelia.alignment.spec.stack.StackMetaData;
import org.janelia.alignment.util.ImageProcessorCache;
import org.janelia.render.client.RenderDataClient;
import org.janelia.render.client.solver.MinimalTileSpec;
import org.janelia.render.client.solver.MultiResolutionSource;
import org.janelia.render.client.solver.visualize.lazy.Lazy;
import org.janelia.render.client.solver.visualize.lazy.RenderRA;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.util.volatiles.VolatileViews;
import ij.ImageJ;
import ij.ImagePlus;
import mpicbg.models.AffineModel2D;
import mpicbg.trakem2.transform.TransformMeshMappingWithMasks.ImageProcessorWithMasks;
import net.imglib2.Interval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.realtransform.AffineTransform2D;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class RenderTools
{
	final static public String ownerFormat = "%s/owner/%s";
	final static public String stackListFormat = ownerFormat + "/stacks";
	final static public String stackFormat = ownerFormat + "/project/%s/stack/%s";
	final static public String stackBoundsFormat = stackFormat  + "/bounds";
	final static public String boundingBoxFormat = stackFormat + "/z/%d/box/%d,%d,%d,%d,%f";
	final static public String renderParametersFormat = boundingBoxFormat + "/render-parameters";

	final static public int[] availableDownsamplings(
			final String baseUrl,
			final String owner,
			final String project,
			final String stack ) throws IOException
	{
		// Say you have scalings of 0.5, 0.25, 0.1
		// and I query 0.500000001
		// will it use 1.0 then - yes

		final RenderDataClient renderDataClient = new RenderDataClient(baseUrl, owner, project );
		final StackMetaData sourceStackMetaData = renderDataClient.getStackMetaData( stack );

		final MipmapPathBuilder mipmaps = sourceStackMetaData.getCurrentVersion().getMipmapPathBuilder();
		
		if ( mipmaps == null )
		{
			return new int[] { 1 }; // 
		}
		else
		{
			final int[] ds = new int[ mipmaps.getNumberOfLevels() ];

			for ( int i = 0; i < ds.length; ++i )
				ds[ i ] = (int)Math.round( Math.pow( 2, i ) );

			return ds;
		}
	}

	/**
	 * Fetch the raw image for an arbitrary scale (can crash if scale does not exist - if that is easier)
	 * 
	 * @param ipCache
	 * @param baseUrl
	 * @param owner
	 * @param project
	 * @param stack
	 * @param tileId
	 * @param scale - the preexisting downsampled image as stored on disk
	 * @return
	 */
	final static private BufferedImage renderImage(
			final ImageProcessorCache ipCache,
			final String baseUrl,
			final String owner,
			final String project,
			final String stack,
			final String tileId,
			final AffineTransform2D t,
			final double scale )
	{
		return null;
	}

	final static public ImageProcessorWithMasks renderImage(
			final ImageProcessorCache ipCache,
			final String baseUrl,
			final String owner,
			final String project,
			final String stack,
			final long x,
			final long y,
			final long z,
			final long w,
			final long h,
			final double scale,
			final boolean filter) {

		final String renderParametersUrlString = String.format(
				renderParametersFormat,
				baseUrl,
				owner,
				project,
				stack,
				z, // full res coordinates
				x, // full res coordinates
				y, // full res coordinates
				w, // full res coordinates
				h, // full res coordinates
				scale);

		// fetches the raw data 
		final RenderParameters renderParameters = RenderParameters.loadFromUrl(renderParametersUrlString); // we do have that info locally
		renderParameters.setDoFilter(filter);

		/*
		final BufferedImage image = renderParameters.openTargetImage(); // opens an empty buffer
		ArgbRenderer.render(renderParameters, image, ipCache); // loads the entire image and crops the requested size

		return image;
		*/

		return Renderer.renderImageProcessorWithMasks( renderParameters, ipCache );
	}

	public static BdvStackSource< ? > renderMultiRes(
			final String baseUrl,
			final String owner,
			final String project,
			final String stack,
			final HashMap<String, AffineModel2D> idToModels,
			final HashMap<String, MinimalTileSpec> idToTileSpec,
			BdvStackSource< ? > source,
			final int numThreads ) throws IOException
	{
		// one common ImageProcessor cache for all
		final boolean recordStats = true;
		final boolean cacheOriginalsForDownSampledImages = true;
		final ImageProcessorCache ipCache = new ImageProcessorCache( Integer.MAX_VALUE, recordStats, cacheOriginalsForDownSampledImages );

		final ArrayList< Pair< RandomAccessibleInterval< VolatileFloatType >, AffineTransform3D > > multiRes = new ArrayList<>();

		final int[] ds = availableDownsamplings( baseUrl, owner, project, stack );

		for ( final int downsampling : ds )
		{
			//LOG.info( "Assembling Multiresolution pyramid for downsampling=" + downsampling );

			final Interval interval = VisualizingRandomAccessibleInterval.computeInterval(
					idToModels,
					idToTileSpec,
					new double[] { 1.0/downsampling, 1.0/downsampling, 1.0/downsampling } );

			System.out.println( "ds=" + downsampling + ", interval=" + interval );

			final long[] min = new long[ interval.numDimensions() ];
			interval.min( min );

			final RenderRA< FloatType > renderer =
					new RenderRA<>(
							baseUrl,
							owner,
							project,
							stack,
							ipCache,
							min,
							new FloatType(),
							1.0/downsampling );

			// blockSize should be power-of-2 and at least the minimal downsampling
			final int blockSizeXY = Math.max( 64, ds[ ds.length - 1 ] );
			final int[] blockSize = new int[] { blockSizeXY, blockSizeXY, 1 };

			final RandomAccessibleInterval<FloatType> cachedImg =
					Views.translate( Lazy.process( interval, blockSize, new FloatType(), AccessFlags.setOf(), renderer ), min );

			final RandomAccessibleInterval< VolatileFloatType > volatileRA = VolatileViews.wrapAsVolatile( cachedImg );

			// the virtual image is zeroMin, this transformation puts it into the global coordinate system
			final AffineTransform3D t = new AffineTransform3D();
			t.scale( downsampling );

			multiRes.add( new ValuePair<>( volatileRA, t )  );
		}

		if ( source == null )
		{
			BdvOptions options = Bdv.options().numSourceGroups( 1 ).frameTitle( project + "_" + stack ).numRenderingThreads( numThreads );
			source = BdvFunctions.show( new MultiResolutionSource( multiRes, project + "_" + stack ), options );
		}
		else
		{
			source = BdvFunctions.show( new MultiResolutionSource( multiRes, project + "_" + stack ), Bdv.options().addTo( source ).numRenderingThreads( numThreads ) );
		}

		source.setDisplayRange( 0, 4096 );

		return source;
	}

	public static void main( String[] args ) throws IOException
	{
		String baseUrl = "http://tem-services.int.janelia.org:8080/render-ws/v1";
		String owner = "flyem";
		String project = "Z0419_25_Alpha3";
		String stack = "v1_acquire_sp_translation_nodyn";

		final int[] ds = availableDownsamplings( baseUrl, owner, project, stack );

		System.out.println( Util.printCoordinates( ds ) );

		final boolean recordStats = true;
		final boolean cacheOriginalsForDownSampledImages = true;
		final ImageProcessorCache ipCache = new ImageProcessorCache( Integer.MAX_VALUE, recordStats, cacheOriginalsForDownSampledImages );

		final boolean filter = false;

		int w = 12000;
		int h = 7500;
		int x = -6000;
		int y = -5000;
		int z = 3600;

		ImageProcessorWithMasks img1 = renderImage( ipCache, baseUrl, owner, project, stack, x, y, z, w, h, 1.0 / ds[ 4 ], filter );
		ImageProcessorWithMasks img2 = renderImage( ipCache, baseUrl, owner, project, stack, x, y, z, w, h, 1.0 / ds[ 3 ], filter );

		new ImageJ();
		final ImagePlus imp1 = new ImagePlus("img1 " + ds[ 4 ], img1.ip);
		final ImagePlus imp2 = new ImagePlus("img1 " + ds[ 3 ], img2.ip);
		imp1.show();
		imp2.show();
	}
}