package capstone.dots;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by lenovo on 10/2/2017.
 */

public class CameraGuideView extends View {
    private Context context;
    private Paint paintBox;
    private Paint paintBorder;
    private int h;
    private int w;
    private int t;
    private int b;
    private int l;
    private int r;

    public CameraGuideView(Context context, int h, int w) {
        super(context);

        this.context = context;
        this.h = h;
        this.w = w;
    }

    public CameraGuideView(Context context) {
        super(context);

        this.context = context;
    }

    public CameraGuideView(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.context = context;
    }

    public CameraGuideView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        this.context = context;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        initPaint();

        int ltrH = (int) (h * 0.9);
        int ltrW = (int) ((h * 0.9) * 0.69545);

        t = (h - ltrH) / 2;
        b = t + ltrH;
        l = (w - ltrW) / 2;
        r = l + ltrW;

        canvas.drawRect(l, t, r, b, paintBox);

        canvas.drawRect(0, 0, l, b, paintBorder);
        canvas.drawRect(l, 0, w, t, paintBorder);
        canvas.drawRect(r, t, w, h, paintBorder);
        canvas.drawRect(0, b, r, h, paintBorder);
    }

    private void initPaint() {
        paintBox = new Paint();
        paintBox.setColor(ContextCompat.getColor(context, R.color.colorAccent));
        paintBox.setStyle(Paint.Style.STROKE);
        paintBox.setStrokeWidth(3);
        paintBox.setAntiAlias(true);

        paintBorder = new Paint();
        paintBorder.setColor(ContextCompat.getColor(context, R.color.black_transparent));
        paintBorder.setStyle(Paint.Style.FILL);
        paintBorder.setAntiAlias(true);
    }

    public SparseArray<PointF> getPoints() {
        List<PointF> points = new ArrayList<>();
        points.add(new PointF(l, t));
        points.add(new PointF(r, t));
        points.add(new PointF(l, b));
        points.add(new PointF(r, b));

        return getOrderedPoints(points);
    }

    public SparseArray<PointF> getOrderedPoints(List<PointF> points) {
        PointF centerPoint = new PointF();

        int size = points.size();
        for (PointF pointF : points) {
            centerPoint.x += pointF.x / size;
            centerPoint.y += pointF.y / size;
        }

        SparseArray<PointF> orderedPoints = new SparseArray<>();
        for (PointF pointF : points) {
            int index = -1;

            if (pointF.x < centerPoint.x && pointF.y < centerPoint.y) index = 0;
            else if (pointF.x > centerPoint.x && pointF.y < centerPoint.y) index = 1;
            else if (pointF.x < centerPoint.x && pointF.y > centerPoint.y) index = 2;
            else if (pointF.x > centerPoint.x && pointF.y > centerPoint.y) index = 3;

            orderedPoints.put(index, pointF);
        }

        return orderedPoints;
    }
}
