package org.janelia.alignment.match;

import ij.ImageJ;
import ij.ImagePlus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import mpicbg.imagefeatures.Feature;
import mpicbg.imagefeatures.FloatArray2DSIFT;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.AffineModel2D;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.spim.io.IOFunctions;
import mpicbg.trakem2.transform.TransformMeshMappingWithMasks.ImageProcessorWithMasks;

import org.janelia.alignment.RenderParameters;
import org.janelia.alignment.Renderer;
import org.janelia.alignment.match.MatchFilter.FilterType;
import org.janelia.alignment.match.parameters.GeometricDescriptorParameters;
import org.janelia.alignment.match.parameters.MatchDerivationParameters;
import org.janelia.alignment.util.ImageDebugUtil;
import org.janelia.alignment.util.ImageProcessorCache;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.imglib2.KDTree;
import net.imglib2.RealPoint;
import net.imglib2.neighborsearch.KNearestNeighborSearchOnKDTree;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;

import static org.janelia.alignment.match.GeometricDescriptorMatcherTest.*;

/**
 * Runs SIFT match derivation followed by peak extraction for two tiles.
 */
public class GeometricDescriptorSIFTMatcherTest {

    @Test
    public void testNothing() {
		Assert.assertTrue(true);
	}

    public static void main(final String[] args) {

        // -------------------------------------------------------------------
        // NOTES:
        //
        //   1. make sure /groups/flyem/data is mounted
        //      -- on Mac, after mount need to ln -s /Volumes/flyemdata /groups/flyem/data
        //
        //   2. update testTilePairIndex (and possibly TEST_TILE_PAIRS)

        // -------------------------------------------------------------------

        // RUN SIFT HERE
        final int testTilePairIndex = 0;

        final String owner = TEST_TILE_PAIRS[testTilePairIndex][0];
        final String project = TEST_TILE_PAIRS[testTilePairIndex][1];
        final String stack = TEST_TILE_PAIRS[testTilePairIndex][2];
        final String tileId1 = TEST_TILE_PAIRS[testTilePairIndex][3];
        final String tileId2 = TEST_TILE_PAIRS[testTilePairIndex][4];

        final Integer clipSize = TEST_TILE_PAIRS[testTilePairIndex].length > 5 ? null : 500;

        // -------------------------------------------------------------------
        // run test ...

        /*
		renderScale: 0.15 fdSize: 4 minScale: 0.25 maxScale: 1 steps: 3
		Match Parameters:	modelType: AFFINE regularizerModelType: RIGID interpolatedModelLambda: 0.25
		ROD: 0.92 iterations: 1000 maxEpsilon: 50 minInlierRatio: 0
		minNumInliers: 10 maxTrust: 4 filter: SINGLE_SET
         */

        // RUN SIFT
        /*FeatureExtractionParameters siftParam = new FeatureExtractionParameters();
        siftParam.fdSize = 4;
        siftParam.minScale = 0.25;
        siftParam.maxScale = 1.0;
        siftParam.steps = 3;*/
        final double renderScaleSIFT = 0.15;

        final RenderParameters renderParametersTile1 =
                getRenderParametersForTile(owner, project, stack, tileId1, renderScaleSIFT, true, clipSize, MontageRelativePosition.LEFT);
        final RenderParameters renderParametersTile2 =
                getRenderParametersForTile(owner, project, stack, tileId2, renderScaleSIFT, true, clipSize, MontageRelativePosition.RIGHT);

        final ImageProcessorWithMasks ipmSIFT1 = Renderer.renderImageProcessorWithMasks(renderParametersTile1, ImageProcessorCache.DISABLED_CACHE);
        final ImageProcessorWithMasks ipmSIFT2 = Renderer.renderImageProcessorWithMasks(renderParametersTile2, ImageProcessorCache.DISABLED_CACHE);

        final FloatArray2DSIFT.Param coreSiftParameters = new FloatArray2DSIFT.Param();
        coreSiftParameters.fdSize = 4;
        coreSiftParameters.steps = 3;
        final double minScale = 0.25;
        final double maxScale = 1.0;

        final CanvasFeatureExtractor canvasFeatureExtractor = new CanvasFeatureExtractor( coreSiftParameters, minScale, maxScale );
        final List< Feature > f1 = canvasFeatureExtractor.extractFeaturesFromImageAndMask( ipmSIFT1.ip, ipmSIFT1.mask );
        final List< Feature > f2 = canvasFeatureExtractor.extractFeaturesFromImageAndMask( ipmSIFT2.ip, ipmSIFT2.mask );

        // GET INLIERS
        final MatchDerivationParameters ransacParam = new MatchDerivationParameters( 0.92f, ModelType.AFFINE, 1000, 50, 0, 10, 4, null, FilterType.SINGLE_SET );
        ransacParam.matchRegularizerModelType = ModelType.RIGID;
        ransacParam.matchInterpolatedModelLambda = 0.25;

        final CanvasFeatureMatcher matcherSIFT = new CanvasFeatureMatcher(ransacParam, renderScaleSIFT);
        final CanvasMatchResult resultSIFT = matcherSIFT.deriveMatchResult(f1, f2);

        final List<PointMatch> inliersSIFT = resultSIFT.getInlierPointMatchList();

        final List<Point> inlierPoints1 = new ArrayList<>();
        PointMatch.sourcePoints(inliersSIFT, inlierPoints1);

        final List<Point> inlierPoints2 = new ArrayList<>();
        PointMatch.targetPoints(inliersSIFT, inlierPoints2);

        LOG.debug( "#inliersSIFT: " + inliersSIFT.size() );

        final GeometricDescriptorParameters gdParameters = getInitialDescriptorParameters();

        new ImageJ();

        final double blockRadiusSIFT = gdParameters.fullScaleBlockRadius * renderScaleSIFT;
        
        final Pair< Long, Long > coverage = 
                CoverageUtils.computeOverlappingCoverage(ipmSIFT1.ip, ipmSIFT1.mask,
                                                         ipmSIFT2.ip, ipmSIFT2.mask,
                                                         inliersSIFT,
                                                         new AffineModel2D(), // model used to fit the inliers and tranform Q
                                                         blockRadiusSIFT );
        
        LOG.debug( "area coverage [%]: " + (double)coverage.getA() / (double)coverage.getB() * 100 );
        //SimpleMultiThreading.threadHaltUnClean();

        // debug
        /*
        final ImagePlus impSIFT1 = new ImagePlus(tileId1 + "_SIFT", ipmSIFT1.ip);
        final ImagePlus impSIFT2 = new ImagePlus(tileId2 + "_SIFT", ipmSIFT1.ip);
        ImageDebugUtil.drawBlockedRegions(inlierPoints1, blockRadiusSIFT, impSIFT1);
        ImageDebugUtil.setPointRois(inlierPoints1, impSIFT1);
        ImageDebugUtil.drawBlockedRegions(inlierPoints2, blockRadiusSIFT, impSIFT2);
        ImageDebugUtil.setPointRois(inlierPoints2, impSIFT2);
        impSIFT1.show();
        impSIFT2.show();

        SimpleMultiThreading.threadHaltUnClean();*/

        //
        // NOW Run Geometric Descriptor matching using the set inliers for masking
        //

        // Geometric descriptor parameters
        final double renderScaleGeo = 0.25;

        final CanvasPeakExtractor extractorGeo = new CanvasPeakExtractor(gdParameters);
        final CanvasPeakMatcher matcherGeo =
                new CanvasPeakMatcher(gdParameters,
                                      GeometricDescriptorMatcherTest.getMatchFilterParameters(renderScaleGeo),
									  renderScaleGeo);

        // -------------------------------------------------------------------
        // run test ...

        // modify SIFT render parameters for Geo
        renderParametersTile1.setScale(renderScaleGeo);
        renderParametersTile1.setDoFilter(false);
        renderParametersTile2.setScale(renderScaleGeo);
        renderParametersTile2.setDoFilter(false);

        final ImageProcessorWithMasks geo1 = Renderer.renderImageProcessorWithMasks(renderParametersTile1, ImageProcessorCache.DISABLED_CACHE);
        final ImageProcessorWithMasks geo2 = Renderer.renderImageProcessorWithMasks(renderParametersTile2, ImageProcessorCache.DISABLED_CACHE);

        final ImagePlus impGeo1 = new ImagePlus("peak_" + tileId1, geo1.ip);
        final ImagePlus impGeo2 = new ImagePlus("peak_" + tileId2, geo2.ip);

        /*
        // debug
        final double blockRadiusGeo = gdParameters.fullScaleBlockRadius * renderScaleGeo;
        // adjust the locations of the inliers to the potentially difference renderScale
        final Pair< ArrayList< Point >, ArrayList< Point > >
                adjustedInliers = adjustInliers(inliersSIFT, renderScaleSIFT, renderScaleGeo );

        ImageDebugUtil.drawBlockedRegions( adjustedInliers.getA(), blockRadiusGeo, impGeo1 );
        ImageDebugUtil.drawBlockedRegions( adjustedInliers.getB(), blockRadiusGeo, impGeo2 );
        ImageDebugUtil.setPointRois( adjustedInliers.getA(), impGeo1 );
        ImageDebugUtil.setPointRois( adjustedInliers.getB(), impGeo2 );
        impGeo1.show();
        impGeo2.show();
        */

        // extract DoG peaks for Descriptor-based registration
        List<DifferenceOfGaussianPeak<FloatType>> canvasPeaks1 = extractorGeo.extractPeaksFromImageAndMask(geo1.ip, geo1.mask);
        List<DifferenceOfGaussianPeak<FloatType>> canvasPeaks2 = extractorGeo.extractPeaksFromImageAndMask(geo2.ip, geo2.mask);

        LOG.debug( "#detections: " + canvasPeaks1.size() + " & " + canvasPeaks2.size() );

        // filter DoG peaks for Descriptor-based registration using the SIFT matches
        extractorGeo.filterPeaksByInliers(canvasPeaks1, renderScaleGeo, inlierPoints1, renderScaleSIFT);
        extractorGeo.filterPeaksByInliers(canvasPeaks2, renderScaleGeo, inlierPoints2, renderScaleSIFT);

        LOG.debug( "#detections after filtering by sift: " + canvasPeaks1.size() + " & " + canvasPeaks2.size() );

        // filter DoG peaks by max number (doesn't work too well)
        //canvasPeaks1 = limitList( 2000, 1, canvasPeaks1 );
        //canvasPeaks2 = limitList( 2000, 1, canvasPeaks2 );

        //LOG.debug( "#detections after filtering by max number: " + canvasPeaks1.size() + " & " + canvasPeaks2.size() );

        // filter DoG peaks by nonMaximalSuppression
        canvasPeaks1 = extractorGeo.nonMaximalSuppression(canvasPeaks1, renderScaleGeo);
        canvasPeaks2 = extractorGeo.nonMaximalSuppression(canvasPeaks2, renderScaleGeo);

        LOG.debug( "#detections after nonMaximalSuppression: " + canvasPeaks1.size() + " & " + canvasPeaks2.size() );

        ImageDebugUtil.setPeakPointRois(canvasPeaks1, impGeo1);
        ImageDebugUtil.setPeakPointRois(canvasPeaks2, impGeo2);

        impGeo1.show();
        impGeo2.show();

        // important, we need to use the adjusted parameters here as well
        final CanvasMatchResult resultGeo = matcherGeo.deriveMatchResult(canvasPeaks1, canvasPeaks2);

        final List<PointMatch> inliersGeo = resultGeo.getInlierPointMatchList();
        LOG.debug( "#inliersGeo: " + inliersGeo.size() );

        final ImagePlus ipnew1 = new ImagePlus("match_" + tileId1, geo1.ip.duplicate());
        final ImagePlus ipnew2 = new ImagePlus("match_" + tileId2, geo2.ip.duplicate());

        ImageDebugUtil.setPointMatchRois(inliersGeo, ipnew1, ipnew2);

        ipnew1.show();
        //ipnew2.show();

        SimpleMultiThreading.threadHaltUnClean();
    }

