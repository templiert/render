package org.janelia.render.client.multisem;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import mpicbg.models.RigidModel2D;

import org.janelia.alignment.match.CanvasId;
import org.janelia.alignment.match.CanvasMatchResult;
import org.janelia.alignment.match.CanvasMatches;
import org.janelia.alignment.match.OrderedCanvasIdPair;
import org.janelia.alignment.match.RenderableCanvasIdPairs;
import org.janelia.alignment.match.parameters.FeatureStorageParameters;
import org.janelia.alignment.spec.ResolvedTileSpecCollection;
import org.janelia.alignment.spec.TileSpec;
import org.janelia.alignment.util.FileUtil;
import org.janelia.render.client.ClientRunner;
import org.janelia.render.client.MultiStagePointMatchClient;
import org.janelia.render.client.RenderDataClient;
import org.janelia.render.client.parameter.CommandLineParameters;
import org.janelia.render.client.parameter.MatchWebServiceParameters;
import org.janelia.render.client.parameter.RenderWebServiceParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.janelia.alignment.match.RenderableCanvasIdPairs.TEMPLATE_GROUP_ID_TOKEN;

/**
 * Java client for patching matches missing between the same MFOV in adjacent (cross) z layers.
 * <br/><br/>
 * The client:
 * <ul>
 *   <li>
 *       Finds adjacent z (cross) layer MFOVs that do not contain enough connected SFOV tile pairs
 *       after standard matching where "enough" is specified by the --min_pairs_for_connection parameter.
 *   </li>
 *   <li>
 *       For each unconnected cross MFOV pair, renders the stitched MFOVs and generates matches between them.
 *       The hope is that enough matches can be found when comparing scaled views of entire MFOVs.
 *       Note that the rendered MFOVs should be stitched in isolation, meaning that the SFOV tiles
 *       in each MFOV contain transforms solely derived from matches with SFOV tiles in the same MFOV.
 *   </li>
 *   <li>
 *       Transforms the MFOV pair matches into SFOV tile matches for one or more SFOV tile pairs.
 *       Most (if not all) of the transformed match point locations will be outside the bounds of the SFOV tiles,
 *       but that does not matter to solvers.  It simply means that the match points may not be visible in some
 *       tile pair views since match points are typically within the bounds of the tile.
 *   </li>
 *       Stores the transformed matches with a specified weight (typically something like 0.99 to help
 *       differentiate patch matches from standard matches which have a weight of 1.0).
 *   </li>
 * </ul>
 *
 * @author Stephan Preibisch
 * @author Eric Trautman
 */
public class MFOVCrossMatchPatchClient {

