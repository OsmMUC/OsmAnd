package net.osmand.plus.views;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Shader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.GPXUtilities;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.core.android.MapRendererView;
import net.osmand.core.jni.PointI;
import net.osmand.data.QuadRect;
import net.osmand.data.RotatedTileBox;
import net.osmand.plus.routing.ColoringType;
import net.osmand.plus.track.GradientScaleType;
import net.osmand.plus.utils.NativeUtilities;
import net.osmand.plus.views.layers.MapTileLayer;
import net.osmand.plus.views.layers.geometry.GpxGeometryWay;
import net.osmand.router.RouteSegmentResult;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Renderable {

    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECONDS = 30;

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "Renderable #" + mCount.getAndIncrement());
        }
    };

    private static final BlockingQueue<Runnable> sPoolWorkQueue = new LinkedBlockingQueue<>(128);
    public static final Executor THREAD_POOL_EXECUTOR;

    static {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                sPoolWorkQueue, sThreadFactory);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        THREAD_POOL_EXECUTOR = threadPoolExecutor;
    }

    public static abstract class RenderableSegment {

        private static final boolean DRAW_BORDER = true;

        protected static final int MIN_CULLER_ZOOM = 16;
        protected static final int BORDER_TYPE_ZOOM_THRESHOLD = MapTileLayer.DEFAULT_MAX_ZOOM + MapTileLayer.OVERZOOM_IN;

        public List<WptPt> points;                           // Original list of points
        protected List<WptPt> culled = new ArrayList<>();    // Reduced/resampled list of points
        protected int pointSize;
        protected double segmentSize;

        protected List<RouteSegmentResult> routeSegments;

        protected QuadRect trackBounds;
        protected double zoom = -1;
        protected AsynchronousResampler culler = null;       // The currently active resampler
        protected Paint paint = null;                        // MUST be set by 'updateLocalPaint' before use
        protected Paint borderPaint;
        protected int color;
        protected String width;

        @NonNull
        protected ColoringType coloringType = ColoringType.TRACK_SOLID;
        protected String routeInfoAttribute = null;

        protected GpxGeometryWay geometryWay;
        protected boolean drawArrows = false;

        public RenderableSegment(List<WptPt> points, double segmentSize) {
            this.points = points;
            this.segmentSize = segmentSize;
            trackBounds = GPXUtilities.calculateBounds(points);
        }

        public void setBorderPaint(Paint borderPaint) {
            this.borderPaint = borderPaint;
        }

        public boolean setDrawArrows(boolean drawArrows) {
            boolean changed = this.drawArrows != drawArrows;
            this.drawArrows = drawArrows;
            return changed;
        }

        protected void updateLocalPaint(Paint p) {
            if (paint == null) {
                paint = new Paint(p);
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setStyle(Paint.Style.STROKE);
            }
            paint.setColor(p.getColor());
            paint.setStrokeWidth(p.getStrokeWidth());
            if (coloringType.isGradient()) {
                paint.setAlpha(0xFF);
            }
        }

        public boolean setTrackParams(int color, String width,
                                      @NonNull ColoringType coloringType,
                                      @Nullable String routeInfoAttribute) {
            boolean changed = this.color != color
                    || !Algorithms.stringsEqual(this.width, width)
                    || this.coloringType != coloringType
                    || !Algorithms.stringsEqual(this.routeInfoAttribute, routeInfoAttribute);
            this.color = color;
            this.width = width;
            this.coloringType = coloringType;
            this.routeInfoAttribute = routeInfoAttribute;
            return changed;
        }

        public GpxGeometryWay getGeometryWay() {
            return geometryWay;
        }

        public void setGeometryWay(GpxGeometryWay geometryWay) {
            this.geometryWay = geometryWay;
        }

        protected abstract void startCuller(double newZoom);

        protected void drawSingleSegment(double zoom, Paint p, Canvas canvas, RotatedTileBox tileBox, MapRendererView renderer) {
            if (points.size() < 2) {
                return;
            }

            updateLocalPaint(p);
            canvas.rotate(-tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());
            if (coloringType.isGradient()) {
                if (DRAW_BORDER && zoom < BORDER_TYPE_ZOOM_THRESHOLD) {
                    drawSolid(points, borderPaint, canvas, tileBox, renderer);
                }
                drawGradient(zoom, points, paint, canvas, tileBox, renderer);
            } else {
                drawSolid(getPointsForDrawing(), paint, canvas, tileBox, renderer);
            }
            canvas.rotate(tileBox.getRotate(), tileBox.getCenterPixelX(), tileBox.getCenterPixelY());
        }

        public void drawSegment(double zoom, Paint p, Canvas canvas, RotatedTileBox tileBox, MapRendererView renderer) {
            if (QuadRect.trivialOverlap(getTileBounds(renderer, tileBox), trackBounds)) { // is visible?
                if (coloringType.isTrackSolid()) {
                    startCuller(zoom);
                }
                drawSingleSegment(zoom, p, canvas, tileBox, renderer);
            }
        }

        public void setRDP(List<WptPt> cull) {
            culled = cull;
        }

        public List<WptPt> getPointsForDrawing() {
            return culled.isEmpty() ? points : culled;
        }

        public boolean setRoute(List<RouteSegmentResult> routeSegments) {
            boolean changed = this.routeSegments != routeSegments;
            this.routeSegments = routeSegments;
            return changed;
        }

        public void drawGeometry(@NonNull Canvas canvas, @NonNull RotatedTileBox tileBox,
                                 @NonNull QuadRect quadRect, int trackColor, float trackWidth,
                                 @Nullable float[] dashPattern) {
            drawGeometry(canvas, tileBox, quadRect, trackColor, trackWidth, dashPattern, drawArrows);
        }

        public void drawGeometry(@NonNull Canvas canvas, @NonNull RotatedTileBox tileBox,
                                 @NonNull QuadRect quadRect, int trackColor, float trackWidth,
                                 @Nullable float[] dashPattern, boolean drawArrows) {
            if (geometryWay != null) {
                List<WptPt> points = coloringType.isRouteInfoAttribute() ? this.points : getPointsForDrawing();
                if (!Algorithms.isEmpty(points)) {
                    geometryWay.setTrackStyleParams(trackColor, trackWidth, dashPattern, drawArrows,
                            coloringType, routeInfoAttribute);
                    geometryWay.updateSegment(tileBox, points, routeSegments);
                    geometryWay.drawSegments(tileBox, canvas, quadRect.top, quadRect.left,
                            quadRect.bottom, quadRect.right, null, 0);
                }
            }
        }

        protected void drawSolid(@NonNull List<WptPt> pts, @NonNull Paint p,
                                 @NonNull Canvas canvas, @NonNull RotatedTileBox tileBox, MapRendererView renderer) {
            QuadRect tileBounds = getTileBounds(renderer, tileBox);
            WptPt lastPt = pts.get(0);
            boolean recalculateLastXY = true;
            Path path = new Path();
            for (int i = 1; i < pts.size(); i++) {
                WptPt pt = pts.get(i);
                if (arePointsInsideTile(pt, lastPt, tileBounds) && !arePrimeMeridianPoints(pt, lastPt)) {
                    if (recalculateLastXY) {
                        recalculateLastXY = false;
                        float lastX = getPixXFromLatLon(lastPt.lat, lastPt.lon, tileBox, renderer);
                        float lastY = getPixYFromLatLon(lastPt.lat, lastPt.lon, tileBox, renderer);
                        if (!path.isEmpty()) {
                            canvas.drawPath(path, p);
                        }
                        path.reset();
                        path.moveTo(lastX, lastY);
                    }
                    float x = getPixXFromLatLon(pt.lat, pt.lon, tileBox, renderer);
                    float y = getPixYFromLatLon(pt.lat, pt.lon, tileBox, renderer);
                    path.lineTo(x, y);
                } else {
                    recalculateLastXY = true;
                }
                lastPt = pt;
            }
            if (!path.isEmpty()) {
                canvas.drawPath(path, p);
            }
        }

        protected void drawGradient(double zoom, @NonNull List<WptPt> pts, @NonNull Paint p,
                                    @NonNull Canvas canvas, @NonNull RotatedTileBox tileBox, MapRendererView renderer) {
            System.out.println("!!! 0=====");
            GradientScaleType scaleType = coloringType.toGradientScaleType();
            if (scaleType == null) {
                return;
            }
            QuadRect tileBounds = getTileBounds(renderer, tileBox);
            boolean drawSegmentBorder = DRAW_BORDER && zoom >= BORDER_TYPE_ZOOM_THRESHOLD;
            Path path = new Path();
            boolean recalculateLastXY = true;
            WptPt lastPt = pts.get(0);

            List<PointF> gradientPoints = new ArrayList<>();
            List<Integer> gradientColors = new ArrayList<>();
            float gradientAngle = 0;

            List<Path> paths = new ArrayList<>();
            List<LinearGradient> gradients = new ArrayList<>();

            for (int i = 1; i < pts.size(); i++) {
                WptPt pt = pts.get(i);
                WptPt nextPt = i + 1 < pts.size() ? pts.get(i + 1) : null;
                float nextX = nextPt == null ? 0 : getPixXFromLatLon(nextPt.lat, nextPt.lon, tileBox, renderer);
                float nextY = nextPt == null ? 0 : getPixYFromLatLon(nextPt.lat, nextPt.lon, tileBox, renderer);
                float lastX = 0;
                float lastY = 0;
                if (arePointsInsideTile(pt, lastPt, tileBounds) && !arePrimeMeridianPoints(pt, lastPt)) {
                    if (recalculateLastXY) {
                        recalculateLastXY = false;
                        lastX = getPixXFromLatLon(lastPt.lat, lastPt.lon, tileBox, renderer);
                        lastY = getPixYFromLatLon(lastPt.lat, lastPt.lon, tileBox, renderer);
                        if (!path.isEmpty()) {
                            paths.add(new Path(path));
                            gradients.add(createGradient(gradientPoints, gradientColors));
                        }
                        path.reset();
                        path.moveTo(lastX, lastY);

                        gradientPoints.clear();
                        gradientColors.clear();
                        gradientPoints.add(new PointF(lastX, lastY));
                        gradientColors.add(lastPt.getColor(scaleType.toColorizationType()));
                    }
                    float x = getPixXFromLatLon(pt.lat, pt.lon, tileBox, renderer);
                    float y = getPixYFromLatLon(pt.lat, pt.lon, tileBox, renderer);
                    path.lineTo(x, y);
                    gradientPoints.add(new PointF(x, y));
                    gradientColors.add(pt.getColor(scaleType.toColorizationType()));

                    if (gradientColors.size() == 2) {
                        gradientAngle = calculateAngle(lastX, lastY, x, y);
                    }
                    if (nextPt != null) {
                        float nextAngle = calculateAngle(x, y, nextX, nextY);
                        if (Math.abs(nextAngle - gradientAngle) > 20) {
                            recalculateLastXY = true;
                        }
                    }
                } else {
                    recalculateLastXY = true;
                }
                lastPt = pt;
            }
            if (!path.isEmpty()) {
                paths.add(new Path(path));
                gradients.add(createGradient(gradientPoints, gradientColors));
            }

            if (!paths.isEmpty()) {
                if (drawSegmentBorder) {
                    canvas.drawPath(paths.get(0), borderPaint);
                }
                for (int i = 0; i < paths.size(); i++) {
                    if (drawSegmentBorder && i + 1 < paths.size()) {
                        canvas.drawPath(paths.get(i + 1), borderPaint);
                    }
                    p.setShader(gradients.get(i));
                    canvas.drawPath(paths.get(i), p);
                }
            }
        }

        private LinearGradient createGradient(@NonNull List<PointF> gradientPoints,
                                              @NonNull List<Integer> gradientColors) {
            float gradientLength = 0;
            List<Float> pointsLength = new ArrayList<>(gradientPoints.size() - 1);
            for (int i = 1; i < gradientPoints.size(); i++) {
                PointF start = gradientPoints.get(i - 1);
                PointF end = gradientPoints.get(i);
                pointsLength.add((float) MapUtils.getSqrtDistance(start.x, start.y, end.x, end.y));
                gradientLength += pointsLength.get(i - 1);
            }

            float[] positions = new float[gradientPoints.size()];
            positions[0] = 0;
            for (int i = 1; i < gradientPoints.size(); i++) {
                positions[i] = positions[i - 1] + pointsLength.get(i - 1) / gradientLength;
            }

            int[] colors = new int[gradientColors.size()];
            for (int i = 0; i < gradientColors.size(); i++) {
                colors[i] = gradientColors.get(i);
            }

            PointF gradientStart = gradientPoints.get(0);
            PointF gradientEnd = gradientPoints.get(gradientPoints.size() - 1);
            return new LinearGradient(gradientStart.x, gradientStart.y, gradientEnd.x, gradientEnd.y,
                    colors, positions, Shader.TileMode.CLAMP);
        }

        private float calculateAngle(float startX, float startY, float endX, float endY) {
            return (float) Math.abs(Math.toDegrees(Math.atan2(endY - startY, endX - startX)));
        }

        protected boolean arePointsInsideTile(WptPt first, WptPt second, QuadRect tileBounds) {
            if (first == null || second == null) {
                return false;
            }
            return Math.min(first.lon, second.lon) < tileBounds.right
                    && Math.max(first.lon, second.lon) > tileBounds.left
                    && Math.min(first.lat, second.lat) < tileBounds.top
                    && Math.max(first.lat, second.lat) > tileBounds.bottom;
        }

        protected boolean arePrimeMeridianPoints(WptPt first, WptPt second) {
            return Math.max(first.lon, second.lon) == GPXUtilities.PRIME_MERIDIAN
                    && Math.min(first.lon, second.lon) == -GPXUtilities.PRIME_MERIDIAN;
        }

        protected float getPixXFromLatLon(double lat, double lon, RotatedTileBox tileBox, MapRendererView renderer) {
            if (renderer == null) {
                return tileBox.getPixXFromLatLon(lat, lon);
            } else {
                return NativeUtilities.getPixelFromLatLon(renderer, tileBox, lat, lon).x;
            }
        }

        protected float getPixYFromLatLon(double lat, double lon, RotatedTileBox tileBox, MapRendererView renderer) {
            if (renderer == null) {
                return tileBox.getPixYFromLatLon(lat, lon);
            } else {
                return NativeUtilities.getPixelFromLatLon(renderer, tileBox, lat, lon).y;
            }
        }

        protected QuadRect getTileBounds(MapRendererView renderer, RotatedTileBox tileBox) {
            if (renderer == null) {
                return tileBox.getLatLonBounds();
            } else {
                PointI windowSize = renderer.getState().getWindowSize();
                PointI tl31 = NativeUtilities.get31FromPixel(renderer, tileBox, 0, 0, true);
                PointI br31 = NativeUtilities.get31FromPixel(renderer, tileBox, windowSize.getX(), windowSize.getY(), true);
                return new QuadRect(MapUtils.get31LongitudeX(tl31.getX()),
                        MapUtils.get31LatitudeY(tl31.getY()),
                        MapUtils.get31LongitudeX(br31.getX()),
                        MapUtils.get31LatitudeY(br31.getY()));
            }
        }
    }

    public static class StandardTrack extends RenderableSegment {

        public StandardTrack(List<WptPt> pt, double base) {
            super(pt, base);
        }

        @Override public void startCuller(double newZoom) {

            if (zoom != newZoom) {
                if (culler != null) {
                    culler.cancel(true);
                }
                if (zoom < newZoom) {            // if line would look worse (we're zooming in) then...
                    culled.clear();              // use full-resolution until re-cull complete
                }
                zoom = newZoom;
                if (newZoom >= MIN_CULLER_ZOOM) {
                    return;
                }

                double cullDistance = Math.pow(2.0, segmentSize - zoom);    // segmentSize == epsilon
                culler = new AsynchronousResampler.RamerDouglasPeucer(this, cullDistance);
                try {
                    culler.executeOnExecutor(THREAD_POOL_EXECUTOR, "");
                } catch (RejectedExecutionException e) {
                    culler = null;
                }
            }
        }
    }

    public static class CurrentTrack extends RenderableSegment {

        public CurrentTrack(List<WptPt> pt) {
            super(pt, 0);
        }

        @Override
        public void drawSegment(double zoom, Paint p, Canvas canvas, RotatedTileBox tileBox, MapRendererView renderer) {
            if (points.size() != pointSize) {
                int prevSize = pointSize;
                pointSize = points.size();
                GPXUtilities.updateBounds(trackBounds, points, prevSize);
            }
            drawSingleSegment(zoom, p, canvas, tileBox, renderer);
        }

        @Override protected void startCuller(double newZoom) {}
    }
}