	private static final String[] limitDetectionChoice = {"Brightest", "Around median (of those above threshold)", "Weakest (above threshold)" };

	@SuppressWarnings("unused")
	public static List< DifferenceOfGaussianPeak<FloatType> > limitList(final int maxDetections, final int maxDetectionsTypeIndex, final List< DifferenceOfGaussianPeak<FloatType> > list )
	{
		if ( list.size() <= maxDetections )
		{
			return list;
		}
		else
		{
			IOFunctions.println( "(" + new Date( System.currentTimeMillis() ) + "): Limiting detections to " + maxDetections + ", type = " + limitDetectionChoice[ maxDetectionsTypeIndex ] );

			sortDetections( list );

			final ArrayList< DifferenceOfGaussianPeak<FloatType> > listNew = new ArrayList<>();

			if ( maxDetectionsTypeIndex == 0 )
			{
				// max
				for ( int i = 0; i < maxDetections; ++i )
					listNew.add( list.get( i ) );
			}
			else if ( maxDetectionsTypeIndex == 2 )
			{
				// min
				for ( int i = 0; i < maxDetections; ++i )
					listNew.add( list.get( list.size() - 1 - i ) );
			}
			else
			{
				// median
				final int median = list.size() / 2;

				IOFunctions.println( "Medium intensity: " + Math.abs( list.get( median ).getImgValue().get() ) );

				final int from = median - maxDetections/2;
				final int to = median + maxDetections/2;

				for ( int i = from; i <= to; ++i )
					listNew.add( list.get( list.size() - 1 - i ) );
			}

			return listNew;
		}
	}