    public static class Parameters
            extends CommandLineParameters {

        @ParametersDelegate
        public RenderWebServiceParameters renderWeb = new RenderWebServiceParameters();

        @Parameter(
                names = "--stack",
                description = "Stack to process",
                variableArity = true,
                required = true)
        public String stack;

        @Parameter(
                names = "--mfov",
                description = "Multi-field-of-view identifier <slab number>_<mFOV number> (e.g. 001_000006).  " +
                              "If specified, only generate cross matches for this mFOV.  " +
                              "Omit to generate for all mFOVs ",
                variableArity = true
        )
        public List<String> mFOVList;

        @Parameter(
                names = "--sfov",
                description = "Single-field-of-view identifier <slab number>_<mfov number>_<sfov number> " +
                              "(e.g. 001_000006_026).  " +
                              "If specified, only generate cross matches for this sFOV " +
                              "and use transformed mFOV points instead of using mFOV model with center points.  " +
                              "Omit to generate for all sFOVs using mFOV model with center points.",
                variableArity = true
        )
        public List<String> sFOVList;

        @Parameter(
                names = "--matchOwner",
                description = "Match collection owner (default is to use stack owner)")
        public String matchOwner;

        @Parameter(
                names = "--matchCollection",
                description = "Match collection with unconnected MFOV tile pairs",
                required = true)
        public String matchCollection;

        @Parameter(
                names = "--matchStorageCollection",
                description = "Collection for storage of derived matches (omit to store to source collection)")
        public String matchStorageCollection;

        @Parameter(
                names = "--stored_match_weight",
                description = "Weight for stored matches (e.g. 0.0001)",
                required = true)
        public Double storedMatchWeight;

        @Parameter(
                names = "--z",
                description = "Z value of p layer to be matched (omit to process all layers)",
                variableArity = true,
                required = true)
        public List<Double> zValues;

        @ParametersDelegate
        FeatureStorageParameters featureStorage = new FeatureStorageParameters();

        @Parameter(
                names = "--stageJson",
                description = "JSON file where stage match parameters are defined",
                required = true)
        public String stageJson;

        @Parameter(
                names = "--matchStorageFile",
                description = "File to store matches (omit if matches should be stored through web service)"
        )
        public String matchStorageFile;

        private MultiStagePointMatchClient.Parameters matchDerivationClientParameters;
        private Set<String> sFOVSet;

        public Parameters() {
        }

        public String getMatchOwner() {
            return matchOwner == null ? renderWeb.owner : matchOwner;
        }

        public Path getStoragePath(final Double z,
                                   final String mFOVId) {
            final Path storagePath;
            if (matchStorageFile == null) {
                storagePath = null;
            } else {
                final int lastDot = matchStorageFile.lastIndexOf('.');
                String prefix = matchStorageFile;
                String suffix = "_" + z + "_" + mFOVId;
                if (lastDot > -1) {
                    prefix = matchStorageFile.substring(0, lastDot);
                    suffix = suffix + matchStorageFile.substring(lastDot);
                }
                storagePath = Paths.get(prefix + suffix).toAbsolutePath();
            }
            return storagePath;
        }

        public void validateAndSetupDerivedValues()
                throws IllegalArgumentException {

            final MatchWebServiceParameters matchWebServiceParameters = new MatchWebServiceParameters();
            matchWebServiceParameters.baseDataUrl = renderWeb.baseDataUrl;
            matchWebServiceParameters.owner = getMatchOwner();
            matchWebServiceParameters.collection = matchCollection;

            matchDerivationClientParameters =
                    new MultiStagePointMatchClient.Parameters(matchWebServiceParameters,
                                                              featureStorage,
                                                              2,
                                                              false,
                                                              null,
                                                              stageJson,
                                                              null);

            if (matchStorageFile != null) {
                Utilities.validateMatchStorageLocation(matchStorageFile);
            }

            if (sFOVList == null) {
                sFOVList = new ArrayList<>();
                if (mFOVList == null) {
                    mFOVList = new ArrayList<>();
                }
            } else if (mFOVList == null) {
                // remove duplicates and sort sFOV list
                sFOVList = sFOVList.stream().distinct().sorted().collect(Collectors.toList());
                // build mFOV list based upon sFOVs
                mFOVList = sFOVList.stream().map(Utilities::getMFOVForTileId).distinct().collect(Collectors.toList());
            } else {
                throw new IllegalArgumentException("must specify either --mfov or --sfov but not both");
            }

            sFOVSet = new HashSet<>(sFOVList);
        }
    }

    public static void main(final String[] args) {

        final ClientRunner clientRunner = new ClientRunner(args) {
            @Override
            public void runClient(final String[] args)
                    throws Exception {

                final Parameters parameters = new Parameters();
                parameters.parse(args);
                parameters.validateAndSetupDerivedValues();

                LOG.info("runClient: entry, parameters={}", parameters);

                final MFOVCrossMatchPatchClient client = new MFOVCrossMatchPatchClient(parameters);
                client.deriveAndSaveMatchesForUnconnectedMFOVs();
            }
        };
        clientRunner.run();
    }

    private final Parameters parameters;
    private final RenderDataClient renderDataClient;
    private final RenderDataClient matchClient;
    private final RenderDataClient matchStorageClient;
    private final MultiStagePointMatchClient multiStagePointMatchClient;
    private final boolean matchCollectionExists;

    private ResolvedTileSpecCollection pResolvedTiles;
    private ResolvedTileSpecCollection qResolvedTiles;
    private List<OrderedCanvasIdPair> unconnectedPairsForMFOV;

