package org.janelia.render.client.newsolver.assembly.matches;

import java.util.List;

import org.janelia.alignment.spec.TileSpec;
import org.janelia.render.client.newsolver.BlockData;

import mpicbg.models.PointMatch;

public interface SameTileMatchCreator< R >
{
	/**
	 * Add a reasonable number of matches to the provided List that map the same TileSpec from one BlockData to another
	 *
	 * @param tileSpec - the TileSpec of the common TileSpec for which we have two models
	 * @param modelA - model of BlockData A, needs to be applied to p
	 * @param modelB - model of BlockData A, needs to be applied to q
	 * @param blockContextA - the block that is queried for context data
	 * @param blockContextB - the block that is queried for context data
	 * @param matchesAtoB - list to add the PointMatches to
	 */
	public void addMatches(
			TileSpec tileSpec,
			R modelA,
			R modelB,
			BlockData<?, R, ?, ?> blockContextA,
			BlockData<?, R, ?, ?> blockContextB,
			List< PointMatch > matchesAtoB );

	
	// TODO: IMPORTANT: you need both blocks, blockA for modelA and blockB for modelB, no (see above)
	// TODO: that would also get rid of the method below that is just needed for some implementations and seems incomplete
	/*
	 * Set the block context in which the matches are created - this is required for the intensity based match creator
	 * @param blockContext - the block that is queried for context data
	 */
	//void setBlockContext(BlockData<?, ?, ?, ?> blockContext);
}