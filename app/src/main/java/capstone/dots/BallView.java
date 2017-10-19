package capstone.dots;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;

public class BallView extends View {
    public float x;
    public float y;
    private int rad;
    private Paint paint;
    private Context context;

    public BallView(Context context, float x, float y, int rad) {
        super(context);

        this.context = context;
        this.x = x;
        this.y = y;
        this.rad = rad;
    }

    public BallView(Context context) {
        super(context);

        this.context = context;
    }

    public BallView(Context context, AttributeSet attrs) {
        super(context, attrs);

        this.context = context;
    }

    public BallView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        this.context = context;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        initPaint();
        canvas.drawCircle(x, y, rad, paint);
    }

    private void initPaint() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(ContextCompat.getColor(context, R.color.gray));
    }
}