    MFOVCrossMatchPatchClient(final Parameters parameters)
            throws IOException {
        this.parameters = parameters;
        this.renderDataClient = parameters.renderWeb.getDataClient();
        this.matchClient = new RenderDataClient(parameters.renderWeb.baseDataUrl,
                                                parameters.getMatchOwner(),
                                                parameters.matchCollection);
        if (parameters.matchStorageCollection == null) {
            this.matchStorageClient = this.matchClient;
        } else {
            this.matchStorageClient = new RenderDataClient(parameters.renderWeb.baseDataUrl,
                                                           parameters.getMatchOwner(),
                                                           parameters.matchStorageCollection);
        }
        this.multiStagePointMatchClient = new MultiStagePointMatchClient(parameters.matchDerivationClientParameters);

        final Set<String> exitingCollectionNames =
                this.matchClient.getOwnerMatchCollections().stream()
                        .map(d -> d.getCollectionId().getName())
                        .collect(Collectors.toSet());

        this.matchCollectionExists = exitingCollectionNames.contains(parameters.matchCollection);

        if (! this.matchCollectionExists) {
            LOG.info("match collection {} does not exist so all pairs will be considered unconnected",
                     parameters.matchCollection);
        }
    }

    public void deriveAndSaveMatchesForUnconnectedMFOVs()
            throws IOException {

        LOG.info("deriveAndSaveMatchesForUnconnectedMFOVs: entry");

        final Map<Double, Set<String>> zToSectionIdsMap =
                renderDataClient.getStackZToSectionIdsMap(parameters.stack,
                                                          null,
                                                          null,
                                                          parameters.zValues);

        for (final Double z : zToSectionIdsMap.keySet().stream().sorted().collect(Collectors.toList())) {
            deriveAndSaveMatchesForUnconnectedPairs(z);
        }

        LOG.info("deriveAndSaveMatchesForUnconnectedMFOVs: exit");
    }

    public void deriveAndSaveMatchesForUnconnectedPairs(final Double pZ)
            throws IOException {

        LOG.info("deriveAndSaveMatchesForUnconnectedPairs: entry, pZ={}", pZ);

        final Double deltaZ = 1.0;
        final Double qZ = pZ + deltaZ;

        final String pGroupId = pZ.toString();
        final String qGroupId = qZ.toString();

        loadResolvedTiles(pZ, qZ);

        for (final String mFOVId : getMFOVIdList(pZ)) {

            buildUnconnectedData(pZ, qZ, mFOVId);

            if (unconnectedPairsForMFOV.size() > 0) {
                final String urlTemplateString =
                        "{baseDataUrl}/owner/" + parameters.renderWeb.owner + "/project/" +
                        parameters.renderWeb.project +
                        "/stack/" + parameters.stack + "/z/" + TEMPLATE_GROUP_ID_TOKEN +
                        "/render-parameters?tileIdPattern=" +
                        // note: prepend ^ to tileIdPattern to ensure only beginning of each tileId is matched for MFOV
                        URLEncoder.encode("^", StandardCharsets.UTF_8.toString()) + mFOVId;

                final OrderedCanvasIdPair mFOVPair =
                        new OrderedCanvasIdPair(new CanvasId(pGroupId, "mFOV_" + mFOVId + "." + pGroupId),
                                                new CanvasId(qGroupId, "mFOV_" + mFOVId + "." + qGroupId),
                                                deltaZ);

                final RenderableCanvasIdPairs renderableCanvasIdPairs =
                        new RenderableCanvasIdPairs(urlTemplateString,
                                                    Collections.singletonList(mFOVPair));
                final List<CanvasMatches> mFOVMatchesList =
                        multiStagePointMatchClient.generateMatchesForPairs(renderableCanvasIdPairs);

                if (mFOVMatchesList.size() == 1) {

                    final List<PointMatch> mFOVMatches =
                            CanvasMatchResult.convertMatchesToPointMatchList(mFOVMatchesList.get(0).getMatches());

                    final List<CanvasMatches> sFOVMatchesList;
                    if (parameters.sFOVList == null) {
                        sFOVMatchesList = buildMatchesUsingModel(mFOVPair, mFOVMatches);
                    } else {
                        sFOVMatchesList = buildMatchesUsingInvertedTransform(mFOVMatches);
                    }

                    if (sFOVMatchesList.size() > 0) {
                        LOG.info("deriveAndSaveMatchesForUnconnectedPairs: saving matches for {} z {} mFOV {} pairs",
                                 sFOVMatchesList.size(), pZ, mFOVId);

                        if (parameters.matchStorageFile != null) {
                            final Path storagePath = parameters.getStoragePath(pZ, mFOVId);
                            FileUtil.saveJsonFile(storagePath.toString(), sFOVMatchesList);
                        } else {
                            matchStorageClient.saveMatches(sFOVMatchesList);
                        }
                    } else {
                        LOG.warn("deriveAndSaveMatchesForUnconnectedPairs: no sFOV matches derived for z {} and mFOV {}",
                                 pZ, mFOVId);
                    }

                } else {
                    LOG.warn("deriveAndSaveMatchesForUnconnectedPairs: no mFOV matches derived for z {} and mFOV {}",
                             pZ, mFOVId);
                }

            }
        }

        LOG.info("deriveAndSaveMatchesForUnconnectedPairs: exit");
    }