	private static void sortDetections(final List<DifferenceOfGaussianPeak<FloatType>> list)
	{
		list.sort((o1, o2) -> {
			final double v1 = Math.abs(o1.getValue().get());
			final double v2 = Math.abs(o2.getValue().get());
			return Double.compare(v2, v1);
		});
	}


	@SuppressWarnings("unused")
    public static List< DifferenceOfGaussianPeak< FloatType > > thinOut( final List< DifferenceOfGaussianPeak< FloatType > > canvasPeaks, final double minDistance, final double maxDistance, final boolean keepRange )
    {
		// assemble the list of points (we need two lists as the KDTree sorts the list)
		// we assume that the order of list2 is preserved
		final List< RealPoint > list1 = new ArrayList<>();
		final List< RealPoint > list2 = new ArrayList<>();

		for ( final DifferenceOfGaussianPeak<FloatType> ip : canvasPeaks )
		{
			list1.add ( new RealPoint(
					ip.getSubPixelPosition( 0 ),
					ip.getSubPixelPosition( 1 ) ) );

			list2.add ( new RealPoint(
					ip.getSubPixelPosition( 0 ),
					ip.getSubPixelPosition( 1 ) ) );
		}

		// make the KDTree
		final KDTree< RealPoint > tree = new KDTree<>(list1, list1);

		// Nearest neighbor for each point, populate the new list
		final KNearestNeighborSearchOnKDTree< RealPoint > nn = new KNearestNeighborSearchOnKDTree<>(tree, 2);
		final List< DifferenceOfGaussianPeak< FloatType > > newIPs = new ArrayList<>();

		for ( int j = 0; j < list2.size(); ++j )
		{
			final RealPoint p = list2.get( j );
			nn.search( p );

			// first nearest neighbor is the point itself, we need the second nearest
			final double d = nn.getDistance( 1 );

			if ( ( keepRange && d >= minDistance && d <= maxDistance ) || ( !keepRange && ( d < minDistance || d > maxDistance ) ) )
				newIPs.add( canvasPeaks.get( j ) );
		}

		return newIPs;
    }

