/*
 * Copyright (c) 2003, The JUNG Authors
 * All rights reserved.
 *
 * This software is open-source under the BSD license; see either "license.txt"
 * or https://github.com/jrtom/jung/blob/master/LICENSE for a description.
 *
 */
package edu.uci.ics.jung.visualization.transform;

import static edu.uci.ics.jung.visualization.layout.AWT.POINT_MODEL;

import edu.uci.ics.jung.layout.model.PolarPoint;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HyperbolicTransformer wraps a MutableAffineTransformer and modifies the transform and
 * inverseTransform methods so that they create a fisheye projection of the graph points, with
 * points near the center spread out and points near the edges collapsed onto the circumference of
 * an ellipse.
 *
 * <p>HyperbolicTransformer is not an affine transform, but it uses an affine transform to cause
 * translation, scaling, rotation, and shearing while applying a non-affine hyperbolic filter in its
 * transform and inverseTransform methods.
 *
 * @author Tom Nelson
 */
public class HyperbolicTransformer extends LensTransformer implements MutableTransformer {

  private static final Logger log = LoggerFactory.getLogger(HyperbolicTransformer.class);

  /**
   * Create an instance, setting values from the passed component and registering to listen for
   * layoutSize changes on the component.
   *
   * @param component the component used for rendering
   */
  public HyperbolicTransformer(Component component) {
    this(component, new MutableAffineTransformer());
  }

  /**
   * create an instance, setting values from the passed component and registering to listen for
   * layoutSize changes on the component
   *
   * @param component the component used for rendering
   */
  public HyperbolicTransformer(Component component, MutableTransformer delegate) {
    super(component, delegate);
  }

  /**
   * Create an instance with a possibly shared transform.
   *
   * @param lens a lens created elsewhere, but on the same component
   */
  public HyperbolicTransformer(Lens lens, MutableTransformer delegate) {
    super(lens, delegate);
  }

  /** override base class transform to project the fisheye effect */
  public Point2D transform(Point2D graphPoint) {
    if (graphPoint == null) {
      return null;
    }
    Ellipse2D lensEllipse = (Ellipse2D) lens.getLensShape();
    if (lensEllipse.contains(graphPoint)) {
      log.trace("lens {} contains graphPoint{}", lensEllipse, graphPoint);
    } else {
      log.trace("lens {} does not contain graphPoint {}", lensEllipse, graphPoint);
    }
    Point2D viewCenter = lens.getViewCenter();
    double viewRadius = lens.getViewRadius();
    double ratio = lens.getRatio();
    // transform the point from the graph to the view
    Point2D viewPoint = delegate.transform(graphPoint);
    if (lensEllipse.contains(viewPoint)) {
      log.trace("lens {} contains viewPoint {}", lensEllipse, viewPoint);
    } else {
      log.trace("lens {} does not contain viewPoint {}", lensEllipse, viewPoint);
    }

    // calculate point from center
    double dx = viewPoint.getX() - viewCenter.getX();
    double dy = viewPoint.getY() - viewCenter.getY();
    // factor out ellipse
    dx *= ratio;
    Point2D pointFromCenter = new Point2D.Double(dx, dy);

    PolarPoint polar = PolarPoint.cartesianToPolar(POINT_MODEL, pointFromCenter);
    double theta = polar.getTheta();
    double radius = polar.getRadius();
    if (radius > viewRadius) {
      log.trace("outside point radius {} > viewRadius {}", radius, viewRadius);
      return viewPoint;
    } else {
      log.trace("inside point radius {} >= viewRadius {}", radius, viewRadius);
    }

    double mag = Math.tan(Math.PI / 2 * lens.getMagnification());
    radius *= mag;

    radius = Math.min(radius, viewRadius);
    radius /= viewRadius;
    radius *= Math.PI / 2;
    radius = Math.abs(Math.atan(radius));
    radius *= viewRadius;
    Point2D projectedPoint = PolarPoint.polarToCartesian(POINT_MODEL, theta, radius);
    projectedPoint.setLocation(projectedPoint.getX() / ratio, projectedPoint.getY());
    Point2D translatedBack =
        new Point2D.Double(
            projectedPoint.getX() + viewCenter.getX(), projectedPoint.getY() + viewCenter.getY());
    return translatedBack;
  }

  /** override base class to un-project the fisheye effect */
  public Point2D inverseTransform(Point2D viewPoint) {

    Ellipse2D lensEllipse = (Ellipse2D) lens.getLensShape();
    if (lensEllipse.contains(viewPoint)) {
      log.trace("lens {} contains viewPoint{}", lensEllipse, viewPoint);
    } else {
      log.trace("lens {} does not contain viewPoint {}", lensEllipse, viewPoint);
    }

    Point2D viewCenter = lens.getViewCenter();
    double viewRadius = lens.getViewRadius();
    double ratio = lens.getRatio();
    double dx = viewPoint.getX() - viewCenter.getX();
    double dy = viewPoint.getY() - viewCenter.getY();
    // factor out ellipse
    dx *= ratio;

    Point2D pointFromCenter = new Point2D.Double(dx, dy);

    PolarPoint polar = PolarPoint.cartesianToPolar(POINT_MODEL, pointFromCenter);

    double radius = polar.getRadius();
    if (radius > viewRadius) {
      log.trace("outside point radius {} > viewRadius {}", radius, viewRadius);
    } else {
      log.trace("inside point radius {} <= viewRadius {}", radius, viewRadius);
    }

    if (radius > viewRadius) {
      return delegate.inverseTransform(viewPoint);
    }

    radius /= viewRadius;
    radius = Math.abs(Math.tan(radius));
    radius /= Math.PI / 2;
    radius *= viewRadius;
    double mag = Math.tan(Math.PI / 2 * lens.getMagnification());
    radius /= mag;
    polar.setRadius(radius);
    Point2D projectedPoint = PolarPoint.polarToCartesian(POINT_MODEL, polar);
    projectedPoint.setLocation(projectedPoint.getX() / ratio, projectedPoint.getY());
    Point2D translatedBack =
        new Point2D.Double(
            projectedPoint.getX() + viewCenter.getX(), projectedPoint.getY() + viewCenter.getY());
    return delegate.inverseTransform(translatedBack);
  }
}