    public void loadResolvedTiles(final Double pZ,
                                  final Double qZ)
            throws IOException {

        LOG.info("loadResolvedTiles: entry, pZ={}, qZ={}", pZ, qZ);

        // typically step through z sequentially, so if possible save request by moving previous q to p
        if ((qResolvedTiles != null) &&
            pZ.equals(qResolvedTiles.getTileSpecs().stream().findFirst().orElse(new TileSpec()).getZ())) {
            pResolvedTiles = qResolvedTiles;
        } else {
            pResolvedTiles = renderDataClient.getResolvedTiles(parameters.stack, pZ);
        }

        qResolvedTiles = renderDataClient.getResolvedTiles(parameters.stack, qZ);

        LOG.info("loadResolvedTiles: exit");
    }

    public void buildUnconnectedData(final Double pZ,
                                     final Double qZ,
                                     final String mFOVId)
            throws IOException {

        LOG.info("buildUnconnectedData: entry, pZ={}, qZ={}, mFOVId={}", pZ, qZ, mFOVId);

        final Map<String, TileSpec> pSFOVToTileSpec = Utilities.mapMFOVTilesToSFOVIds(pResolvedTiles.getTileSpecs(),
                                                                                      mFOVId);
        final Map<String, TileSpec> qSFOVToTileSpec = Utilities.mapMFOVTilesToSFOVIds(qResolvedTiles.getTileSpecs(),
                                                                                      mFOVId);

        final String pGroupId = pZ.toString();
        final String qGroupId = qZ.toString();
        final Double deltaZ = qZ - pZ;

        unconnectedPairsForMFOV = new ArrayList<>(pSFOVToTileSpec.size());
        for (final String SFOV : pSFOVToTileSpec.keySet()) {
            final TileSpec pTileSpec = pSFOVToTileSpec.get(SFOV);
            final TileSpec qTileSpec = qSFOVToTileSpec.get(SFOV);
            if (qTileSpec != null) {
                final OrderedCanvasIdPair pair = new OrderedCanvasIdPair(new CanvasId(pGroupId, pTileSpec.getTileId()),
                                                                         new CanvasId(qGroupId, qTileSpec.getTileId()),
                                                                         deltaZ);
                unconnectedPairsForMFOV.add(pair);
            }
        }

        // query web service to find connected tile pairs and remove them from unconnected set
        if ((unconnectedPairsForMFOV.size() > 0) && matchCollectionExists) {
            for (final CanvasMatches canvasMatches : matchClient.getMatchesBetweenGroups(pGroupId,
                                                                                         qGroupId,
                                                                                         true)) {
                final String pId = canvasMatches.getpId();
                final String qId = canvasMatches.getqId();
                if (pId.startsWith(mFOVId) && qId.startsWith(mFOVId)) {
                    final OrderedCanvasIdPair pair = new OrderedCanvasIdPair(new CanvasId(pGroupId, pId),
                                                                             new CanvasId(qGroupId, qId),
                                                                             deltaZ);
                    if (! unconnectedPairsForMFOV.remove(pair)) {
                        LOG.warn("buildUnconnectedData: failed to locate existing pair {}",
                                 pair);
                    }
                }
            }
        }

        LOG.info("buildUnconnectedData: exit, found {} unconnected tile pairs within mFOV {} in z {}",
                 unconnectedPairsForMFOV.size(), mFOVId, pZ);
    }

