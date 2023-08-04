package org.janelia.render.client.newsolver.blocksolveparameters;

import java.util.List;
import java.util.function.Function;

import mpicbg.models.Affine2D;
import mpicbg.models.Model;

/**
 * 
 * @author preibischs
 *
 * @param <B> the final block solve type (the result)
 * @param <S> the stitching-first type
 */
public class FIBSEMAlignmentParameters< M extends Model< M > & Affine2D< M >, S extends Model< S > & Affine2D< S > > extends BlockDataSolveParameters< M, FIBSEMAlignmentParameters< M, S> >
{
	private static final long serialVersionUID = 4247180309556813829L;
	public enum PreAlign { NONE, TRANSLATION, RIGID }

	final private Function< Integer, S > stitchingModelSupplier;
	final private Function< Integer, Integer > minStitchingInliersSupplier; // if it is less, it is not stitched first

	final double maxAllowedErrorStitching;
	final int maxIterationsStitching;
	final int maxPlateauWidthStitching;

	final private List<Double> blockOptimizerLambdasRigid;
	final private List<Double> blockOptimizerLambdasTranslation;
	final private List<Integer> blockOptimizerIterations;
	final private List<Integer> blockMaxPlateauWidth;

	final int preAlignOrdinal; // storing the ordinal of the enum for serialization purposes
	final private double blockMaxAllowedError;

	final String matchOwner;
	final String matchCollection;
	final int maxNumMatches;
	final int maxZRangeMatches;

	/**
	 * 
	 * @param blockSolveModel - result model
	 * @param stitchingModelSupplier - returns the stitching model as a function of z
	 * @param maxAllowedErrorStitching - max error of stitching round for optimizer
	 * @param maxIterationsStitching - max iterations of stitching round for optimizer
	 * @param maxPlateauWidthStitching - max plateau width of stitching round for optimizer
	 * @param minStitchingInliersSupplier - returns minNumStitchingInliers as a function of z (if smaller no stitching first)
	 * @param blockOptimizerLambdasRigid - list of lambdas for rigid regularizer for optimizer
	 * @param blockOptimizerLambdasTranslation - list of lambdas for translation regularizer for optimizer
	 * @param blockOptimizerIterations - list of max num iterations for optimizer
	 * @param blockMaxPlateauWidth - list of max plateau width for optimizer
	 * @param blockMaxAllowedError - max error for optimizer
	 * @param maxNumMatches - maximal number of matches between two tiles -- will randomly be reduced if above (default: 0 - no limit)
	 * @param maxZRangeMatches - max z-range in which to load matches (default: '-1' - no limit)
	 * @param preAlign - if and how to pre-align the stack
	 * @param baseDataUrl - render url
	 * @param owner - render owner
	 * @param project - render project
	 * @param stack - render stack
	 * @param matchOwner - render match owner
	 * @param matchCollection - render match collection
	 */
	public FIBSEMAlignmentParameters(
			final M blockSolveModel,
			final Function< Integer, S > stitchingModelSupplier,
			final Function< Integer, Integer > minStitchingInliersSupplier,
			final double maxAllowedErrorStitching,
			final int maxIterationsStitching,
			final int maxPlateauWidthStitching,
			final List<Double> blockOptimizerLambdasRigid,
			final List<Double> blockOptimizerLambdasTranslation,
			final List<Integer> blockOptimizerIterations,
			final List<Integer> blockMaxPlateauWidth,
			final double blockMaxAllowedError,
			final int maxNumMatches,
			final int maxZRangeMatches,
			final PreAlign preAlign,
			final String baseDataUrl,
			final String owner,
			final String project,
			final String stack,
			final String matchOwner,
			final String matchCollection )
	{
		super(baseDataUrl, owner, project, stack, blockSolveModel.copy());

		this.stitchingModelSupplier = stitchingModelSupplier;
		this.minStitchingInliersSupplier = minStitchingInliersSupplier;
		this.maxAllowedErrorStitching = maxAllowedErrorStitching;
		this.maxIterationsStitching = maxIterationsStitching;
		this.maxPlateauWidthStitching = maxPlateauWidthStitching;
		this.blockOptimizerLambdasRigid = blockOptimizerLambdasRigid;
		this.blockOptimizerLambdasTranslation = blockOptimizerLambdasTranslation;
		this.blockOptimizerIterations = blockOptimizerIterations;
		this.blockMaxPlateauWidth = blockMaxPlateauWidth;
		this.blockMaxAllowedError = blockMaxAllowedError;
		this.preAlignOrdinal = preAlign.ordinal();
		this.maxNumMatches = maxNumMatches;
		this.maxZRangeMatches = maxZRangeMatches;
		this.matchOwner = matchOwner;
		this.matchCollection = matchCollection;
	}

	@Override
	public M blockSolveModel() { return blockSolveModel().copy(); }
	public S stitchingSolveModelInstance( final int z ) { return stitchingModelSupplier.apply( z ); }
	public Function< Integer, S > stitchingModelSupplier() { return stitchingModelSupplier; }

	public Function< Integer, Integer > minStitchingInliersSupplier() { return minStitchingInliersSupplier; }
	//public int minStitchingInliers( final int z ) { return minStitchingInliersSupplier.apply( z ); }

	public List<Double> blockOptimizerLambdasRigid() { return blockOptimizerLambdasRigid; }
	public List<Double> blockOptimizerLambdasTranslation() { return blockOptimizerLambdasTranslation; }
	public List<Integer> blockOptimizerIterations() { return blockOptimizerIterations; }
	public List<Integer> blockMaxPlateauWidth() {return blockMaxPlateauWidth; }
	public double blockMaxAllowedError() { return blockMaxAllowedError; }
	public PreAlign preAlign() { return PreAlign.values()[ preAlignOrdinal ]; }

	public int maxNumMatches() { return maxNumMatches; }
	public int maxZRangeMatches() { return maxZRangeMatches; }
	public String matchOwner() { return matchOwner; }
	public String matchCollection() { return matchCollection; }

	public double maxAllowedErrorStitching() { return maxAllowedErrorStitching; }
	public int maxIterationsStitching() { return maxIterationsStitching; }
	public int maxPlateauWidthStitching() { return maxPlateauWidthStitching; }

	@Override
	public FIBSEMAlignmentParameters<M, S> createInstance( final boolean hasIssue )
	{
		// creates a new set of parameters for a specific block

		// see e.g. org.janelia.render.client.solver.custom.SolveSetFactoryOcellar

		// TODO Auto-generated method stub
		return this;
	}

}