package org.janelia.render.client.solver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.janelia.alignment.RenderParameters;
import org.janelia.alignment.match.ModelType;
import org.janelia.alignment.spec.LeafTransformSpec;
import org.janelia.alignment.spec.ReferenceTransformSpec;
import org.janelia.alignment.spec.ResolvedTileSpecCollection;
import org.janelia.alignment.spec.ResolvedTileSpecCollection.TransformApplicationMethod;
import org.janelia.alignment.spec.TileSpec;
import org.janelia.alignment.spec.stack.StackMetaData.StackState;
import org.janelia.alignment.util.ScriptUtil;
import org.janelia.render.client.RenderDataClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mpicbg.models.Affine2D;
import mpicbg.models.AffineModel2D;
import mpicbg.models.CoordinateTransform;
import mpicbg.models.CoordinateTransformList;
import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.InterpolatedAffineModel2D;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;
import mpicbg.models.Tile;
import mpicbg.models.TileConfiguration;
import mpicbg.models.TranslationModel2D;
import net.imglib2.RandomAccess;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class SolveTools
{
	private SolveTools() {}

	protected static HashMap< Tile< ? >, Double > computeMetaDataLambdas( final Collection< Tile< ? > > tiles, final SolveItem< ?,?,? > solveItem )
	{
		// a z-section can have more than one grouped tile if they are connected from above and below
		final HashMap< Integer, List< Pair< Tile< ? >, Tile< TranslationModel2D > > > > zToTiles = fakePreAlign( tiles, solveItem );

		final ArrayList< Integer > allZ = new ArrayList<Integer>( zToTiles.keySet() );
		Collections.sort( allZ );

		final Img< DoubleType > valueX = ArrayImgs.doubles( allZ.size() );
		final Img< DoubleType > valueY = ArrayImgs.doubles( allZ.size() );

		RandomAccess< DoubleType > rX = valueX.randomAccess();
		RandomAccess< DoubleType > rY = valueY.randomAccess();
		
		for ( int z = 0; z < allZ.size(); ++ z )
		{
			final double[] offset = layerMinBounds( zToTiles.get( allZ.get( z ) ), solveItem);
			
			rX.setPosition( z, 0 );
			rY.setPosition( z, 0 );

			rX.get().set( offset[ 0 ] );
			rY.get().set( offset[ 1 ] );
		}

		RandomAccess< DoubleType > rxIn = Views.extendMirrorSingle( valueX ).randomAccess();
		RandomAccess< DoubleType > ryIn = Views.extendMirrorSingle( valueY ).randomAccess();

		final Img< DoubleType > derX = ArrayImgs.doubles( allZ.size() );
		final Img< DoubleType > derY = ArrayImgs.doubles( allZ.size() );

		RandomAccess< DoubleType > rxOut = derX.randomAccess();
		RandomAccess< DoubleType > ryOut = derY.randomAccess();

		for ( int z = 0; z < allZ.size(); ++z )
		{
			rxIn.setPosition( z - 1, 0 );
			ryIn.setPosition( z - 1, 0 );

			double x = rxIn.get().get();
			double y = ryIn.get().get();
			
			rxIn.fwd( 0 );
			ryIn.fwd( 0 );
			
			rxOut.setPosition( rxIn );
			ryOut.setPosition( ryIn );

			rxOut.get().set( Math.pow( x - rxIn.get().get(), 2 ) );
			ryOut.get().set( Math.pow( y - ryIn.get().get(), 2 ) );
		}

		final Img< DoubleType > filterX = ArrayImgs.doubles( allZ.size() );
		final Img< DoubleType > filterY = ArrayImgs.doubles( allZ.size() );

		Gauss3.gauss( 20, Views.extendMirrorSingle( derX ), filterX );
		Gauss3.gauss( 20, Views.extendMirrorSingle( derY ), filterY );

		rX = filterX.randomAccess();
		rY = filterY.randomAccess();
		
		for ( int i = 0; i < allZ.size(); ++i )
		{
			rX.setPosition( i, 0 );
			rY.setPosition( i, 0 );
		
			final double sum = (rX.get().get() + rY.get().get() );

			rY.get().set( sum );

			final double lambda = Math.max( 0, sum < 115 ? ( 0.000023333*sum*sum - 0.005233333*sum + 0.3 ) / 2.0 : 0.00674563 / 2.0 );

			rX.get().set( lambda );
		}

		Gauss3.gauss( 5, Views.extendMirrorSingle( filterX ), filterX );

		final HashMap< Tile< ? >, Double > tileToDynamicLambda = new HashMap<>();

		for ( int i = 0; i < allZ.size(); ++i )
		{
			final int z = allZ.get( i );

			rX.setPosition(new int[] { i } );
			final double lambda = rX.get().get();
					
			solveItem.zToDynamicLambda().put( z, lambda );
			for ( final Pair< Tile< ? >, Tile< TranslationModel2D > > tilePair : zToTiles.get( z ) )
				tileToDynamicLambda.put( tilePair.getA(), lambda );
		}

//		new ImageJ();
//		ImageJFunctions.show( filterX );
//		ImageJFunctions.show( filterY );
		return tileToDynamicLambda;
	}

	protected static HashMap< Integer, List< Pair< Tile< ? >, Tile< TranslationModel2D > > > > fakePreAlign( final Collection< Tile< ? > > tiles, final SolveItem<?, ?, ?> solveItem )
	{
		LOG.info( "Pre-aligning with Translation to compute dynamic lambdas..." );
		
		final HashMap< Integer, List< Pair< Tile< ? >, Tile< TranslationModel2D > > > > zToTiles = new HashMap<>();

		final HashMap< Tile< ? >, Tile< TranslationModel2D > > tilesToFaketiles = new HashMap<>();
		final HashMap< Point, Tile< ? > > p1ToTile = new HashMap<>(); // to efficiently find a tile associated with a pointmatch

		for ( final Tile< ? > tile : tiles )
		{
			final Tile< TranslationModel2D > fakeTile = new Tile<>( new TranslationModel2D() );
			tilesToFaketiles.put( tile, fakeTile );

			for ( final PointMatch pm : tile.getMatches() )
				p1ToTile.put( pm.getP1(), tile );

			final Tile< ? > aTile = solveItem.groupedTileToTiles().get( tile ).get( 0 ); 
			final String tileId = solveItem.tileToIdMap().get( aTile );
			final int z = (int)Math.round( solveItem.idToTileSpec().get( tileId ).getZ() );
			zToTiles.putIfAbsent( z, new ArrayList<>() ); 
			zToTiles.get( z ).add( new ValuePair<>( tile, fakeTile ) );
		}

		final HashSet< Tile<?> > alreadyVisited = new HashSet<>();

		for ( final Tile< ? > tile : tiles )
		{
			//LOG.info( "tile z " + Math.round( solveItem.idToTileSpec().get( solveItem.tileToIdMap().get( solveItem.groupedTileToTiles().get( tile ).get( 0 ) ) ).getZ() ) + " (" + tile.getMatches().size() + " matches). " );
			
			final HashMap< Tile< TranslationModel2D >, ArrayList< PointMatch > > matches = new HashMap<>();

			for ( final PointMatch pm : tile.getMatches() )
			{
				final Tile< ? > connectedTile = p1ToTile.get( pm.getP2() );
				
				if ( alreadyVisited.contains( connectedTile ) )
					continue;

				final Tile< TranslationModel2D > connectedFakeTile = tilesToFaketiles.get( connectedTile );

				final PointMatch newPM = new PointMatch(
						new Point( pm.getP1().getL().clone(), pm.getP1().getW().clone() ),
						new Point( pm.getP2().getL().clone(), pm.getP2().getW().clone() ),
						pm.getWeight() );
				
				matches.putIfAbsent( connectedFakeTile, new ArrayList<PointMatch>() );
				matches.get( connectedFakeTile ).add( newPM );
			}
		
			final Tile< TranslationModel2D > fakeTile = tilesToFaketiles.get( tile );

			for ( final Tile< TranslationModel2D > connectedFakeTile : matches.keySet() )
			{
				final ArrayList< PointMatch > newMatches = matches.get( connectedFakeTile ); 
				fakeTile.connect( connectedFakeTile, newMatches );
			}
			
			alreadyVisited.add( tile );
		}

		final TileConfiguration tileConfig = new TileConfiguration();
		tileConfig.addTiles( tilesToFaketiles.values() );

		try
		{
			double[] errors = computeErrors( tileConfig.getTiles() );
			LOG.info( "errors: " + errors[ 0 ] + "/" + errors[ 1 ] + "/" + errors[ 2 ] );

			final Map< Tile< ? >, Integer > tileToZ = new HashMap<>();

			for ( final Tile< ? > tile : tilesToFaketiles.keySet() )
			{
				final Tile< TranslationModel2D > fakeTile = tilesToFaketiles.get( tile );
				tileToZ.put( fakeTile, (int)Math.round( solveItem.idToTileSpec().get( solveItem.tileToIdMap().get( solveItem.groupedTileToTiles().get( tile ).get( 0 ) ) ).getZ() ) );
			}

			preAlignByLayerDistance( tileConfig, tileToZ );
			//tileConfig.preAlign();
			
			errors = computeErrors( tileConfig.getTiles() );
			LOG.info( "errors: " + errors[ 0 ] + "/" + errors[ 1 ] + "/" + errors[ 2 ] );
		}
		catch (NotEnoughDataPointsException | IllDefinedDataPointsException e)
		{
			LOG.info( "prealign failed: " + e );
			e.printStackTrace();
		}

		return zToTiles;
	}

	protected static double[] layerMinBounds( final List< Pair< Tile< ? >, Tile< TranslationModel2D > > > tilesList, final SolveItem< ?,?,? > solveItem )
	{
		double minX = Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;

		// a z-section can have more than one grouped tile if they are connected from above and below
		for ( final Pair< Tile< ? >, Tile< TranslationModel2D > > tiles : tilesList )
		{
			final Tile< ? > groupedTile = tiles.getA();
			final Tile< TranslationModel2D > fakeAlignedGroupedTile = tiles.getB();

			final AffineModel2D groupedModel = SolveTools.createAffine( fakeAlignedGroupedTile.getModel() );

			for ( final Tile< ? > tile : solveItem.groupedTileToTiles().get( groupedTile ) )
			{
				final String tileId = solveItem.tileToIdMap().get( tile );
				final MinimalTileSpec tileSpec = solveItem.idToTileSpec().get( tileId );

				final AffineModel2D affine = solveItem.idToStitchingModel().get( tileId ).copy();
				affine.preConcatenate( groupedModel );

				double[] tmp = new double[ 2 ];

				tmp[ 0 ] = 0;
				tmp[ 1 ] = tileSpec.getHeight() / 2.0;

				affine.applyInPlace( tmp );

				minX = Math.min( minX, tmp[ 0 ] );
				minY = Math.min( minY, tmp[ 1 ] );

				tmp[ 0 ] = tileSpec.getWidth() / 2;
				tmp[ 1 ] = 0;

				affine.applyInPlace( tmp );

				minX = Math.min( minX, tmp[ 0 ] );
				minY = Math.min( minY, tmp[ 1 ] );
			}
		}

		return new double[] { minX, minY };
	}

	protected static double[] computeErrors( final Collection< ? extends Tile< ? > > tiles )
	{
		double cd = 0.0;
		double minError = Double.MAX_VALUE;
		double maxError = 0.0;

		for ( final Tile< ? > t : tiles )
			t.update();
		
		for ( final Tile< ? > t : tiles )
		{
			t.update();
			final double d = t.getDistance();
			if ( d < minError ) minError = d;
			if ( d > maxError ) maxError = d;
			cd += d;
		}
		cd /= tiles.size();
		
		return new double[] { minError, cd, maxError };
	}

	public static List< Tile< ? > > preAlignByLayerDistance(
			final TileConfiguration tileConfig,
			final Map< Tile< ? >, Integer > tileToZ )
					throws NotEnoughDataPointsException, IllDefinedDataPointsException
	{
		// first get order all tiles by
		// a) unaligned
		// b) aligned - which initially only contains the fixed ones
		final ArrayList< Tile< ? > > unAlignedTiles = new ArrayList< Tile< ? > >();
		final ArrayList< Tile< ? > > alignedTiles = new ArrayList< Tile< ? > >();

		final Tile< ? > firstTile;

		// if no tile is fixed, take another */
		if ( tileConfig.getFixedTiles().size() == 0 )
		{
			final Iterator< Tile< ? > > it = tileConfig.getTiles().iterator();
			alignedTiles.add( it.next() );
			
			if ( alignedTiles.size() > 0 )
				firstTile = alignedTiles.get( 0 );
			else
				firstTile = null;
			
			while ( it.hasNext() )
				unAlignedTiles.add( it.next() );
		}
		else
		{
			for ( final Tile< ? > tile : tileConfig.getTiles() )
			{
				if ( tileConfig.getFixedTiles().contains( tile ) )
					alignedTiles.add( tile );
				else
					unAlignedTiles.add( tile );
			}
			firstTile = null;
		}

		// we go through each fixed/aligned tile and try to find a pre-alignment
		// for all other unaligned tiles
		for ( final ListIterator< Tile< ?> > referenceIterator = alignedTiles.listIterator(); referenceIterator.hasNext(); )
		{
			// once all tiles are aligned we can quit this loop
			if ( unAlignedTiles.size() == 0 )
				break;

			// get the next reference tile (either a fixed or an already aligned one
			final Tile< ? > referenceTile = referenceIterator.next();

			// transform all reference points into the reference coordinate system
			// so that we get the direct model even if we are not anymore at the
			// level of the fixed tile
			referenceTile.apply();

			//
			// NEW: we sort the unaligned by distance to the reference
			//
			Collections.sort( unAlignedTiles, new Comparator<Tile< ? >>()
			{
				@Override
				public int compare( final Tile< ? > o1, final Tile< ? > o2 )
				{	
					return deltaZ( o2, referenceTile ) - deltaZ( o1, referenceTile );
				}

				public int deltaZ( final Tile<?> tile1, final Tile<?> tile2 )
				{
					return Math.abs( tileToZ.get( tile1 ) - tileToZ.get( tile2 ) );
				}
			});
			
			// now we go through the unaligned tiles to see if we can align it to the current reference tile one
			for ( final ListIterator< Tile< ?> > targetIterator = unAlignedTiles.listIterator(); targetIterator.hasNext(); )
			{
				// get the tile that we want to preregister
				final Tile< ? > targetTile = targetIterator.next();

				// target tile is connected to reference tile
				if ( referenceTile.getConnectedTiles().contains( targetTile ) )
				{
					// extract all PointMatches between reference and target tile and fit a model only on these
					final ArrayList< PointMatch > pm = tileConfig.getConnectingPointMatches( targetTile, referenceTile );

					// are there enough matches?
					if ( pm.size() > targetTile.getModel().getMinNumMatches() )
					{
						// fit the model of the targetTile to the subset of matches
						// mapping its local coordinates target.p.l into the world
						// coordinates reference.p.w
						// this will give us an approximation for the global optimization
						targetTile.getModel().fit( pm );

						// now that we managed to fit the model we remove the
						// Tile from unaligned tiles and add it to aligned tiles
						targetIterator.remove();

						// now add the aligned target tile to the end of the reference list
						int countFwd = 0;

						while ( referenceIterator.hasNext() )
						{
							referenceIterator.next();
							++countFwd;
						}
						referenceIterator.add( targetTile );

						// move back to the current position
						// (+1 because it add just behind the current position)
						for ( int j = 0; j < countFwd + 1; ++j )
							referenceIterator.previous();
					}
				}

			}
		}

		if ( firstTile != null )
		{
			for ( final Tile< ? > templateTile : firstTile.getConnectedTiles() )
			{
				final ArrayList< PointMatch > pm = tileConfig.getConnectingPointMatches( firstTile, templateTile );
			
				if ( pm.size() > firstTile.getModel().getMinNumMatches() )
				{
					firstTile.getModel().fit( pm );
					break;
				}
			}
		}
	
		return unAlignedTiles;
	}

	protected static AffineModel2D createAffine( final Affine2D< ? > model )
	{
		final AffineModel2D m = new AffineModel2D();
		m.set( model.createAffine() );

		return m;
	}

	protected static List< PointMatch > duplicate( List< PointMatch > pms )
	{
		final List< PointMatch > copy = new ArrayList<>();

		for ( final PointMatch pm : pms )
			copy.add( new PointMatch( pm.getP1().clone(), pm.getP2().clone(), pm.getWeight() ) );

		return copy;
	}

	public static List< PointMatch > createRelativePointMatches(
			final List< PointMatch > absolutePMs,
			final Model< ? > pModel,
			final Model< ? > qModel )
	{
		final List< PointMatch > relativePMs = new ArrayList<>( absolutePMs.size() );

		if ( absolutePMs.size() == 0 )
			return relativePMs;

		final int n = absolutePMs.get( 0 ).getP1().getL().length;

		for ( final PointMatch absPM : absolutePMs )
		{
			final double[] pLocal = new double[ n ];
			final double[] qLocal = new double[ n ];

			for (int d = 0; d < n; ++d )
			{
				pLocal[ d ] = absPM.getP1().getL()[ d ];
				qLocal[ d ] = absPM.getP2().getL()[ d ];
			}

			if ( pModel != null )
				pModel.applyInPlace( pLocal );

			if ( qModel != null )
				qModel.applyInPlace( qLocal );

			relativePMs.add( new PointMatch( new Point( pLocal ), new Point( qLocal ), absPM.getWeight() ) );
		}

		return relativePMs;
	}


	public static AffineModel2D createAffineModel( final RigidModel2D rigid )
	{
		final double[] array = new double[ 6 ];
		rigid.toArray( array );
		final AffineModel2D affine = new AffineModel2D();
		affine.set( array[ 0 ], array[ 1 ], array[ 2 ], array[ 3 ], array[ 4 ], array[ 5 ] );
		return affine;
	}

	//protected abstract void run() throws IOException, ExecutionException, InterruptedException, NoninvertibleModelException;

	// must be called after all Tilespecs are updated
	public static void completeStack( final String targetStack, final RunParameters runParams ) throws IOException
	{
		runParams.targetDataClient.setStackState( targetStack, StackState.COMPLETE );
	}

	public static < B extends Model< B > & Affine2D< B > > Pair< Tile< B >, AffineModel2D > buildTileFromSpec(
			final B instance,
			final int samplesPerDimension,
			final TileSpec tileSpec )
	{
        final AffineModel2D lastTransform = loadLastTransformFromSpec( tileSpec );
        final AffineModel2D lastTransformCopy = lastTransform.copy();

        final double sampleWidth = (tileSpec.getWidth() - 1.0) / (samplesPerDimension - 1.0);
        final double sampleHeight = (tileSpec.getHeight() - 1.0) / (samplesPerDimension - 1.0);

        try {
            ScriptUtil.fit(instance, lastTransformCopy, sampleWidth, sampleHeight, samplesPerDimension);
        } catch (final Throwable t) {
            throw new IllegalArgumentException(instance.getClass() + " model derivation failed for tile '" +
                                               tileSpec.getTileId() + "', cause: " + t.getMessage(),
                                               t);
        }

        return new ValuePair<>(
        		new Tile< B >( instance ), 
        		lastTransform.copy() );
	}


	public static < B extends Model< B > & Affine2D< B > > Pair< Tile<InterpolatedAffineModel2D<AffineModel2D, B>>, AffineModel2D > buildTileFromSpec(
			final int samplesPerDimension,
			final ModelType regularizerModelType,
			final double startLambda,
			final TileSpec tileSpec )
	{
        final AffineModel2D lastTransform = loadLastTransformFromSpec( tileSpec );
        final AffineModel2D lastTransformCopy = lastTransform.copy();

        final double sampleWidth = (tileSpec.getWidth() - 1.0) / (samplesPerDimension - 1.0);
        final double sampleHeight = (tileSpec.getHeight() - 1.0) / (samplesPerDimension - 1.0);

        final B regularizer = regularizerModelType.getInstance();

        try {
            ScriptUtil.fit(regularizer, lastTransformCopy, sampleWidth, sampleHeight, samplesPerDimension);
        } catch (final Throwable t) {
            throw new IllegalArgumentException(regularizer.getClass() + " model derivation failed for tile '" +
                                               tileSpec.getTileId() + "', cause: " + t.getMessage(),
                                               t);
        }

        return new ValuePair<>(
        		new Tile<>(new InterpolatedAffineModel2D<>(
        				lastTransformCopy,
        				regularizer,
        				startLambda)), // note: lambda gets reset during optimization loops
        		lastTransform.copy() );
	}

	public static < M extends Model< M > & Affine2D< M > > Tile< M > buildTile(
			final AffineModel2D lastTransform,
			final M model,
			final int width,
			final int height,
			final int samplesPerDimension
			)
	{
        final double sampleWidth = (width - 1.0) / (samplesPerDimension - 1.0);
        final double sampleHeight = (height - 1.0) / (samplesPerDimension - 1.0);

        try
        {
            ScriptUtil.fit(model, lastTransform, sampleWidth, sampleHeight, samplesPerDimension);
        }
        catch (final Throwable t)
        {
            throw new IllegalArgumentException(model.getClass() + " model derivation failed, cause: " + t.getMessage(), t);
        }

        return new Tile<>(model);
	}

	public static TileSpec getTileSpec(
			final String stack,
			final RunParameters runParams,
			final String sectionId,
			final String tileId ) throws IOException {
		
		return getTileSpec( runParams.sectionIdToZMap, runParams.zToTileSpecsMap, runParams.renderDataClient, stack, sectionId, tileId );
	}

	public static TileSpec getTileSpec(
			final Map<String, ? extends List<Double>> sectionIdToZMap,
			final Map<Double, ResolvedTileSpecCollection> zToTileSpecsMap,
			final RenderDataClient renderDataClient,
			final String stack,
			final String sectionId,
			final String tileId ) throws IOException {

        TileSpec tileSpec = null;

        if (sectionIdToZMap.containsKey(sectionId)) {

            for (final Double z : sectionIdToZMap.get(sectionId)) {

                if ( !zToTileSpecsMap.containsKey(z)) {

//                    if (runParams.totalTileCount > 100000) {
//                        throw new IllegalArgumentException("More than 100000 tiles need to be loaded - please reduce z values");
//                    }

                    final ResolvedTileSpecCollection resolvedTiles = renderDataClient.getResolvedTiles(stack, z);

                    // check for accidental use of rough aligned stack ...
                    resolvedTiles.getTileSpecs().forEach(ts -> {
                        if (ts.getLastTransform() instanceof ReferenceTransformSpec) {
                            throw new IllegalStateException(
                                    "last transform for tile " + ts.getTileId() +
                                    " is a reference transform which will break this fragile client, " +
                                    "make sure --stack is not a rough aligned stack ");
                        }
                    });

                    resolvedTiles.resolveTileSpecs();
                    zToTileSpecsMap.put(z, resolvedTiles);
                    //runParams.totalTileCount += resolvedTiles.getTileCount();
                }

                final ResolvedTileSpecCollection resolvedTileSpecCollection = zToTileSpecsMap.get(z);
                tileSpec = resolvedTileSpecCollection.getTileSpec(tileId);

                if (tileSpec != null) {
                    break;
                }
            }
            
        }

        return tileSpec;
    }

	public static RenderParameters getRenderParametersForTile( final String owner,
			final String project, final String stack, final String tileId,
			final double renderScale )
	{
		final String baseTileUrl = "http://renderer-dev.int.janelia.org:8080/render-ws/v1/owner/" + owner + "/project/" + project + "/stack/" + stack + "/tile/";
		final String urlSuffix = "/render-parameters?scale=" + renderScale;
		// TODO: add &fillWithNoise=true ?
		// TODO: add &excludeMask=true ?
		final String url = baseTileUrl + tileId + urlSuffix;

		final RenderParameters renderParameters = RenderParameters.loadFromUrl( url );
		renderParameters.setDoFilter( false );
		renderParameters.initializeDerivedValues();

		renderParameters.validate();

		// remove mipmapPathBuilder so that we don't get exceptions when /nrs is
		// not mounted
		renderParameters.setMipmapPathBuilder( null );
		renderParameters.applyMipmapPathBuilderToTileSpecs();

		return renderParameters;
	}
	//
	// overwrites the area that was re-aligned or it preconcatenates
	//
	public static void saveTargetStackTiles(
			final String stack, // parameters.stack
			final String targetStack, // parameters.targetStack
			final RunParameters runParams,
			final Map< String, AffineModel2D > idToModel,
			final AffineModel2D relativeModel,
			final List< Double > zToSave,
			final TransformApplicationMethod applyMethod ) throws IOException
	{
		LOG.info( "saveTargetStackTiles: entry, saving tile specs in {} layers", zToSave.size() );

		for ( final Double z : zToSave )
		{
			final ResolvedTileSpecCollection resolvedTiles;

			if ( !runParams.zToTileSpecsMap.containsKey( z ) )
			{
				resolvedTiles = runParams.renderDataClient.getResolvedTiles( stack, z );
			}
			else
			{
				resolvedTiles = runParams.zToTileSpecsMap.get( z );
			}

			if ( idToModel != null || relativeModel != null )
			{
				for (final TileSpec tileSpec : resolvedTiles.getTileSpecs())
				{
					final String tileId = tileSpec.getTileId();
					final AffineModel2D model;
	
					if ( applyMethod.equals(  TransformApplicationMethod.REPLACE_LAST  ) )
						model = idToModel.get( tileId );
					else if ( applyMethod.equals( TransformApplicationMethod.PRE_CONCATENATE_LAST ))
						model = relativeModel;
					else
						throw new RuntimeException( "not supported: " + applyMethod );
	
					if ( model != null )
					{
						resolvedTiles.addTransformSpecToTile( tileId,
								getTransformSpec( model ),
								applyMethod );
					}
				}
			}

			if ( resolvedTiles.getTileCount() > 0 )
				runParams.targetDataClient.saveResolvedTiles( resolvedTiles, targetStack, null );
			else
				LOG.info( "skipping tile spec save since no specs are left to save" );
		}

		LOG.info( "saveTargetStackTiles: exit" );
	}

	private static LeafTransformSpec getTransformSpec( final AffineModel2D forModel )
	{
		final double[] m = new double[ 6 ];
		forModel.toArray( m );
		final String data = String.valueOf( m[ 0 ] ) + ' ' + m[ 1 ] + ' ' + m[ 2 ] + ' ' + m[ 3 ] + ' ' + m[ 4 ] + ' ' + m[ 5 ];
		return new LeafTransformSpec( mpicbg.trakem2.transform.AffineModel2D.class.getName(), data );
	}

	public static AffineModel2D loadLastTransformFromSpec( final TileSpec tileSpec )
	{
		// TODO: make sure there is only one transform
        final CoordinateTransformList<CoordinateTransform> transformList = tileSpec.getTransformList();

        if ( transformList.getList( null ).size() != 1 )
        	throw new RuntimeException( "size " + transformList.getList( null ).size() );
        final AffineModel2D lastTransform = (AffineModel2D)
                transformList.get(transformList.getList(null).size() - 1);
        return lastTransform;
	}

	private static final Logger LOG = LoggerFactory.getLogger(SolveTools.class);
}