    private List<CanvasMatches> buildMatchesUsingModel(final OrderedCanvasIdPair mFOVPair,
                                                       final List<PointMatch> mFOVMatches)
            throws IOException {

        final List<CanvasMatches> sFOVMatchesList = new ArrayList<>();

        final List<OrderedCanvasIdPair> sortedUnconnectedPairs =
                unconnectedPairsForMFOV.stream().sorted().collect(Collectors.toList());

        LOG.info("buildMatchesUsingModel: mFOVMatches.size={}, sortedUnconnectedPairs.size={}",
                 mFOVMatches.size(), sortedUnconnectedPairs.size());

        final RigidModel2D mFOVModel = new RigidModel2D(); // TODO: derive model type from match parameters?
        Utilities.fitModelAndLogError(mFOVModel, mFOVMatches, "pair " + mFOVPair);

        final int cornerMargin = 700;
        for (final OrderedCanvasIdPair pair : sortedUnconnectedPairs) {
            final TileSpec pTileSpec = pResolvedTiles.getTileSpec(pair.getP().getId());
            final TileSpec qTileSpec = qResolvedTiles.getTileSpec(pair.getQ().getId());
            sFOVMatchesList.add(
                    Utilities.buildPointMatches(pair,
                                                Utilities.getMatchingTransformedCornersForTile(pTileSpec, cornerMargin),
                                                Utilities.getMatchingTransformedCornersForTile(qTileSpec, cornerMargin),
                                                mFOVModel,
                                                parameters.storedMatchWeight));
        }

        return sFOVMatchesList;
    }

    private List<CanvasMatches> buildMatchesUsingInvertedTransform(final List<PointMatch> mFOVMatches)
            throws IOException {

        final List<CanvasMatches> sFOVMatchesList = new ArrayList<>();

        final List<OrderedCanvasIdPair> sortedUnconnectedPairs =
                unconnectedPairsForMFOV.stream()
                        .filter(p -> {
                            final String sFOV = Utilities.getSFOVForTileId(p.getP().getId());
                            return parameters.sFOVSet.contains(sFOV);
                        })
                        .sorted()
                        .collect(Collectors.toList());

        LOG.info("buildMatchesUsingInvertedTransform: mFOVMatches.size={}, sortedUnconnectedPairs.size={}",
                 mFOVMatches.size(), sortedUnconnectedPairs.size());

        for (final OrderedCanvasIdPair sFOVPair : sortedUnconnectedPairs) {
            sFOVMatchesList.add(
                    buildMatchesUsingInvertedTransformForSFOVPair(mFOVMatches,
                                                                  sFOVPair,
                                                                  parameters.storedMatchWeight));
        }

        return sFOVMatchesList;
    }

    private CanvasMatches buildMatchesUsingInvertedTransformForSFOVPair(final List<PointMatch> mFOVMatches,
                                                                        final OrderedCanvasIdPair sFOVPair,
                                                                        final double derivedMatchWeight)
            throws IOException {

        final CanvasId p = sFOVPair.getP();
        final CanvasId q = sFOVPair.getQ();

        final TileSpec pTileSpec = pResolvedTiles.getTileSpec(p.getId());
        final TileSpec qTileSpec = qResolvedTiles.getTileSpec(q.getId());

        final List<Point> pPoints = Utilities.transformMFOVMatchesForTile(mFOVMatches, pTileSpec, true);
        final List<Point> qPoints = Utilities.transformMFOVMatchesForTile(mFOVMatches, qTileSpec, false);

        final List<PointMatch> matchList = new ArrayList<>(pPoints.size());
        for (int i = 0; i < pPoints.size(); i++) {
            final Point pPoint = pPoints.get(i);
            final Point qPoint = qPoints.get(i);
            if ((pPoint != null) && (qPoint != null)) {
                matchList.add(new PointMatch(pPoints.get(i), qPoints.get(i), derivedMatchWeight));
            }
        }

        if (matchList.size() == 0) {
            throw new IOException("unable to invert matches for sFOV pair " + sFOVPair);
        }

        return new CanvasMatches(p.getGroupId(),
                                 p.getId(),
                                 q.getGroupId(),
                                 q.getId(),
                                 CanvasMatchResult.convertPointMatchListToMatches(matchList,
                                                                                  1.0));
    }

    private List<String> getMFOVIdList(final Double z) {
        final List<String> mFOVList;
        if (parameters.mFOVList.size() == 0) {
            mFOVList = pResolvedTiles.getTileSpecs().stream()
                    .map(ts -> Utilities.getMFOVForTileId(ts.getTileId()))
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            LOG.info("getMFOVIdList: found {} distinct mFOVs in z {}",
                     mFOVList.size(), z);
        } else {
            mFOVList = parameters.mFOVList;
        }
        return mFOVList;
    }

    private static final Logger LOG = LoggerFactory.getLogger(MFOVCrossMatchPatchClient.class);
}