	@SuppressWarnings("unused")
	static Pair< ArrayList< Point >, ArrayList< Point > > adjustInliers( final List<PointMatch> inliers, final double scaleSIFT, final double scaleGeo )
	{
		final ArrayList< Point > sourcePoints = new ArrayList<>();
		final ArrayList< Point > targetPoints = new ArrayList<>();

		PointMatch.sourcePoints( inliers, sourcePoints );
		PointMatch.targetPoints( inliers, targetPoints );

		// TODO: do not ignore world coordinates
		for ( final Point p : sourcePoints )
		{
			p.getL()[ 0 ] = (p.getL()[ 0 ] / scaleSIFT) * scaleGeo;
			p.getL()[ 1 ] = (p.getL()[ 1 ] / scaleSIFT) * scaleGeo;
		}

		// TODO: do not ignore world coordinates
		for ( final Point p : targetPoints )
		{
			p.getL()[ 0 ] = (p.getL()[ 0 ] / scaleSIFT) * scaleGeo;
			p.getL()[ 1 ] = (p.getL()[ 1 ] / scaleSIFT) * scaleGeo;
		}

		return new ValuePair<>(sourcePoints, targetPoints );
	}

	private static final Logger LOG = LoggerFactory.getLogger(GeometricDescriptorSIFTMatcherTest.class);
}
