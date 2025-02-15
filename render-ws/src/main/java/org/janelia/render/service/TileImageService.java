package org.janelia.render.service;

import java.net.UnknownHostException;
import java.util.Set;

import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import org.janelia.alignment.RenderParameters;
import org.janelia.alignment.spec.stack.StackId;
import org.janelia.alignment.spec.stack.StackMetaData;
import org.janelia.render.service.model.RenderQueryParameters;
import org.janelia.render.service.util.RenderServiceUtil;
import org.janelia.render.service.util.ResponseHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Tile centric APIs for rendering images.
 *
 * @author Eric Trautman
 */
@Path("/")
@Api(tags = {"Tile Image APIs"})
public class TileImageService {

    private final RenderDataService renderDataService;
    private final TileDataService tileDataService;

    @SuppressWarnings("UnusedDeclaration")
    public TileImageService()
            throws UnknownHostException {
        this(new RenderDataService(),
             new TileDataService());
    }

    private TileImageService(final RenderDataService renderDataService,
                             final TileDataService tileDataService) {
        this.renderDataService = renderDataService;
        this.tileDataService = tileDataService;
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/tile/{tileId}/jpeg-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_JPEG_MIME_TYPE)
    @ApiOperation(value = "Render JPEG image for a tile")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Tile not found")
    })
    public Response renderJpegImageForTile(@PathParam("owner") final String owner,
                                           @PathParam("project") final String project,
                                           @PathParam("stack") final String stack,
                                           @PathParam("tileId") final String tileId,
                                           @BeanParam final RenderQueryParameters renderQueryParameters,
                                           @QueryParam("x") final Integer xOffset,
                                           @QueryParam("y") final Integer yOffset,
                                           @QueryParam("width") final Integer fullScaleWidth,
                                           @QueryParam("height") final Integer fullScaleHeight,
                                           @QueryParam("normalizeForMatching") final Boolean normalizeForMatching,
                                           @QueryParam("excludeTransformsAfterLast") final Set<String> excludeAfterLastLabels,
                                           @QueryParam("excludeFirstTransformAndAllAfter") final Set<String> excludeFirstAndAllAfterLabels,
                                           @QueryParam("excludeAllTransforms") final Boolean excludeAllTransforms,
                                           @Context final Request request) {

        LOG.info("renderJpegImageForTile: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    tileDataService.getRenderParameters(owner, project, stack, tileId, renderQueryParameters,
                                                        xOffset, yOffset, fullScaleWidth, fullScaleHeight,
                                                        normalizeForMatching,
                                                        excludeAfterLastLabels, excludeFirstAndAllAfterLabels,
                                                        excludeAllTransforms);
            return RenderServiceUtil.renderJpegImage(renderParameters, null, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/tile/{tileId}/png-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_PNG_MIME_TYPE)
    @ApiOperation(value = "Render PNG image for a tile")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Tile not found")
    })
    public Response renderPngImageForTile(@PathParam("owner") final String owner,
                                          @PathParam("project") final String project,
                                          @PathParam("stack") final String stack,
                                          @PathParam("tileId") final String tileId,
                                          @BeanParam final RenderQueryParameters renderQueryParameters,
                                          @QueryParam("x") final Integer xOffset,
                                          @QueryParam("y") final Integer yOffset,
                                          @QueryParam("width") final Integer fullScaleWidth,
                                          @QueryParam("height") final Integer fullScaleHeight,
                                          @QueryParam("normalizeForMatching") final Boolean normalizeForMatching,
                                          @QueryParam("excludeTransformsAfterLast") final Set<String> excludeAfterLastLabels,
                                          @QueryParam("excludeFirstTransformAndAllAfter") final Set<String> excludeFirstAndAllAfterLabels,
                                          @QueryParam("excludeAllTransforms") final Boolean excludeAllTransforms,
                                          @Context final Request request) {

        LOG.info("renderPngImageForTile: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    tileDataService.getRenderParameters(owner, project, stack, tileId, renderQueryParameters,
                                                        xOffset, yOffset, fullScaleWidth, fullScaleHeight,
                                                        normalizeForMatching,
                                                        excludeAfterLastLabels, excludeFirstAndAllAfterLabels,
                                                        excludeAllTransforms);
            return RenderServiceUtil.renderPngImage(renderParameters, null, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/tile/{tileId}/tiff-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_TIFF_MIME_TYPE)
    @ApiOperation(value = "Render TIFF image for a tile")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Tile not found")
    })
    public Response renderTiffImageForTile(@PathParam("owner") final String owner,
                                           @PathParam("project") final String project,
                                           @PathParam("stack") final String stack,
                                           @PathParam("tileId") final String tileId,
                                           @BeanParam final RenderQueryParameters renderQueryParameters,
                                           @QueryParam("x") final Integer xOffset,
                                           @QueryParam("y") final Integer yOffset,
                                           @QueryParam("width") final Integer fullScaleWidth,
                                           @QueryParam("height") final Integer fullScaleHeight,
                                           @QueryParam("normalizeForMatching") final Boolean normalizeForMatching,
                                           @QueryParam("excludeTransformsAfterLast") final Set<String> excludeAfterLastLabels,
                                           @QueryParam("excludeFirstTransformAndAllAfter") final Set<String> excludeFirstAndAllAfterLabels,
                                           @QueryParam("excludeAllTransforms") final Boolean excludeAllTransforms,
                                           @Context final Request request) {

        LOG.info("renderTiffImageForTile: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    tileDataService.getRenderParameters(owner, project, stack, tileId, renderQueryParameters,
                                                        xOffset, yOffset, fullScaleWidth, fullScaleHeight,
                                                        normalizeForMatching,
                                                        excludeAfterLastLabels, excludeFirstAndAllAfterLabels,
                                                        excludeAllTransforms);
            return RenderServiceUtil.renderTiffImage(renderParameters, null, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/tile/{tileId}/tiff16-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_TIFF_MIME_TYPE)
    @ApiOperation(value = "Render TIFF image for a tile")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Tile not found")
    })
    public Response renderTiff16ImageForTile(@PathParam("owner") final String owner,
                                             @PathParam("project") final String project,
                                             @PathParam("stack") final String stack,
                                             @PathParam("tileId") final String tileId,
                                             @BeanParam final RenderQueryParameters renderQueryParameters,
                                             @QueryParam("x") final Integer xOffset,
                                             @QueryParam("y") final Integer yOffset,
                                             @QueryParam("width") final Integer fullScaleWidth,
                                             @QueryParam("height") final Integer fullScaleHeight,
                                             @QueryParam("normalizeForMatching") final Boolean normalizeForMatching,
                                             @QueryParam("excludeTransformsAfterLast") final Set<String> excludeAfterLastLabels,
                                             @QueryParam("excludeFirstTransformAndAllAfter") final Set<String> excludeFirstAndAllAfterLabels,
                                             @QueryParam("excludeAllTransforms") final Boolean excludeAllTransforms,
                                             @Context final Request request) {

        LOG.info("renderTiffImageForTile: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    tileDataService.getRenderParameters(owner, project, stack, tileId, renderQueryParameters,
                                                        xOffset, yOffset, fullScaleWidth, fullScaleHeight,
                                                        normalizeForMatching,
                                                        excludeAfterLastLabels, excludeFirstAndAllAfterLabels,
                                                        excludeAllTransforms);
            return RenderServiceUtil.renderTiffImage(renderParameters, null, responseHelper, true);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/tile/{tileId}/source/jpeg-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_JPEG_MIME_TYPE)
    @ApiOperation(value = "Render tile's source image without transformations in JPEG format")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Tile not found")
    })
    public Response renderJpegSourceImageForTile(@PathParam("owner") final String owner,
                                                 @PathParam("project") final String project,
                                                 @PathParam("stack") final String stack,
                                                 @PathParam("tileId") final String tileId,
                                                 @BeanParam final RenderQueryParameters renderQueryParameters,
                                                 @Context final Request request) {

        LOG.info("renderJpegSourceImageForTile: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    tileDataService.getTileSourceRenderParameters(owner, project, stack, tileId, null,
                                                                  renderQueryParameters);
            return RenderServiceUtil.renderJpegImage(renderParameters, null, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/tile/{tileId}/source/png-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_PNG_MIME_TYPE)
    @ApiOperation(value = "Render tile's source image without transformations in PNG format")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Tile not found")
    })
    public Response renderPngSourceImageForTile(@PathParam("owner") final String owner,
                                                @PathParam("project") final String project,
                                                @PathParam("stack") final String stack,
                                                @PathParam("tileId") final String tileId,
                                                @BeanParam final RenderQueryParameters renderQueryParameters,
                                                @Context final Request request) {

        LOG.info("renderPngSourceImageForTile: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    tileDataService.getTileSourceRenderParameters(owner, project, stack, tileId, null,
                                                                  renderQueryParameters);
            return RenderServiceUtil.renderPngImage(renderParameters, null, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/tile/{tileId}/source/tiff-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_TIFF_MIME_TYPE)
    @ApiOperation(value = "Render tile's source image without transformations in TIFF format")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Tile not found")
    })
    public Response renderTiffSourceImageForTile(@PathParam("owner") final String owner,
                                                 @PathParam("project") final String project,
                                                 @PathParam("stack") final String stack,
                                                 @PathParam("tileId") final String tileId,
                                                 @BeanParam final RenderQueryParameters renderQueryParameters,
                                                 @Context final Request request) {

        LOG.info("renderTiffSourceImageForTile: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    tileDataService.getTileSourceRenderParameters(owner, project, stack, tileId, null,
                                                                  renderQueryParameters);
            return RenderServiceUtil.renderTiffImage(renderParameters, null, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/tile/{tileId}/mask/jpeg-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_JPEG_MIME_TYPE)
    @ApiOperation(value = "Render tile's mask image without transformations in JPEG format")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Tile not found")
    })
    public Response renderJpegMaskImageForTile(@PathParam("owner") final String owner,
                                               @PathParam("project") final String project,
                                               @PathParam("stack") final String stack,
                                               @PathParam("tileId") final String tileId,
                                               @BeanParam final RenderQueryParameters renderQueryParameters,
                                               @Context final Request request) {

        LOG.info("renderJpegMaskImageForTile: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    tileDataService.getTileMaskRenderParameters(owner, project, stack, tileId, null,
                                                                renderQueryParameters);
            return RenderServiceUtil.renderJpegImage(renderParameters, null, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/tile/{tileId}/mask/png-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_PNG_MIME_TYPE)
    @ApiOperation(value = "Render tile's mask image without transformations in PNG format")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Tile not found")
    })
    public Response renderPngMaskImageForTile(@PathParam("owner") final String owner,
                                              @PathParam("project") final String project,
                                              @PathParam("stack") final String stack,
                                              @PathParam("tileId") final String tileId,
                                              @BeanParam final RenderQueryParameters renderQueryParameters,
                                              @Context final Request request) {

        LOG.info("renderPngMaskImageForTile: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    tileDataService.getTileMaskRenderParameters(owner, project, stack, tileId, null,
                                                                renderQueryParameters);
            return RenderServiceUtil.renderPngImage(renderParameters, null, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/tile/{tileId}/mask/tiff-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_TIFF_MIME_TYPE)
    @ApiOperation(value = "Render tile's mask image without transformations in TIFF format")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Tile not found")
    })
    public Response renderTiffMaskImageForTile(@PathParam("owner") final String owner,
                                               @PathParam("project") final String project,
                                               @PathParam("stack") final String stack,
                                               @PathParam("tileId") final String tileId,
                                               @BeanParam final RenderQueryParameters renderQueryParameters,
                                               @Context final Request request) {

        LOG.info("renderTiffMaskImageForTile: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    tileDataService.getTileMaskRenderParameters(owner, project, stack, tileId, null,
                                                                renderQueryParameters);
            return RenderServiceUtil.renderTiffImage(renderParameters, null, responseHelper);
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    @Path("v1/owner/{owner}/project/{project}/stack/{stack}/tile/{tileId}/withNeighbors/jpeg-image")
    @GET
    @Produces(RenderServiceUtil.IMAGE_JPEG_MIME_TYPE)
    @ApiOperation(value = "Render tile with its neighbors in JPEG format")
    @ApiResponses(value = {
            @ApiResponse(code = 404, message = "Tile not found")
    })
    public Response renderJpegTileWithNeighborsImage(@PathParam("owner") final String owner,
                                                     @PathParam("project") final String project,
                                                     @PathParam("stack") final String stack,
                                                     @PathParam("tileId") final String tileId,
                                                     @BeanParam final RenderQueryParameters renderQueryParameters,
                                                     @QueryParam("widthFactor") final Double widthFactor,
                                                     @QueryParam("heightFactor") final Double heightFactor,
                                                     @QueryParam("boundingBoxesOnly") final Boolean boundingBoxesOnly,
                                                     @Context final Request request) {

        LOG.info("renderJpegTileWithNeighborsImage: entry, owner={}, project={}, stack={}, tileId={}",
                 owner, project, stack, tileId);

        final ResponseHelper responseHelper = new ResponseHelper(request, getStackMetaData(owner, project, stack));
        if (responseHelper.isModified()) {
            final RenderParameters renderParameters =
                    tileDataService.getTileWithNeighborsRenderParameters(owner, project, stack, tileId,
                                                                         widthFactor, heightFactor,
                                                                         renderQueryParameters);
            if ((boundingBoxesOnly != null) && boundingBoxesOnly) {
                return RenderServiceUtil.renderJpegBoundingBoxes(renderParameters, responseHelper);
            } else {
                return RenderServiceUtil.renderJpegImage(renderParameters, null, responseHelper);
            }
        } else {
            return responseHelper.getNotModifiedResponse();
        }
    }

    private StackMetaData getStackMetaData(final String owner,
                                           final String project,
                                           final String stack) {
        final StackId stackId = new StackId(owner, project, stack);
        return renderDataService.getStackMetaData(stackId);
    }

    private static final Logger LOG = LoggerFactory.getLogger(TileImageService.class);
}
