package org.janelia.alignment.spec;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

import org.janelia.alignment.json.JsonUtils;

/**
 * Coordinate bound ranges.
 *
 * @author Eric Trautman
 */
public class Bounds implements Serializable {

    private Double minX;
    private Double minY;
    private Double minZ;
    private Double maxX;
    private Double maxY;
    private Double maxZ;

    public Bounds() {
    }

    public Bounds(final Double minX,
                  final Double minY,
                  final Double maxX,
                  final Double maxY) {
        this(minX, minY, null, maxX, maxY, null);
    }

    public Bounds(final Double minX,
                  final Double minY,
                  final Double minZ,
                  final Double maxX,
                  final Double maxY,
                  final Double maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public Double getMinX() {
        return minX;
    }

    public Double getMinY() {
        return minY;
    }

    public Double getMaxX() {
        return maxX;
    }

    public Double getMaxY() {
        return maxY;
    }

    public Double getMaxZ() {
        return maxZ;
    }

    public Double getMinZ() {
        return minZ;
    }

    @JsonIgnore
    public boolean isBoundingBoxDefined() {
        return ((minX != null) && (minY != null) && (maxX != null) && (maxY != null));
    }

    @JsonIgnore
    public double getDeltaX() {
        return maxX - minX;
    }

    @JsonIgnore
    public double getDeltaY() {
        return maxY - minY;
    }

    @JsonIgnore
    public double getDeltaZ() {
        return ((maxZ == null) || (minZ == null)) ? 0 : maxZ - minZ;
    }

    @JsonIgnore
    public int getX() {
        return (int) Math.floor(minX);
    }

    @JsonIgnore
    public int getY() {
        return (int) Math.ceil(minY);
    }

    @JsonIgnore
    public int getWidth() {
        return (int) Math.ceil(getDeltaX());
    }

    @JsonIgnore
    public int getHeight() {
        return (int) Math.ceil(getDeltaY());
    }

    @JsonIgnore
    public double getCenterX() {
        return minX + (getDeltaX() / 2.0);
    }

    @JsonIgnore
    public double getCenterY() {
        return minY + (getDeltaY() / 2.0);
    }

    @Override
    public String toString() {
        return toJson();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final Bounds bounds = (Bounds) o;

        if (!minX.equals(bounds.minX)) {
            return false;
        }
        if (!minY.equals(bounds.minY)) {
            return false;
        }
        if (minZ != null ? !minZ.equals(bounds.minZ) : bounds.minZ != null) {
            return false;
        }
        if (!maxX.equals(bounds.maxX)) {
            return false;
        }
        if (!maxY.equals(bounds.maxY)) {
            return false;
        }
        return maxZ != null ? maxZ.equals(bounds.maxZ) : bounds.maxZ == null;
    }

    @Override
    public int hashCode() {
        int result = minX.hashCode();
        result = 31 * result + minY.hashCode();
        result = 31 * result + (minZ != null ? minZ.hashCode() : 0);
        result = 31 * result + maxX.hashCode();
        result = 31 * result + maxY.hashCode();
        result = 31 * result + (maxZ != null ? maxZ.hashCode() : 0);
        return result;
    }

    public boolean intersects(final Bounds that) {

        boolean result = false;

        if (this.hasXAndYDimensionsDefined() && that.hasXAndYDimensionsDefined()) {

            result = (this.minX <= that.maxX) && (this.minY <= that.maxY) &&
                     (this.maxX >= that.minX) && (this.maxY >= that.minY);

            if (result && this.hasZDimensionsDefined() && that.hasZDimensionsDefined()) {
                result = (this.minZ <= that.maxZ) && (this.maxZ >= that.minZ);
            }
        }

        return result;
    }

    public Bounds union(final Bounds that) {
        return new Bounds(union(this.minX, that.minX, true),
                          union(this.minY, that.minY, true),
                          union(this.minZ, that.minZ, true),
                          union(this.maxX, that.maxX, false),
                          union(this.maxY, that.maxY, false),
                          union(this.maxZ, that.maxZ, false));
    }

    public String toJson() {
        return JSON_HELPER.toJson(this);
    }

    public static Bounds fromJson(final String json) {
        return JSON_HELPER.fromJson(json);
    }

    @JsonIgnore
    private boolean hasXAndYDimensionsDefined() {
        return (minX != null) && (minY != null) && (maxX != null) && (maxY != null);
    }

    @JsonIgnore
    private boolean hasZDimensionsDefined() {
        return (minZ != null) && (maxZ != null);
    }

    private static final JsonUtils.Helper<Bounds> JSON_HELPER =
            new JsonUtils.Helper<>(Bounds.class);

    private static Double union(final Double a,
                                final Double b,
                                final boolean isMin) {
        final Double value;
        if (a == null) {
            value = b;
        } else if (b == null) {
            value = a;
        } else if (isMin) {
            value = Math.min(a, b);
        } else {
            value = Math.max(a, b);
        }
        return value;
    }
}
